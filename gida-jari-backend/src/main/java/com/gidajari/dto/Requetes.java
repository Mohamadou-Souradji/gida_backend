package com.gidajari.dto;

import com.gidajari.entity.enums.AdminRole;
import com.gidajari.entity.enums.EscrowStatus;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.entity.enums.NiveauCommission;
import com.gidajari.entity.enums.OperateurMobileMoney;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Corps de requete (entrees de l'API). Les controleurs n'acceptent jamais
 * directement une entite JPA en entree : on evite ainsi qu'un client force
 * un id, un statut ou un proprietaire (mass assignment).
 */
public final class Requetes {

    private Requetes() {
    }

    public record JalonRequest(@NotBlank String libelle, @NotNull @Positive Long montantFcfa) {}

    public record ProjetRequest(
            @NotBlank @Size(max = 200) String titre,
            String description,
            @NotBlank String typeProjet,
            @NotBlank String localisation,
            Long budgetEstimeFcfa,
            @Valid List<JalonRequest> jalons) {}

    public record EngagementRequest(@NotBlank String beneficiaireId, NiveauCommission niveauCommission) {}

    public record PaiementRequest(@NotNull OperateurMobileMoney operateur, @NotBlank String numero) {}

    public record MotifRequest(@NotBlank @Size(max = 2000) String motif) {}

    public record ResolutionLitigeRequest(@NotNull EscrowStatus resolution, String commentaire) {}

    public record CallbackPaiementRequest(@NotBlank String reference, boolean succes) {}

    public record RetraitRequest(@NotNull OperateurMobileMoney operateur, @NotBlank String numero,
                                 @NotNull @Positive Long montantFcfa) {}

    public record AppelOffreRequest(@NotBlank String titre, @NotBlank String cahierDesChargesTexte,
                                    @NotNull LocalDate dateLimiteDepot) {}

    public record CandidatureRequest(@NotBlank String propositionTechnique,
                                     @NotNull @Positive Long montantPropositionFcfa) {}

    public record ProduitRequest(@NotBlank String nom, String categorie, String unite,
                                 @NotNull @Positive Long prixUnitaireFcfa, @Min(0) Integer stockDisponible,
                                 Boolean actif) {}

    public record PhaseRequest(@NotBlank String libelle, String description, @NotNull @Positive Long montantFcfa) {}

    public record PropositionRequest(@NotBlank String projetId, @NotBlank String titre, String description,
                                     @NotEmpty @Valid List<PhaseRequest> phases) {}

    public record NotationRequest(
            @NotBlank String projetId,
            @NotBlank String notePourId,
            @NotNull @Min(1) @Max(5) Integer qualite,
            @NotNull @Min(1) @Max(5) Integer delai,
            @NotNull @Min(1) @Max(5) Integer communication,
            @NotNull @Min(1) @Max(5) Integer respectBudget,
            @NotNull @Min(1) @Max(5) Integer proprete,
            @Size(max = 2000) String commentaire) {}

    public record NouveauMessageRequest(@NotBlank String destinataireId, String projetId,
                                        @NotBlank @Size(max = 4000) String contenu) {}

    public record MessageRequest(@NotBlank @Size(max = 4000) String contenu) {}

    public record PortfolioRequest(@NotBlank String titre, String description, @NotBlank String photoUrl) {}

    public record LangueRequest(@NotBlank String langue) {}

    public record KycDecisionRequest(String commentaire) {}

    /** Ajout d'un membre a l'equipe d'administration (super-admin uniquement). */
    public record MembreEquipeRequest(@NotBlank String phone, @Size(max = 120) String nom, @NotNull AdminRole role) {}

    /** Modification d'un membre : chaque champ est facultatif. */
    public record MembreEquipeModificationRequest(AdminRole role, Boolean actif, @Size(max = 120) String nom) {}

    public record KycSubmissionRequest(@NotNull KycLevel niveauVise, @NotBlank String typeDocument,
                                       @NotBlank String documentUrl) {}
}
