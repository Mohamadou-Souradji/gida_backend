package com.gidajari.controller;

import com.gidajari.dto.Requetes.PropositionRequest;
import com.gidajari.entity.IngenieurArchitecte;
import com.gidajari.entity.PhaseEtude;
import com.gidajari.entity.Projet;
import com.gidajari.entity.PropositionEtude;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.entity.enums.PropositionEtudeStatus;
import com.gidajari.repository.IngenieurArchitecteRepository;
import com.gidajari.repository.ProjetRepository;
import com.gidajari.repository.PropositionEtudeRepository;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.KYCService;
import com.gidajari.service.NotificationService;
import com.gidajari.service.exception.RessourceIntrouvableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Profil 4bis (CDC) - Ingenieur / Architecte.
 * Roadmap 50% :
 *  - Creation de profil expert (numero d'ordre, diplome, agrement ministeriel)
 *  - Configuration de la grille tarifaire (horaire et forfaitaire)
 *  - Module de creation et d'envoi de propositions d'etudes decoupees en phases
 */
@RestController
@RequestMapping("/api/experts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INGENIEUR_ARCHITECTE')")
public class ExpertController {

    private final IngenieurArchitecteRepository expertRepository;
    private final ProjetRepository projetRepository;
    private final PropositionEtudeRepository propositionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final KYCService kycService;
    private final NotificationService notificationService;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public IngenieurArchitecte getProfil(@PathVariable String id) {
        return expertRepository.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Expert introuvable"));
    }

    /**
     * Compléter le profil expert avec les informations spécifiques.
     * L'utilisateur doit être connecté.
     */
    @PatchMapping("/complete-profile")
    @Transactional
    public IngenieurArchitecte completerProfil(@RequestBody IngenieurArchitecte dto) {
        String userId = currentUserProvider.getCurrentUserId();
        IngenieurArchitecte existant = expertRepository.findById(userId)
                .orElseThrow(() -> new RessourceIntrouvableException("Expert introuvable"));
        
        // Mettre à jour les champs spécifiques
        if (dto.getSpecialite() != null) {
            existant.setSpecialite(dto.getSpecialite());
        }
        if (dto.getNumeroOrdre() != null) {
            existant.setNumeroOrdre(dto.getNumeroOrdre());
        }
        if (dto.getAgrementMinisteriel() != null) {
            existant.setAgrementMinisteriel(dto.getAgrementMinisteriel());
        }
        
        IngenieurArchitecte saved = expertRepository.save(existant);
        kycService.verifierIdentiteDeclaree(saved);
        return saved;
    }

    /** Configure la grille tarifaire de l'expert CONNECTE (id resolu depuis le JWT). */
    @PatchMapping("/grille-tarifaire")
    public IngenieurArchitecte configurerGrilleTarifaire(@RequestBody String grilleTarifaireJson) {
        IngenieurArchitecte existant = getProfil(currentUserProvider.getCurrentUserId());
        existant.setGrilleTarifaireJson(grilleTarifaireJson);
        return expertRepository.save(existant);
    }

    /** Cree une proposition d'etude (brouillon) decoupee en phases, pour un projet publie. */
    @PostMapping("/propositions")
    @Transactional
    public PropositionEtude creerProposition(@Valid @RequestBody PropositionRequest req) {
        IngenieurArchitecte expert = getProfil(currentUserProvider.getCurrentUserId());
        Projet projet = projetRepository.findById(req.projetId())
                .orElseThrow(() -> new RessourceIntrouvableException("Projet introuvable"));
        if (!projet.isPublie()) {
            throw new IllegalStateException("Le projet n'est pas publié");
        }
        PropositionEtude p = new PropositionEtude();
        p.setExpert(expert);
        p.setProjet(projet);
        p.setTitre(req.titre());
        p.setDescription(req.description());
        p.setStatut(PropositionEtudeStatus.BROUILLON);
        int ordre = 0;
        long total = 0;
        for (var phaseReq : req.phases()) {
            PhaseEtude phase = new PhaseEtude();
            phase.setProposition(p);
            phase.setOrdre(++ordre);
            phase.setLibelle(phaseReq.libelle());
            phase.setDescription(phaseReq.description());
            phase.setMontantFcfa(phaseReq.montantFcfa());
            p.getPhases().add(phase);
            total += phaseReq.montantFcfa();
        }
        p.setMontantTotalFcfa(total);
        return propositionRepository.save(p);
    }

    /** Envoie une proposition d'etude au client du projet (mise en relation : KYC niveau 2 requis). */
    @PostMapping("/propositions/{id}/envoyer")
    @Transactional
    public PropositionEtude envoyerProposition(@PathVariable String id) {
        PropositionEtude p = propositionRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Proposition introuvable"));
        currentUserProvider.exigerProprietaire(p.getExpert().getId());
        kycService.exigerNiveau(p.getExpert(), KycLevel.NIVEAU_2_IDENTITE_DECLAREE);
        if (p.getStatut() != PropositionEtudeStatus.BROUILLON) {
            throw new IllegalStateException("Proposition déjà envoyee");
        }
        p.setStatut(PropositionEtudeStatus.ENVOYEE);
        p.setEnvoyeeLe(Instant.now());
        notificationService.notifier(p.getProjet().getClient(), "Proposition d'étude",
                "Vous avez reçu une proposition d'étude pour \"" + p.getProjet().getTitre() + "\".");
        return propositionRepository.save(p);
    }

    /** Mes propositions d'etudes (expert CONNECTE). */
    @GetMapping("/propositions/mes-propositions")
    public List<PropositionEtude> mesPropositions() {
        return propositionRepository.findByExpertIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId());
    }
}
