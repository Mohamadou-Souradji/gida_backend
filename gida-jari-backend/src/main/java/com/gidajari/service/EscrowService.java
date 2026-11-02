package com.gidajari.service;

import com.gidajari.entity.EscrowMilestone;
import com.gidajari.entity.EscrowTransaction;
import com.gidajari.entity.Jalon;
import com.gidajari.entity.Projet;
import com.gidajari.entity.User;
import com.gidajari.entity.enums.EscrowStatus;
import com.gidajari.entity.enums.JalonStatus;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.entity.enums.NiveauCommission;
import com.gidajari.entity.enums.OperateurMobileMoney;
import com.gidajari.entity.enums.ProfileType;
import com.gidajari.integration.MobileMoneyGateway;
import com.gidajari.repository.EscrowMilestoneRepository;
import com.gidajari.repository.EscrowTransactionRepository;
import com.gidajari.repository.JalonRepository;
import com.gidajari.repository.ProjetRepository;
import com.gidajari.repository.UserRepository;
import com.gidajari.service.exception.AccesRefuseException;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service Escrow - coeur du systeme de confiance financier.
 *
 * Regle n1 (Cahier des Charges) : les fonds transitent EXCLUSIVEMENT par le
 * compte de sequestre Gida Jari, jamais de paiement direct entre les parties.
 * Regle n2 : la commission est prelevee uniquement au moment de la
 * liberation effective des fonds au prestataire (commission differee).
 * Regle n3 : un jalon soumis sans action du client pendant 48h est
 * auto-valide (voir JalonAutoValidationScheduler) et ses fonds liberes.
 * Regle n4 : payer et recevoir des fonds exige un niveau KYC minimum.
 *
 * Machine a etats : DRAFT -> PENDING_PAYMENT -> FUNDED -> PARTIAL_RELEASE
 * -> COMPLETED (ou FUNDED/PARTIAL_RELEASE -> DISPUTED -> RESOLVED_CLIENT /
 * RESOLVED_PROVIDER / REFUNDED).
 *
 * Les fonds liberes sont credites sur le solde Gida Jari (walletBalance) du
 * prestataire, qui les retire ensuite vers son Mobile Money (CompteService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EscrowService {

    /** Niveau KYC minimum pour alimenter un sequestre (client). */
    public static final KycLevel KYC_PAYEUR = KycLevel.NIVEAU_2_IDENTITE_DECLAREE;
    /** Niveau KYC minimum pour etre engage comme prestataire et recevoir des fonds. */
    public static final KycLevel KYC_BENEFICIAIRE = KycLevel.NIVEAU_3_PIECE_IDENTITE;

    private static final Set<EscrowStatus> ETATS_TERMINAUX = EnumSet.of(
            EscrowStatus.COMPLETED, EscrowStatus.RESOLVED_CLIENT,
            EscrowStatus.RESOLVED_PROVIDER, EscrowStatus.REFUNDED);

    private static final Set<EscrowStatus> ETATS_FINANCES = EnumSet.of(
            EscrowStatus.FUNDED, EscrowStatus.PARTIAL_RELEASE);

    private final EscrowTransactionRepository escrowTransactionRepository;
    private final EscrowMilestoneRepository milestoneRepository;
    private final ProjetRepository projetRepository;
    private final JalonRepository jalonRepository;
    private final UserRepository userRepository;
    private final KYCService kycService;
    private final NotificationService notificationService;
    private final AdminJournalService journal;
    private final MobileMoneyGateway mobileMoneyGateway;

    @Value("${gida-jari.escrow.commission.standard}")
    private double tauxStandard;

    @Value("${gida-jari.escrow.commission.premium}")
    private double tauxPremium;

    @Value("${gida-jari.escrow.commission.pro}")
    private double tauxPro;

    // ------------------------------------------------------------------
    // Creation et financement
    // ------------------------------------------------------------------

    /**
     * Le client engage un prestataire (artisan ou entreprise BTP) sur son projet :
     * cree le sequestre (DRAFT) avec une tranche (milestone) par jalon.
     */
    @Transactional
    public EscrowTransaction engager(String projetId, User client, String beneficiaireId, NiveauCommission niveau) {
        Projet projet = getProjet(projetId);
        exigerMemeUtilisateur(projet.getClient(), client, "Seul le client du projet peut engager un prestataire");
        if (!projet.isPublie()) {
            throw new IllegalStateException("Publiez le projet avant d'engager un prestataire");
        }
        kycService.exigerNiveau(client, KYC_PAYEUR);

        if (sequestreActif(projetId).isPresent()) {
            throw new IllegalStateException("Un prestataire est déjà engagé sur ce projet");
        }

        User beneficiaire = userRepository.findById(beneficiaireId)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestataire introuvable: " + beneficiaireId));
        if (beneficiaire.getProfileType() != ProfileType.ARTISAN
                && beneficiaire.getProfileType() != ProfileType.ENTREPRISE_BTP) {
            throw new IllegalArgumentException("Le prestataire doit être un artisan ou une entreprise BTP");
        }
        kycService.exigerNiveau(beneficiaire, KYC_BENEFICIAIRE);

        List<Jalon> jalons = jalonRepository.findByProjetIdOrderByOrdreAsc(projetId);
        if (jalons.isEmpty()) {
            throw new IllegalStateException("Définissez au moins un jalon avant d'engager un prestataire");
        }
        if (jalons.stream().anyMatch(j -> j.getMontantFcfa() == null || j.getMontantFcfa() <= 0)) {
            throw new IllegalStateException("Chaque jalon doit avoir un montant positif");
        }

        EscrowTransaction t = new EscrowTransaction();
        t.setProjet(projet);
        t.setPayeur(client);
        t.setBeneficiaire(beneficiaire);
        t.setNiveauCommission(niveau != null ? niveau : NiveauCommission.STANDARD);
        t.setStatut(EscrowStatus.DRAFT);
        long total = 0;
        for (Jalon jalon : jalons) {
            EscrowMilestone m = new EscrowMilestone();
            m.setEscrowTransaction(t);
            m.setJalon(jalon);
            m.setMontantFcfa(jalon.getMontantFcfa());
            t.getMilestones().add(m);
            total += jalon.getMontantFcfa();
        }
        t.setMontantTotalFcfa(total);
        t.setUpdatedAt(Instant.now());
        EscrowTransaction saved = escrowTransactionRepository.save(t);

        notificationService.notifier(beneficiaire, "Nouvelle mission",
                "Vous êtes engagé sur le projet \"" + projet.getTitre() + "\" (" + total + " FCFA en séquestre après paiement).");
        return saved;
    }

    /** DRAFT -> PENDING_PAYMENT : le client paie par Mobile Money vers le compte de sequestre. */
    @Transactional
    public EscrowTransaction payer(String transactionId, User client, OperateurMobileMoney operateur, String numero) {
        EscrowTransaction t = getOrThrow(transactionId);
        exigerMemeUtilisateur(t.getPayeur(), client, "Seul le client payeur peut alimenter ce séquestre");
        exigerEtat(t, EscrowStatus.DRAFT);
        kycService.exigerNiveau(client, KYC_PAYEUR);

        t.setStatut(EscrowStatus.PENDING_PAYMENT);
        t.setOperateurMobileMoney(operateur.name());
        t.setNumeroPayeur(numero);
        t.setUpdatedAt(Instant.now());

        MobileMoneyGateway.Resultat res = mobileMoneyGateway.initierCollecte(
                operateur, numero, t.getMontantTotalFcfa(), "Séquestre projet " + t.getProjet().getTitre());
        t.setReferencePaiement(res.reference());
        if (res.confirme()) {
            financer(t);
        }
        return escrowTransactionRepository.save(t);
    }

    /** Callback operateur Mobile Money : PENDING_PAYMENT -> FUNDED (succes) ou -> DRAFT (echec). */
    @Transactional
    public EscrowTransaction confirmerPaiementOperateur(String reference, boolean succes) {
        EscrowTransaction t = escrowTransactionRepository.findByReferencePaiement(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement inconnu: " + reference));
        exigerEtat(t, EscrowStatus.PENDING_PAYMENT);
        if (succes) {
            financer(t);
        } else {
            t.setStatut(EscrowStatus.DRAFT);
            t.setUpdatedAt(Instant.now());
            notificationService.notifier(t.getPayeur(), "Paiement échoué",
                    "Le paiement Mobile Money n'a pas abouti. Vous pouvez réessayer.");
        }
        return escrowTransactionRepository.save(t);
    }

    /** PENDING_PAYMENT -> FUNDED. Methode historique conservee (utile aux tests et a l'administration). */
    @Transactional
    public EscrowTransaction confirmerFinancement(String transactionId) {
        EscrowTransaction t = getOrThrow(transactionId);
        exigerEtat(t, EscrowStatus.PENDING_PAYMENT);
        financer(t);
        return escrowTransactionRepository.save(t);
    }

    private void financer(EscrowTransaction t) {
        t.setStatut(EscrowStatus.FUNDED);
        t.setUpdatedAt(Instant.now());
        notificationService.notifier(t.getPayeur(), "Séquestre alimenté",
                t.getMontantTotalFcfa() + " FCFA sont bloques chez Gida Jari pour \"" + t.getProjet().getTitre() + "\".");
        notificationService.notifier(t.getBeneficiaire(), "Fonds sécurisés",
                "Le client a alimenté le séquestre de \"" + t.getProjet().getTitre() + "\". Vous pouvez démarrer les travaux.");
    }

    // ------------------------------------------------------------------
    // Jalons : preuve, validation, rejet
    // ------------------------------------------------------------------

    /** Le prestataire engage soumet sa preuve photo geolocalisee : A_FAIRE/REJETE -> SOUMIS. */
    @Transactional
    public Jalon soumettrePreuve(String jalonId, User prestataire, String photoUrl, Double latitude, Double longitude) {
        Jalon jalon = getJalon(jalonId);
        EscrowTransaction t = sequestreDuJalon(jalon);
        exigerMemeUtilisateur(t.getBeneficiaire(), prestataire, "Seul le prestataire engagé peut soumettre une preuve");
        if (!ETATS_FINANCES.contains(t.getStatut())) {
            throw new IllegalStateException("Le séquestre n'est pas alimenté (état " + t.getStatut() + ")");
        }
        if (jalon.getStatut() != JalonStatus.A_FAIRE && jalon.getStatut() != JalonStatus.REJETE) {
            throw new IllegalStateException("Ce jalon est déjà " + jalon.getStatut());
        }
        jalon.setPreuvePhotoUrls(photoUrl);
        jalon.setLatitude(latitude);
        jalon.setLongitude(longitude);
        jalon.setStatut(JalonStatus.SOUMIS);
        jalon.setSoumisLe(Instant.now());
        jalon.setCommentaireClient(null);
        jalonRepository.save(jalon);

        notificationService.notifier(t.getPayeur(), "Jalon à valider",
                "\"" + jalon.getLibelle() + "\" a été soumis. Sans reponse sous 48h il sera validé automatiquement.");
        return jalon;
    }

    /** Le client valide un jalon soumis : SOUMIS -> VALIDE et liberation de la tranche. */
    @Transactional
    public Jalon validerJalon(String jalonId, User client) {
        Jalon jalon = getJalon(jalonId);
        EscrowTransaction t = sequestreDuJalon(jalon);
        exigerMemeUtilisateur(t.getPayeur(), client, "Seul le client du projet peut valider ce jalon");
        if (jalon.getStatut() != JalonStatus.SOUMIS) {
            throw new IllegalStateException("Seul un jalon SOUMIS peut être validé (état " + jalon.getStatut() + ")");
        }
        marquerValideEtLiberer(jalon, t);
        return jalon;
    }

    /** Le client rejette la preuve : SOUMIS -> REJETE (le prestataire peut resoumettre). */
    @Transactional
    public Jalon rejeterJalon(String jalonId, User client, String motif) {
        Jalon jalon = getJalon(jalonId);
        EscrowTransaction t = sequestreDuJalon(jalon);
        exigerMemeUtilisateur(t.getPayeur(), client, "Seul le client du projet peut rejeter ce jalon");
        if (jalon.getStatut() != JalonStatus.SOUMIS) {
            throw new IllegalStateException("Seul un jalon SOUMIS peut être rejeté");
        }
        jalon.setStatut(JalonStatus.REJETE);
        jalon.setCommentaireClient(motif);
        jalonRepository.save(jalon);
        notificationService.notifier(t.getBeneficiaire(), "Jalon refusé",
                "\"" + jalon.getLibelle() + "\" a été refusé : " + motif + ". Corrigez puis soumettez une nouvelle preuve.");
        return jalon;
    }

    /** Regle n3 - appele par la tache planifiee pour un jalon SOUMIS depuis plus de 48h. */
    @Transactional
    public void autoValider(Jalon jalonDetache) {
        Jalon jalon = jalonRepository.findById(jalonDetache.getId()).orElse(null);
        if (jalon == null || jalon.getStatut() != JalonStatus.SOUMIS) {
            return;
        }
        Optional<EscrowMilestone> m = milestoneRepository.findByJalonId(jalon.getId());
        if (m.isPresent() && ETATS_FINANCES.contains(m.get().getEscrowTransaction().getStatut())) {
            marquerValideEtLiberer(jalon, m.get().getEscrowTransaction());
            log.info("Jalon {} auto-valide (48h sans action du client), fonds liberes", jalon.getId());
        } else {
            // Sequestre en litige ou absent : on ne libere rien, le jalon reste en attente d'arbitrage.
            log.warn("Jalon {} non auto-valide : sequestre absent ou non finance", jalon.getId());
        }
    }

    private void marquerValideEtLiberer(Jalon jalon, EscrowTransaction t) {
        jalon.setStatut(JalonStatus.VALIDE);
        jalon.setValideLe(Instant.now());
        jalonRepository.save(jalon);
        EscrowMilestone m = t.getMilestones().stream()
                .filter(x -> x.getJalon() != null && x.getJalon().getId().equals(jalon.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune tranché de séquestre pour ce jalon"));
        libererTranche(t, m);
    }

    // ------------------------------------------------------------------
    // Liberation des fonds (commission differee)
    // ------------------------------------------------------------------

    /**
     * FUNDED / PARTIAL_RELEASE -> PARTIAL_RELEASE ou COMPLETED : libere une tranche
     * au prestataire apres calcul et prelevement de la commission (regle n2).
     */
    @Transactional
    public EscrowTransaction libererTranche(EscrowTransaction t, EscrowMilestone milestone) {
        if (!ETATS_FINANCES.contains(t.getStatut())) {
            throw new IllegalStateException("Impossible de liberer une tranché : séquestre en état " + t.getStatut());
        }
        crediterTranche(t, milestone);
        t.setStatut(t.getMontantLibereFcfa() >= t.getMontantTotalFcfa()
                ? EscrowStatus.COMPLETED
                : EscrowStatus.PARTIAL_RELEASE);
        t.setUpdatedAt(Instant.now());
        if (t.getStatut() == EscrowStatus.COMPLETED) {
            notificationService.notifier(t.getPayeur(), "Chantier terminé",
                    "Tous les jalons de \"" + t.getProjet().getTitre() + "\" sont validés. Pensez a noter le prestataire.");
        }
        return escrowTransactionRepository.save(t);
    }

    private void crediterTranche(EscrowTransaction t, EscrowMilestone milestone) {
        if (milestone.isLibere()) {
            return;
        }
        long commission = calculerCommission(milestone.getMontantFcfa(), t.getNiveauCommission());
        long net = milestone.getMontantFcfa() - commission;

        milestone.setCommissionFcfa(commission);
        milestone.setMontantNetFcfa(net);
        milestone.setLibere(true);
        milestone.setLibereLe(Instant.now());

        t.setMontantLibereFcfa(t.getMontantLibereFcfa() + milestone.getMontantFcfa());

        User beneficiaire = t.getBeneficiaire();
        beneficiaire.setWalletBalance(beneficiaire.getWalletBalance() + net);
        userRepository.save(beneficiaire);

        notificationService.notifier(beneficiaire, "Fonds libérés",
                net + " FCFA crédités sur votre solde Gida Jari (commission " + commission + " FCFA).");
    }

    /** Commission plateforme selon le niveau (Standard 2% / Premium 1.5% / Pro 1%). */
    public long calculerCommission(long montantFcfa, NiveauCommission niveau) {
        double taux = switch (niveau) {
            case STANDARD -> tauxStandard;
            case PREMIUM -> tauxPremium;
            case PRO -> tauxPro;
        };
        return Math.round(montantFcfa * taux);
    }

    // ------------------------------------------------------------------
    // Litiges (mediation)
    // ------------------------------------------------------------------

    /** FUNDED / PARTIAL_RELEASE -> DISPUTED, a l'initiative du client ou du prestataire. */
    @Transactional
    public EscrowTransaction ouvrirLitige(String transactionId, User demandeur, String motif) {
        EscrowTransaction t = getOrThrow(transactionId);
        boolean estPartie = memeId(t.getPayeur(), demandeur) || memeId(t.getBeneficiaire(), demandeur);
        if (!estPartie) {
            throw new AccesRefuseException("Seules les parties du contrat peuvent ouvrir un litige");
        }
        if (!ETATS_FINANCES.contains(t.getStatut())) {
            throw new IllegalStateException("Un litige ne peut être ouvert que sur un séquestre alimenté (état " + t.getStatut() + ")");
        }
        t.setStatut(EscrowStatus.DISPUTED);
        t.setMotifLitige(motif);
        t.setLitigeOuvertPar(demandeur);
        t.setUpdatedAt(Instant.now());
        // Les jalons en attente de validation sont geles (plus d'auto-validation pendant le litige).
        jalonRepository.findByProjetIdOrderByOrdreAsc(t.getProjet().getId()).stream()
                .filter(j -> j.getStatut() == JalonStatus.SOUMIS)
                .forEach(j -> {
                    j.setStatut(JalonStatus.LITIGE);
                    jalonRepository.save(j);
                });

        User autrePartie = memeId(t.getPayeur(), demandeur) ? t.getBeneficiaire() : t.getPayeur();
        notificationService.notifier(autrePartie, "Litige ouvert",
                "Un litige a été ouvert sur \"" + t.getProjet().getTitre() + "\" : " + motif);
        return escrowTransactionRepository.save(t);
    }

    /**
     * Arbitrage par un administrateur : DISPUTED -> RESOLVED_PROVIDER (le reste est
     * libere au prestataire, commission comprise), RESOLVED_CLIENT ou REFUNDED
     * (le reste non libere est rembourse sur le solde Gida Jari du client).
     */
    @Transactional
    public EscrowTransaction resoudreLitige(String transactionId, EscrowStatus resolution, User admin, String commentaire) {
        EscrowTransaction t = getOrThrow(transactionId);
        exigerEtat(t, EscrowStatus.DISPUTED);
        if (resolution != EscrowStatus.RESOLVED_CLIENT
                && resolution != EscrowStatus.RESOLVED_PROVIDER
                && resolution != EscrowStatus.REFUNDED) {
            throw new IllegalArgumentException("Resolution invalide: " + resolution);
        }

        if (resolution == EscrowStatus.RESOLVED_PROVIDER) {
            t.getMilestones().stream().filter(m -> !m.isLibere()).forEach(m -> crediterTranche(t, m));
            jalonRepository.findByProjetIdOrderByOrdreAsc(t.getProjet().getId()).stream()
                    .filter(j -> j.getStatut() != JalonStatus.VALIDE)
                    .forEach(j -> {
                        j.setStatut(JalonStatus.VALIDE);
                        j.setValideLe(Instant.now());
                        jalonRepository.save(j);
                    });
        } else {
            long reste = t.getMontantTotalFcfa() - t.getMontantLibereFcfa();
            if (reste > 0) {
                User client = t.getPayeur();
                client.setWalletBalance(client.getWalletBalance() + reste);
                userRepository.save(client);
                notificationService.notifier(client, "Remboursement",
                        reste + " FCFA remboursés sur votre solde Gida Jari.");
            }
        }

        t.setStatut(resolution);
        t.setUpdatedAt(Instant.now());
        journal.journaliser(admin, "LITIGE_RESOLU", "EscrowTransaction", t.getId(),
                resolution + (commentaire != null ? " - " + commentaire : ""));
        String message = "Le litige sur \"" + t.getProjet().getTitre() + "\" a été tranché : " + resolution
                + (commentaire != null ? " (" + commentaire + ")" : "");
        notificationService.notifier(t.getPayeur(), "Litige tranché", message);
        notificationService.notifier(t.getBeneficiaire(), "Litige tranché", message);
        return escrowTransactionRepository.save(t);
    }

    // ------------------------------------------------------------------
    // Lectures
    // ------------------------------------------------------------------

    /** Sequestre en cours (non termine) d'un projet, s'il existe. */
    public Optional<EscrowTransaction> sequestreActif(String projetId) {
        return escrowTransactionRepository.findByProjetIdOrderByCreatedAtDesc(projetId).stream()
                .filter(t -> !ETATS_TERMINAUX.contains(t.getStatut()))
                .findFirst();
    }

    /** Dernier sequestre (actif ou termine) d'un projet. */
    public Optional<EscrowTransaction> dernierSequestre(String projetId) {
        return escrowTransactionRepository.findByProjetIdOrderByCreatedAtDesc(projetId).stream().findFirst();
    }

    public List<EscrowTransaction> missionsDe(String beneficiaireId) {
        return escrowTransactionRepository.findByBeneficiaireIdOrderByCreatedAtDesc(beneficiaireId);
    }

    public List<EscrowTransaction> litigesOuverts() {
        return escrowTransactionRepository.findByStatutOrderByUpdatedAtAsc(EscrowStatus.DISPUTED);
    }

    public EscrowTransaction getOrThrow(String id) {
        return escrowTransactionRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Transaction escrow introuvable: " + id));
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private EscrowTransaction sequestreDuJalon(Jalon jalon) {
        return milestoneRepository.findByJalonId(jalon.getId())
                .map(EscrowMilestone::getEscrowTransaction)
                .orElseThrow(() -> new IllegalStateException("Aucun prestataire n'est engagé sur ce jalon"));
    }

    private Projet getProjet(String projetId) {
        return projetRepository.findById(projetId)
                .orElseThrow(() -> new RessourceIntrouvableException("Projet introuvable: " + projetId));
    }

    private Jalon getJalon(String jalonId) {
        return jalonRepository.findById(jalonId)
                .orElseThrow(() -> new RessourceIntrouvableException("Jalon introuvable: " + jalonId));
    }

    private void exigerEtat(EscrowTransaction t, EscrowStatus attendu) {
        if (t.getStatut() != attendu) {
            throw new IllegalStateException("Transition invalide: attendu " + attendu + ", trouve " + t.getStatut());
        }
    }

    private static boolean memeId(User a, User b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }

    private static void exigerMemeUtilisateur(User attendu, User acteur, String message) {
        if (!memeId(attendu, acteur)) {
            throw new AccesRefuseException(message);
        }
    }
}
