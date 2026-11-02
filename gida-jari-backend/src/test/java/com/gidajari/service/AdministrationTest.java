package com.gidajari.service;

import com.gidajari.dto.AdminVues.EtatProjet;
import com.gidajari.entity.AdminMembre;
import com.gidajari.entity.ClientParticulier;
import com.gidajari.entity.EscrowTransaction;
import com.gidajari.entity.Projet;
import com.gidajari.entity.enums.AdminRole;
import com.gidajari.entity.enums.EscrowStatus;
import com.gidajari.repository.AdminMembreRepository;
import com.gidajari.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Back-office : roles de l'equipe d'administration et indicateurs derives. */
class AdministrationTest {

    private AdminMembreRepository membres;
    private AdminEquipeService equipe;

    @BeforeEach
    void init() {
        membres = Mockito.mock(AdminMembreRepository.class);
        equipe = new AdminEquipeService(membres, Mockito.mock(UserRepository.class), Mockito.mock(AdminJournalService.class));
        ReflectionTestUtils.setField(equipe, "telephonesAmorcage", "+22790000000, 96 11 22 33");
        when(membres.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void lesNumerosDeConfigurationSontSuperAdmins() {
        assertThat(equipe.roleDuTelephone("+22790000000")).contains(AdminRole.SUPER_ADMIN);
        assertThat(equipe.roleDuTelephone("+22796112233")).contains(AdminRole.SUPER_ADMIN);
        assertThat(equipe.roleDuTelephone("+22791000000")).isEmpty();
    }

    @Test
    void unMembreDesactiveNaPlusDeRole() {
        AdminMembre m = new AdminMembre();
        m.setPhone("+22791000000");
        m.setRole(AdminRole.OPERATEUR_KYC);
        when(membres.findByPhone("+22791000000")).thenReturn(Optional.of(m));
        assertThat(equipe.roleDuTelephone("+22791000000")).contains(AdminRole.OPERATEUR_KYC);
        m.setActif(false);
        assertThat(equipe.roleDuTelephone("+22791000000")).isEmpty();
    }

    @Test
    void onNePeutNiDoublerUnSuperAdminNiSeModifierSoiMeme() {
        ClientParticulier moi = new ClientParticulier();
        moi.setPhone("+22791000000");
        assertThatThrownBy(() -> equipe.ajouter("90 00 00 00", null, AdminRole.LECTEUR, moi))
                .isInstanceOf(IllegalStateException.class);

        AdminMembre m = new AdminMembre();
        m.setPhone("+22791000000");
        m.setRole(AdminRole.SUPER_ADMIN);
        when(membres.findById("m1")).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> equipe.modifier("m1", AdminRole.LECTEUR, null, null, moi))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> equipe.retirer("m1", moi)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void etatDuProjetSuitLeSequestre() {
        Projet p = new Projet();
        assertThat(AdminPilotageService.etat(p, null)).isEqualTo(EtatProjet.BROUILLON);
        p.setPublie(true);
        assertThat(AdminPilotageService.etat(p, null)).isEqualTo(EtatProjet.PUBLIE);
        EscrowTransaction t = new EscrowTransaction();
        t.setStatut(EscrowStatus.PENDING_PAYMENT);
        assertThat(AdminPilotageService.etat(p, t)).isEqualTo(EtatProjet.ATTENTE_PAIEMENT);
        t.setStatut(EscrowStatus.PARTIAL_RELEASE);
        assertThat(AdminPilotageService.etat(p, t)).isEqualTo(EtatProjet.EN_COURS);
        t.setStatut(EscrowStatus.DISPUTED);
        assertThat(AdminPilotageService.etat(p, t)).isEqualTo(EtatProjet.LITIGE);
        t.setStatut(EscrowStatus.REFUNDED);
        assertThat(AdminPilotageService.etat(p, t)).isEqualTo(EtatProjet.TERMINE);
    }

    @Test
    void motifDeRecherche() {
        assertThat(AdminPilotageService.motif(null)).isEqualTo("%");
        assertThat(AdminPilotageService.motif("  Villa ")).isEqualTo("%villa%");
        assertThat(AdminPilotageService.motif("90 00 00 01")).isEqualTo("%90000001%");
        assertThat(AdminPilotageService.motif("100%_")).isEqualTo("%100%");
    }
}
