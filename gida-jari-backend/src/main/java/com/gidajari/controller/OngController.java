package com.gidajari.controller;

import com.gidajari.dto.Requetes.AppelOffreRequest;
import com.gidajari.entity.AppelOffre;
import com.gidajari.entity.CandidatureAO;
import com.gidajari.entity.ONGInstitution;
import com.gidajari.entity.enums.AppelOffreStatus;
import com.gidajari.entity.enums.CandidatureStatus;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.repository.AppelOffreRepository;
import com.gidajari.repository.CandidatureAORepository;
import com.gidajari.repository.ONGInstitutionRepository;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.KYCService;
import com.gidajari.service.NotificationService;
import com.gidajari.service.exception.RessourceIntrouvableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;

/**
 * Profil 5 - ONG / Institution.
 * Roadmap 50% :
 *  - Inscription / profil institutionnel (raison sociale, NIF/RCCM, logo)
 *  - Formulaire de publication d'Appels d'Offres (AO)
 *  - Interface de consultation et de depouillement des candidatures recues
 */
@RestController
@RequestMapping("/api/ong-institutions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ONG_INSTITUTION')")
public class OngController {

    private static final ZoneId NIAMEY = ZoneId.of("Africa/Niamey");

    private final ONGInstitutionRepository ongRepository;
    private final AppelOffreRepository appelOffreRepository;
    private final CandidatureAORepository candidatureRepository;
    private final CurrentUserProvider currentUserProvider;
    private final KYCService kycService;
    private final NotificationService notificationService;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ONGInstitution getProfil(@PathVariable String id) {
        return ongRepository.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Institution introuvable"));
    }

    /**
     * Compléter le profil ONG/Institution avec les informations spécifiques.
     * L'utilisateur doit être connecté.
     */
    @PatchMapping("/complete-profile")
    @Transactional
    public ONGInstitution completerProfil(@RequestBody ONGInstitution dto) {
        String userId = currentUserProvider.getCurrentUserId();
        ONGInstitution existant = ongRepository.findById(userId)
                .orElseThrow(() -> new RessourceIntrouvableException("Institution introuvable"));
        
        // Mettre à jour les champs spécifiques
        if (dto.getRaisonSociale() != null) {
            existant.setRaisonSociale(dto.getRaisonSociale());
        }
        if (dto.getNifOuRccm() != null) {
            existant.setNifOuRccm(dto.getNifOuRccm());
        }
        if (dto.getTypeOrganisation() != null) {
            existant.setTypeOrganisation(dto.getTypeOrganisation());
        }
        if (dto.getStatutLegal() != null) {
            existant.setStatutLegal(dto.getStatutLegal());
        }
        if (dto.getOddCible() != null) {
            existant.setOddCible(dto.getOddCible());
        }
        if (dto.getLogoUrl() != null) {
            existant.setLogoUrl(dto.getLogoUrl());
        }
        
        ONGInstitution saved = ongRepository.save(existant);
        kycService.verifierIdentiteDeclaree(saved);
        return saved;
    }

    /**
     * L'institution est TOUJOURS l'utilisateur connecte (jamais une valeur du body).
     * Publier un AO est une mise en relation formelle : regle de gestion n4, niveau KYC minimum requis.
     */
    @PostMapping("/appels-offres")
    public AppelOffre publierAppelOffre(@Valid @RequestBody AppelOffreRequest req) {
        ONGInstitution institution = getProfil(currentUserProvider.getCurrentUserId());
        kycService.exigerNiveau(institution, KycLevel.NIVEAU_2_IDENTITE_DECLAREE);
        AppelOffre ao = new AppelOffre();
        ao.setInstitution(institution);
        ao.setTitre(req.titre());
        ao.setCahierDesChargesTexte(req.cahierDesChargesTexte());
        // Date limite incluse : fin de journee, heure de Niamey.
        ao.setDateLimiteDepot(req.dateLimiteDepot().plusDays(1).atStartOfDay(NIAMEY).toInstant().minusSeconds(1));
        ao.setStatut(AppelOffreStatus.PUBLIE);
        return appelOffreRepository.save(ao);
    }

    @GetMapping("/appels-offres")
    public List<AppelOffre> mesAppelsOffres() {
        return appelOffreRepository.findByInstitutionIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId());
    }

    @PatchMapping("/appels-offres/{appelOffreId}/cloturer")
    public AppelOffre cloturer(@PathVariable String appelOffreId) {
        AppelOffre ao = getAoProprietaire(appelOffreId);
        ao.setStatut(AppelOffreStatus.CLOTURE);
        return appelOffreRepository.save(ao);
    }

    @GetMapping("/appels-offres/{appelOffreId}/candidatures")
    public List<CandidatureAO> consulterCandidatures(@PathVariable String appelOffreId) {
        getAoProprietaire(appelOffreId);
        return candidatureRepository.findByAppelOffreIdOrderByCreatedAtAsc(appelOffreId);
    }

    /**
     * Depouillement : retenir ou rejeter une candidature recue.
     * Si retenue, l'appel d'offre passe automatiquement a ATTRIBUE et les autres
     * candidatures encore en evaluation sont rejetees.
     */
    @PatchMapping("/candidatures/{candidatureId}/statut")
    @Transactional
    public CandidatureAO statuerSurCandidature(@PathVariable String candidatureId,
                                               @RequestParam CandidatureStatus statut) {
        if (statut != CandidatureStatus.RETENUE && statut != CandidatureStatus.REJETEE
                && statut != CandidatureStatus.EN_EVALUATION) {
            throw new IllegalArgumentException("Statut attendu : EN_EVALUATION, RETENUE ou REJETEE");
        }
        CandidatureAO candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new RessourceIntrouvableException("Candidature introuvable"));
        AppelOffre ao = getAoProprietaire(candidature.getAppelOffre().getId());
        if (ao.getStatut() == AppelOffreStatus.ATTRIBUE || ao.getStatut() == AppelOffreStatus.ANNULE) {
            throw new IllegalStateException("Cet appel d'offres est déjà " + ao.getStatut());
        }
        candidature.setStatut(statut);
        candidatureRepository.save(candidature);
        notificationService.notifier(candidature.getCandidat(), "Candidature " + statut.name().toLowerCase(),
                "Votre candidature a \"" + ao.getTitre() + "\" est " + statut.name().toLowerCase() + ".");

        if (statut == CandidatureStatus.RETENUE) {
            ao.setStatut(AppelOffreStatus.ATTRIBUE);
            appelOffreRepository.save(ao);
            candidatureRepository.findByAppelOffreIdOrderByCreatedAtAsc(ao.getId()).stream()
                    .filter(c -> !c.getId().equals(candidature.getId()))
                    .filter(c -> c.getStatut() == CandidatureStatus.SOUMISE
                            || c.getStatut() == CandidatureStatus.EN_EVALUATION)
                    .forEach(c -> {
                        c.setStatut(CandidatureStatus.REJETEE);
                        candidatureRepository.save(c);
                        notificationService.notifier(c.getCandidat(), "Candidature rejetée",
                                "L'appel d'offres \"" + ao.getTitre() + "\" a été attribué a un autre candidat.");
                    });
        }
        return candidature;
    }

    private AppelOffre getAoProprietaire(String appelOffreId) {
        AppelOffre ao = appelOffreRepository.findById(appelOffreId)
                .orElseThrow(() -> new RessourceIntrouvableException("Appel d'offres introuvable"));
        currentUserProvider.exigerProprietaire(ao.getInstitution().getId());
        return ao;
    }
}
