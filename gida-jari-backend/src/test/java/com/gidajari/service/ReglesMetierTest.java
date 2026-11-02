package com.gidajari.service;

import com.gidajari.entity.Artisan;
import com.gidajari.entity.ClientParticulier;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.repository.KYCSubmissionRepository;
import com.gidajari.repository.NotationRepository;
import com.gidajari.repository.UserRepository;
import com.gidajari.service.exception.KycInsuffisantException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KYC (regle n4), authentification par telephone (regle n5), notation ponderee. */
class ReglesMetierTest {

    private final KYCService kycService = new KYCService(
            Mockito.mock(KYCSubmissionRepository.class), Mockito.mock(UserRepository.class),
            Mockito.mock(AdminJournalService.class), Mockito.mock(NotificationService.class));

    @Test
    void kycInsuffisantBloqueLAction() {
        ClientParticulier c = new ClientParticulier();
        c.setKycLevel(KycLevel.NIVEAU_1_TELEPHONE_VERIFIE);
        assertThatThrownBy(() -> kycService.exigerNiveau(c, KycLevel.NIVEAU_2_IDENTITE_DECLAREE))
                .isInstanceOf(KycInsuffisantException.class);
        c.setKycLevel(KycLevel.NIVEAU_3_PIECE_IDENTITE);
        kycService.exigerNiveau(c, KycLevel.NIVEAU_2_IDENTITE_DECLAREE);
    }

    @Test
    void profilCompletDonneLeNiveau2SansRetrograder() {
        Artisan a = new Artisan();
        a.setKycLevel(KycLevel.NIVEAU_1_TELEPHONE_VERIFIE);
        a.setNom("Ali");
        a.setPrenom("Sani");
        kycService.verifierIdentiteDeclaree(a);
        assertThat(a.getKycLevel()).isEqualTo(KycLevel.NIVEAU_1_TELEPHONE_VERIFIE); // metier et zone manquants

        a.setCorpsDeMetier("macon");
        a.setZoneIntervention("Niamey");
        kycService.verifierIdentiteDeclaree(a);
        assertThat(a.getKycLevel()).isEqualTo(KycLevel.NIVEAU_2_IDENTITE_DECLAREE);

        a.setKycLevel(KycLevel.NIVEAU_4_JUSTIFICATIF_DOMICILE);
        kycService.verifierIdentiteDeclaree(a);
        assertThat(a.getKycLevel()).isEqualTo(KycLevel.NIVEAU_4_JUSTIFICATIF_DOMICILE);
    }

    @Test
    void telephoneNormaliseAuFormatInternational() {
        assertThat(OTPService.normaliserTelephone("90 00 00 01")).isEqualTo("+22790000001");
        assertThat(OTPService.normaliserTelephone("0022790000001")).isEqualTo("+22790000001");
        assertThat(OTPService.normaliserTelephone("+227 90-00-00-01")).isEqualTo("+22790000001");
        assertThatThrownBy(() -> OTPService.normaliserTelephone("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreDeNotationPondere() {
        NotationService notation = new NotationService(Mockito.mock(NotationRepository.class),
                Mockito.mock(UserRepository.class), Mockito.mock(EscrowService.class));
        ReflectionTestUtils.setField(notation, "poidsQualite", 0.30);
        ReflectionTestUtils.setField(notation, "poidsDelai", 0.20);
        ReflectionTestUtils.setField(notation, "poidsCommunication", 0.15);
        ReflectionTestUtils.setField(notation, "poidsBudget", 0.20);
        ReflectionTestUtils.setField(notation, "poidsProprete", 0.15);
        assertThat(notation.calculerScore(5, 5, 5, 5, 5)).isEqualTo(5.0);
        assertThat(notation.calculerScore(5, 4, 5, 4, 5)).isEqualTo(4.6);
        assertThat(notation.calculerScore(1, 1, 1, 1, 1)).isEqualTo(1.0);
    }
}
