package com.gidajari.service;

import com.gidajari.entity.*;
import com.gidajari.entity.enums.*;
import com.gidajari.integration.MobileMoneyGateway;
import com.gidajari.repository.*;
import com.gidajari.service.exception.AccesRefuseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Regles de gestion n1 a n4 sur le sequestre (Cahier des Charges Technique). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscrowServiceTest {

    @Mock EscrowTransactionRepository escrowRepo;
    @Mock EscrowMilestoneRepository milestoneRepo;
    @Mock ProjetRepository projetRepo;
    @Mock JalonRepository jalonRepo;
    @Mock UserRepository userRepo;
    @Mock KYCService kycService;
    @Mock NotificationService notificationService;
    @Mock AdminJournalService journal;
    @Mock MobileMoneyGateway gateway;

    @InjectMocks EscrowService service;

    ClientParticulier client;
    Artisan artisan;
    Projet projet;
    Jalon j1;
    Jalon j2;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(service, "tauxStandard", 0.02);
        ReflectionTestUtils.setField(service, "tauxPremium", 0.015);
        ReflectionTestUtils.setField(service, "tauxPro", 0.01);

        client = new ClientParticulier();
        client.setId("client");
        client.setKycLevel(KycLevel.NIVEAU_2_IDENTITE_DECLAREE);
        artisan = new Artisan();
        artisan.setId("artisan");
        artisan.setProfileType(ProfileType.ARTISAN);
        artisan.setKycLevel(KycLevel.NIVEAU_3_PIECE_IDENTITE);

        projet = new Projet();
        projet.setId("p1");
        projet.setTitre("Maison");
        projet.setClient(client);
        projet.setPublie(true);
        j1 = jalon("j1", 1, 500_000L);
        j2 = jalon("j2", 2, 1_000_000L);

        when(projetRepo.findById("p1")).thenReturn(Optional.of(projet));
        when(userRepo.findById("artisan")).thenReturn(Optional.of(artisan));
        when(jalonRepo.findByProjetIdOrderByOrdreAsc("p1")).thenReturn(List.of(j1, j2));
        when(jalonRepo.findById("j1")).thenReturn(Optional.of(j1));
        when(jalonRepo.findById("j2")).thenReturn(Optional.of(j2));
        when(escrowRepo.findByProjetIdOrderByCreatedAtDesc("p1")).thenReturn(List.of());
        when(escrowRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.initierCollecte(any(), anyString(), anyLong(), anyString()))
                .thenReturn(new MobileMoneyGateway.Resultat("REF-1", true));
    }

    private Jalon jalon(String id, int ordre, long montant) {
        Jalon j = new Jalon();
        j.setId(id);
        j.setOrdre(ordre);
        j.setLibelle("Jalon " + ordre);
        j.setMontantFcfa(montant);
        j.setProjet(projet);
        return j;
    }

    private EscrowTransaction sequestreFinance() {
        EscrowTransaction t = service.engager("p1", client, "artisan", NiveauCommission.STANDARD);
        t.setId("t1");
        for (EscrowMilestone m : t.getMilestones()) {
            when(milestoneRepo.findByJalonId(m.getJalon().getId())).thenReturn(Optional.of(m));
        }
        when(escrowRepo.findById("t1")).thenReturn(Optional.of(t));
        return service.payer("t1", client, OperateurMobileMoney.AMANA, "+22790000001");
    }

    @Test
    void commissionDiffereeSelonNiveau() {
        assertThat(service.calculerCommission(1_000_000, NiveauCommission.STANDARD)).isEqualTo(20_000);
        assertThat(service.calculerCommission(1_000_000, NiveauCommission.PREMIUM)).isEqualTo(15_000);
        assertThat(service.calculerCommission(1_000_000, NiveauCommission.PRO)).isEqualTo(10_000);
    }

    @Test
    void engagementCreeUneTrancheParJalon() {
        EscrowTransaction t = service.engager("p1", client, "artisan", null);
        assertThat(t.getStatut()).isEqualTo(EscrowStatus.DRAFT);
        assertThat(t.getMontantTotalFcfa()).isEqualTo(1_500_000L);
        assertThat(t.getMilestones()).hasSize(2);
        assertThat(t.getNiveauCommission()).isEqualTo(NiveauCommission.STANDARD);
    }

    @Test
    void seulLeClientDuProjetPeutEngager() {
        ClientParticulier autre = new ClientParticulier();
        autre.setId("autre");
        assertThatThrownBy(() -> service.engager("p1", autre, "artisan", null))
                .isInstanceOf(AccesRefuseException.class);
    }

    @Test
    void paiementConfirmeFinanceLeSequestre() {
        EscrowTransaction t = sequestreFinance();
        assertThat(t.getStatut()).isEqualTo(EscrowStatus.FUNDED);
        assertThat(t.getReferencePaiement()).isEqualTo("REF-1");
    }

    @Test
    void validationDUnJalonLibereLaTrancheNetteDeCommission() {
        EscrowTransaction t = sequestreFinance();
        service.soumettrePreuve("j1", artisan, "/files/a.jpg", 13.5, 2.1);
        assertThat(j1.getStatut()).isEqualTo(JalonStatus.SOUMIS);

        service.validerJalon("j1", client);

        assertThat(j1.getStatut()).isEqualTo(JalonStatus.VALIDE);
        assertThat(t.getStatut()).isEqualTo(EscrowStatus.PARTIAL_RELEASE);
        assertThat(t.getMontantLibereFcfa()).isEqualTo(500_000L);
        assertThat(artisan.getWalletBalance()).isEqualTo(490_000L);
        EscrowMilestone m1 = t.getMilestones().get(0);
        assertThat(m1.getCommissionFcfa()).isEqualTo(10_000L);
        assertThat(m1.getMontantNetFcfa()).isEqualTo(490_000L);
    }

    @Test
    void tousLesJalonsValidesTerminentLeSequestre() {
        EscrowTransaction t = sequestreFinance();
        service.soumettrePreuve("j1", artisan, "/files/a.jpg", 13.5, 2.1);
        service.validerJalon("j1", client);
        service.soumettrePreuve("j2", artisan, "/files/b.jpg", 13.5, 2.1);
        service.validerJalon("j2", client);
        assertThat(t.getStatut()).isEqualTo(EscrowStatus.COMPLETED);
        assertThat(artisan.getWalletBalance()).isEqualTo(1_470_000L);
    }

    @Test
    void autoValidation48hLibereLesFonds() {
        EscrowTransaction t = sequestreFinance();
        service.soumettrePreuve("j1", artisan, "/files/a.jpg", 13.5, 2.1);
        service.autoValider(j1);
        assertThat(j1.getStatut()).isEqualTo(JalonStatus.VALIDE);
        assertThat(t.getMontantLibereFcfa()).isEqualTo(500_000L);
    }

    @Test
    void unAutrePrestataireNePeutPasSoumettreDePreuve() {
        sequestreFinance();
        Artisan intrus = new Artisan();
        intrus.setId("intrus");
        assertThatThrownBy(() -> service.soumettrePreuve("j1", intrus, "/files/x.jpg", 0.0, 0.0))
                .isInstanceOf(AccesRefuseException.class);
    }

    @Test
    void pasDePreuveSansSequestreAlimente() {
        EscrowTransaction t = service.engager("p1", client, "artisan", null);
        for (EscrowMilestone m : t.getMilestones()) {
            when(milestoneRepo.findByJalonId(m.getJalon().getId())).thenReturn(Optional.of(m));
        }
        assertThatThrownBy(() -> service.soumettrePreuve("j1", artisan, "/files/x.jpg", 0.0, 0.0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void litigeGeleLesJalonsEtArbitrageRembourseLeClient() {
        EscrowTransaction t = sequestreFinance();
        service.soumettrePreuve("j1", artisan, "/files/a.jpg", 13.5, 2.1);
        service.ouvrirLitige("t1", client, "Malfacons");
        assertThat(t.getStatut()).isEqualTo(EscrowStatus.DISPUTED);
        assertThat(j1.getStatut()).isEqualTo(JalonStatus.LITIGE);

        // Plus d'auto-validation pendant le litige
        service.autoValider(j1);
        assertThat(t.getMontantLibereFcfa()).isZero();

        User admin = new ClientParticulier();
        admin.setId("admin");
        service.resoudreLitige("t1", EscrowStatus.RESOLVED_CLIENT, admin, "Travaux non conformes");
        assertThat(t.getStatut()).isEqualTo(EscrowStatus.RESOLVED_CLIENT);
        assertThat(client.getWalletBalance()).isEqualTo(1_500_000L);
    }

    @Test
    void arbitrageEnFaveurDuPrestataireLibereLeReste() {
        EscrowTransaction t = sequestreFinance();
        service.ouvrirLitige("t1", artisan, "Client injoignable");
        service.resoudreLitige("t1", EscrowStatus.RESOLVED_PROVIDER, client, null);
        assertThat(t.getMontantLibereFcfa()).isEqualTo(1_500_000L);
        assertThat(artisan.getWalletBalance()).isEqualTo(1_470_000L);
    }

    @Test
    void transitionsInvalidesRefusees() {
        EscrowTransaction t = service.engager("p1", client, "artisan", null);
        t.setId("t1");
        when(escrowRepo.findById("t1")).thenReturn(Optional.of(t));
        // Litige impossible tant que le sequestre n'est pas alimente
        assertThatThrownBy(() -> service.ouvrirLitige("t1", client, "x")).isInstanceOf(IllegalStateException.class);
        // Arbitrage impossible hors litige
        assertThatThrownBy(() -> service.resoudreLitige("t1", EscrowStatus.REFUNDED, client, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
