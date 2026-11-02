package com.gidajari.service;

import com.gidajari.entity.Artisan;
import com.gidajari.entity.ClientParticulier;
import com.gidajari.entity.EntrepriseBTP;
import com.gidajari.entity.FournisseurMateriaux;
import com.gidajari.entity.IngenieurArchitecte;
import com.gidajari.entity.KYCSubmission;
import com.gidajari.entity.ONGInstitution;
import com.gidajari.entity.User;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.repository.KYCSubmissionRepository;
import com.gidajari.repository.UserRepository;
import com.gidajari.service.exception.KycInsuffisantException;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service KYC progressif (7 niveaux, voir Architecture Globale V3, section 5.1).
 * L'acces aux fonctionnalites de mise en relation et de paiement est
 * conditionne par le niveau KYC requis (regle de gestion n4).
 *
 * Progression :
 *  - NIVEAU_1 : automatique a la verification OTP du telephone ;
 *  - NIVEAU_2 : automatique quand le profil (identite declaree) est complete ;
 *  - NIVEAU_3 a 6 : sur soumission de documents, validee par un administrateur.
 */
@Service
@RequiredArgsConstructor
public class KYCService {

    public static final String EN_ATTENTE = "EN_ATTENTE";
    public static final String APPROUVE = "APPROUVE";
    public static final String REJETE = "REJETE";

    private final KYCSubmissionRepository kycSubmissionRepository;
    private final UserRepository userRepository;
    private final AdminJournalService journal;
    private final NotificationService notificationService;

    @Transactional
    public KYCSubmission soumettre(KYCSubmission submission) {
        if (submission.getNiveauVise().ordinal() <= KycLevel.NIVEAU_2_IDENTITE_DECLAREE.ordinal()) {
            throw new IllegalArgumentException(
                    "Les niveaux 1 et 2 sont automatiques (téléphone vérifié, profil complété) : "
                            + "soumettez un document pour le niveau 3 ou plus.");
        }
        submission.setStatut(EN_ATTENTE);
        return kycSubmissionRepository.save(submission);
    }

    @Transactional
    public KYCSubmission approuver(String submissionId, User admin) {
        KYCSubmission s = getEnAttente(submissionId);
        s.setStatut(APPROUVE);
        s.setValideParAdmin(admin);
        s.setTraiteLe(Instant.now());

        User user = s.getUser();
        elever(user, s.getNiveauVise());
        journal.journaliser(admin, "KYC_APPROUVE", "KYCSubmission", s.getId(),
                "Niveau " + s.getNiveauVise() + " accordé à " + user.getPhone());
        notificationService.notifier(user, "KYC validé", "Votre document a été accepté. Niveau : " + s.getNiveauVise());
        return kycSubmissionRepository.save(s);
    }

    @Transactional
    public KYCSubmission rejeter(String submissionId, User admin, String commentaire) {
        KYCSubmission s = getEnAttente(submissionId);
        s.setStatut(REJETE);
        s.setValideParAdmin(admin);
        s.setCommentaireValidation(commentaire);
        s.setTraiteLe(Instant.now());
        journal.journaliser(admin, "KYC_REJETE", "KYCSubmission", s.getId(), commentaire);
        notificationService.notifier(s.getUser(), "KYC refusé", "Document refusé : " + commentaire);
        return kycSubmissionRepository.save(s);
    }

    /** Monte le niveau KYC d'un utilisateur (jamais de retrogradation). */
    @Transactional
    public void elever(User user, KycLevel niveau) {
        if (niveau.ordinal() > user.getKycLevel().ordinal()) {
            user.setKycLevel(niveau);
            userRepository.save(user);
        }
    }

    /**
     * Niveau 2 "identite declaree" : accorde automatiquement des que les champs
     * d'identite obligatoires du profil sont renseignes.
     */
    @Transactional
    public void verifierIdentiteDeclaree(User u) {
        boolean complet = false;
        if (u instanceof ClientParticulier c) {
            complet = rempli(c.getNom(), c.getPrenom());
        } else if (u instanceof Artisan a) {
            complet = rempli(a.getNom(), a.getPrenom(), a.getCorpsDeMetier(), a.getZoneIntervention());
        } else if (u instanceof EntrepriseBTP e) {
            complet = rempli(e.getNomCommercial(), e.getNif(), e.getRccm());
        } else if (u instanceof IngenieurArchitecte i) {
            complet = rempli(i.getNom(), i.getPrenom(), i.getNumeroOrdre());
        } else if (u instanceof FournisseurMateriaux f) {
            complet = rempli(f.getNomBoutique());
        } else if (u instanceof ONGInstitution o) {
            complet = rempli(o.getRaisonSociale(), o.getNifOuRccm());
        }
        if (complet) {
            elever(u, KycLevel.NIVEAU_2_IDENTITE_DECLAREE);
        }
    }

    private static boolean rempli(String... valeurs) {
        for (String v : valeurs) {
            if (v == null || v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Verifie qu'un utilisateur atteint au moins le niveau KYC requis pour une action donnee. */
    public boolean estAutorise(User user, KycLevel niveauRequis) {
        return user.getKycLevel().ordinal() >= niveauRequis.ordinal();
    }

    /**
     * Regle de gestion n4 (Cahier des Charges Technique) : bloque une action si
     * l'utilisateur n'atteint pas le niveau KYC requis (HTTP 403 KYC_INSUFFISANT).
     */
    public void exigerNiveau(User user, KycLevel niveauRequis) {
        if (!estAutorise(user, niveauRequis)) {
            throw new KycInsuffisantException(user.getKycLevel(), niveauRequis);
        }
    }

    public List<KYCSubmission> mesSoumissions(String userId) {
        return kycSubmissionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<KYCSubmission> enAttente() {
        return kycSubmissionRepository.findByStatutOrderByCreatedAtAsc(EN_ATTENTE);
    }

    private KYCSubmission getEnAttente(String submissionId) {
        KYCSubmission s = kycSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new RessourceIntrouvableException("Soumission KYC introuvable: " + submissionId));
        if (!EN_ATTENTE.equals(s.getStatut())) {
            throw new IllegalStateException("Soumission déjà traitée (" + s.getStatut() + ")");
        }
        return s;
    }
}
