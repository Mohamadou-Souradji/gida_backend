package com.gidajari.dto;

import com.gidajari.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reponses du back-office. Contrairement aux vues publiques, elles exposent le telephone
 * des utilisateurs : l'equipe d'administration en a besoin pour le support terrain.
 */
public final class AdminVues {

    private AdminVues() {
    }

    /** Nom affichable d'un utilisateur, quel que soit son profil. */
    public static String nom(User u) {
        if (u == null) {
            return null;
        }
        Object cible = org.hibernate.Hibernate.unproxy(u);
        String nom = null;
        if (cible instanceof ClientParticulier c) {
            nom = joindre(c.getPrenom(), c.getNom());
        } else if (cible instanceof Artisan a) {
            nom = joindre(a.getPrenom(), a.getNom());
        } else if (cible instanceof IngenieurArchitecte i) {
            nom = joindre(i.getPrenom(), i.getNom());
        } else if (cible instanceof EntrepriseBTP e) {
            nom = e.getNomCommercial();
        } else if (cible instanceof ONGInstitution o) {
            nom = o.getRaisonSociale();
        } else if (cible instanceof FournisseurMateriaux f) {
            nom = f.getNomBoutique();
        }
        return nom == null || nom.isBlank() ? "(profil non renseigné)" : nom;
    }

    private static String joindre(String a, String b) {
        String s = ((a == null ? "" : a) + " " + (b == null ? "" : b)).trim();
        return s.isEmpty() ? null : s;
    }

    /** Resume d'un utilisateur pour les listes du back-office. */
    public record Personne(String id, String nom, String phone, String profileType, String kycLevel, boolean actif) {
        public static Personne de(User u) {
            if (u == null) {
                return null;
            }
            return new Personne(u.getId(), AdminVues.nom(u), u.getPhone(),
                    u.getProfileType() != null ? u.getProfileType().name() : null,
                    u.getKycLevel() != null ? u.getKycLevel().name() : null, u.isActif());
        }
    }

    /** Point d'une serie mensuelle (mois au format AAAA-MM). */
    public record PointMensuel(String mois, long inscriptions, long volumeFcfa, long commissionsFcfa, long retraitsFcfa) {}

    public record StatutSequestre(String statut, long nombre, long montantFcfa) {}

    public record Alerte(String niveau, String message, String lien) {}

    public record TableauDeBord(
            long utilisateurs, long nouveaux30j, long comptesSuspendus,
            Map<String, Long> utilisateursParProfil, Map<String, Long> utilisateursParKyc,
            long projets, long projetsPublies, long projetsAvecPrestataire,
            long appelsOffres, long produitsCatalogue,
            long volumeSecuriseFcfa, long enSequestreFcfa, long libereFcfa, long commissionsFcfa,
            long retraitsFcfa, long litigesOuverts, long kycEnAttente, long jalonsEnAttente,
            List<StatutSequestre> sequestreParStatut, List<PointMensuel> serieMensuelle,
            List<Alerte> alertes, Instant genereLe) {}

    /** Etat d'avancement d'un projet, deduit de sa publication et de son sequestre. */
    public enum EtatProjet { BROUILLON, PUBLIE, ATTENTE_PAIEMENT, EN_COURS, LITIGE, TERMINE }

    public record ProjetLigne(String id, String titre, String localisation, String typeProjet,
                              Long budgetEstimeFcfa, Instant createdAt, EtatProjet etat,
                              Personne client, Personne prestataire,
                              int jalons, int jalonsValides, Long montantSequestreFcfa, Long montantLibereFcfa,
                              String transactionId, String statutSequestre) {}

    public record TransactionLigne(String id, String projetId, String projetTitre,
                                   Personne payeur, Personne beneficiaire,
                                   Long montantTotalFcfa, Long montantLibereFcfa, long commissionsFcfa,
                                   String niveauCommission, String statut, String operateurMobileMoney,
                                   String numeroPayeur, String referencePaiement, String motifLitige,
                                   Instant createdAt, Instant updatedAt) {}

    public record TrancheLigne(String id, String jalonId, String jalonLibelle, Long montantFcfa,
                               Long commissionFcfa, Long montantNetFcfa, boolean libere, Instant libereLe) {}

    public record TransactionDetail(TransactionLigne transaction, List<TrancheLigne> tranches) {}

    public record ProjetDetail(ProjetLigne resume, String description, List<Jalon> jalons,
                               List<TransactionDetail> transactions) {}

    public record RetraitLigne(String id, Personne utilisateur, String operateur, String numero,
                               Long montantFcfa, String reference, Instant createdAt) {}

    public record Page<T>(List<T> elements, long total, int page, int taille) {}

    public record MembreEquipe(String id, String phone, String nom, String role, boolean actif,
                               boolean amorcage, boolean aUnCompte, Instant createdAt) {}

    /** Session admin courante (pour adapter les menus du back-office). */
    public record MoiAdmin(String role, List<String> permissions) {}
}
