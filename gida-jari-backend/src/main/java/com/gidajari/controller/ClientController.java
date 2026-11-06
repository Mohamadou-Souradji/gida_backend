package com.gidajari.controller;

import com.gidajari.dto.Requetes.EngagementRequest;
import com.gidajari.dto.Requetes.JalonRequest;
import com.gidajari.dto.Requetes.MotifRequest;
import com.gidajari.dto.Requetes.PaiementRequest;
import com.gidajari.dto.Requetes.ProjetRequest;
import com.gidajari.dto.Vues.ProjetVue;
import com.gidajari.entity.ClientParticulier;
import com.gidajari.entity.EscrowTransaction;
import com.gidajari.entity.Jalon;
import com.gidajari.entity.Projet;
import com.gidajari.entity.PropositionEtude;
import com.gidajari.entity.enums.PropositionEtudeStatus;
import com.gidajari.repository.ClientParticulierRepository;
import com.gidajari.repository.PropositionEtudeRepository;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.EscrowService;
import com.gidajari.service.KYCService;
import com.gidajari.service.NotificationService;
import com.gidajari.service.ProjetService;
import com.gidajari.service.exception.AccesRefuseException;
import com.gidajari.service.exception.RessourceIntrouvableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Profil 1 - Client / Maitre d'ouvrage.
 * Roadmap 50% (Cahier des Charges, section 2) :
 *  - Auth OTP -> voir AuthController (commun aux 6 profils)
 *  - Gestion du profil client + soumission KYC de base -> voir KYCController
 *  - Creation, publication de projet et definition des jalons
 * Parcours complet du chantier : engagement d'un prestataire, paiement en
 * sequestre (Mobile Money), validation / rejet des jalons, propositions d'etudes.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT_PARTICULIER')")
public class ClientController {

    private final ClientParticulierRepository clientRepository;
    private final ProjetService projetService;
    private final EscrowService escrowService;
    private final CurrentUserProvider currentUserProvider;
    private final KYCService kycService;
    private final PropositionEtudeRepository propositionRepository;
    private final NotificationService notificationService;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ClientParticulier getProfil(@PathVariable String id) {
        return clientRepository.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
    }

    /**
     * Compléter le profil client avec les informations spécifiques.
     * L'utilisateur doit être connecté.
     */
    @PatchMapping("/complete-profile")
    @Transactional
    public ClientParticulier completerProfil(@RequestBody ClientParticulier dto) {
        String userId = currentUserProvider.getCurrentUserId();
        ClientParticulier existant = clientRepository.findById(userId)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
        
        // Mettre à jour uniquement les champs spécifiques (pas nom/prénom/adresse)
        if (dto.getTypeClient() != null) {
            existant.setTypeClient(dto.getTypeClient());
        }
        
        ClientParticulier saved = clientRepository.save(existant);
        kycService.verifierIdentiteDeclaree(saved);
        return saved;
    }

    // ----- Projets et jalons -----

    @GetMapping("/projets")
    public List<ProjetVue> mesProjets() {
        return projetService.mesProjets(currentUserProvider.getCurrentUserId());
    }

    /** Le client du projet est TOUJOURS l'utilisateur connecte (jamais une valeur du body). */
    @PostMapping("/projets")
    public Projet creerProjet(@Valid @RequestBody ProjetRequest req) {
        return projetService.creer(currentUserProvider.getCurrentUser(), req);
    }

    /** Publication : regle de gestion n4, niveau KYC minimum requis (NIVEAU_2). */
    @PostMapping("/projets/{projetId}/publier")
    public Projet publierProjet(@PathVariable String projetId) {
        return projetService.publier(projetId, currentUserProvider.getCurrentUser());
    }

    /** Definition des jalons techniques d'un projet (un ou plusieurs a la fois). */
    @PostMapping("/projets/{projetId}/jalons")
    public List<Jalon> definirJalons(@PathVariable String projetId, @Valid @RequestBody List<JalonRequest> jalons) {
        return projetService.ajouterJalons(projetId, currentUserProvider.getCurrentUser(), jalons);
    }

    @DeleteMapping("/jalons/{jalonId}")
    public ResponseEntity<Void> supprimerJalon(@PathVariable String jalonId) {
        projetService.supprimerJalon(jalonId, currentUserProvider.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    // ----- Sequestre (escrow) -----

    /** Engage un artisan ou une entreprise BTP : cree le sequestre (DRAFT). */
    @PostMapping("/projets/{projetId}/engager")
    public EscrowTransaction engager(@PathVariable String projetId, @Valid @RequestBody EngagementRequest req) {
        return escrowService.engager(projetId, currentUserProvider.getCurrentUser(), req.beneficiaireId(), req.niveauCommission());
    }

    /** Paiement Mobile Money vers le compte de sequestre (regle n1). */
    @PostMapping("/escrow/{transactionId}/payer")
    public EscrowTransaction payer(@PathVariable String transactionId, @Valid @RequestBody PaiementRequest req) {
        return escrowService.payer(transactionId, currentUserProvider.getCurrentUser(), req.operateur(), req.numero());
    }

    @PostMapping("/jalons/{jalonId}/valider")
    public Jalon validerJalon(@PathVariable String jalonId) {
        return escrowService.validerJalon(jalonId, currentUserProvider.getCurrentUser());
    }

    @PostMapping("/jalons/{jalonId}/rejeter")
    public Jalon rejeterJalon(@PathVariable String jalonId, @Valid @RequestBody MotifRequest req) {
        return escrowService.rejeterJalon(jalonId, currentUserProvider.getCurrentUser(), req.motif());
    }

    // ----- Propositions d'etudes recues (Ingenieurs / Architectes) -----

    @GetMapping("/propositions")
    public List<PropositionEtude> propositionsRecues() {
        return propositionRepository.findByProjet_Client_IdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId())
                .stream().filter(p -> p.getStatut() != PropositionEtudeStatus.BROUILLON).toList();
    }

    @PatchMapping("/propositions/{id}/{decision}")
    @Transactional
    public PropositionEtude deciderProposition(@PathVariable String id, @PathVariable String decision) {
        PropositionEtude p = propositionRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Proposition introuvable"));
        if (!p.getProjet().getClient().getId().equals(currentUserProvider.getCurrentUserId())) {
            throw new AccesRefuseException("Cette proposition concerne le projet d'un autre client");
        }
        if (p.getStatut() != PropositionEtudeStatus.ENVOYEE) {
            throw new IllegalStateException("Proposition déjà traitée (" + p.getStatut() + ")");
        }
        PropositionEtudeStatus statut = switch (decision) {
            case "accepter" -> PropositionEtudeStatus.ACCEPTEE;
            case "refuser" -> PropositionEtudeStatus.REFUSEE;
            default -> throw new IllegalArgumentException("Decision attendue : accepter ou refuser");
        };
        p.setStatut(statut);
        notificationService.notifier(p.getExpert(), "Proposition " + statut.name().toLowerCase(),
                "Votre proposition \"" + p.getTitre() + "\" a été " + statut.name().toLowerCase() + " par le client.");
        return propositionRepository.save(p);
    }
}
