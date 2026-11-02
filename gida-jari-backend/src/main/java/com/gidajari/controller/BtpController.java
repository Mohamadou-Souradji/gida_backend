package com.gidajari.controller;

import com.gidajari.dto.Requetes.CandidatureRequest;
import com.gidajari.entity.AppelOffre;
import com.gidajari.entity.CandidatureAO;
import com.gidajari.entity.EntrepriseBTP;
import com.gidajari.entity.enums.AppelOffreStatus;
import com.gidajari.entity.enums.CandidatureStatus;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.repository.AppelOffreRepository;
import com.gidajari.repository.CandidatureAORepository;
import com.gidajari.repository.EntrepriseBTPRepository;
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
import java.util.Locale;

/**
 * Profil 4 - Entreprise BTP.
 * Roadmap 50% :
 *  - Inscription entreprise + telechargement des pieces justificatives (NIF, RCCM, KYC)
 *  - Moteur de recherche d'Appels d'Offres (AO)
 *  - Soumission de candidature technique/commerciale a un AO
 */
@RestController
@RequestMapping("/api/entreprises-btp")
@RequiredArgsConstructor
public class BtpController {

    private final EntrepriseBTPRepository entrepriseRepository;
    private final AppelOffreRepository appelOffreRepository;
    private final CandidatureAORepository candidatureRepository;
    private final CurrentUserProvider currentUserProvider;
    private final KYCService kycService;
    private final NotificationService notificationService;

    /** Annuaire des entreprises (pour qu'un client engage une entreprise sur son chantier). */
    @GetMapping
    public List<EntrepriseBTP> annuaire() {
        return entrepriseRepository.findAllByOrderByProfileScoreDesc();
    }

    @GetMapping("/{id}")
    public EntrepriseBTP getProfil(@PathVariable String id) {
        return entrepriseRepository.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Entreprise introuvable"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ENTREPRISE_BTP')")
    @Transactional
    public EntrepriseBTP mettreAJourProfil(@PathVariable String id, @RequestBody EntrepriseBTP maj) {
        currentUserProvider.exigerProprietaire(id);
        EntrepriseBTP existant = getProfil(id);
        existant.setNomCommercial(maj.getNomCommercial());
        existant.setNif(maj.getNif());
        existant.setRccm(maj.getRccm());
        existant.setDomaineActivite(maj.getDomaineActivite());
        existant.setEffectif(maj.getEffectif());
        EntrepriseBTP saved = entrepriseRepository.save(existant);
        kycService.verifierIdentiteDeclaree(saved);
        return saved;
    }

    /** Moteur de recherche d'AO publies (mot-cle facultatif dans le titre ou le cahier des charges). */
    @GetMapping("/appels-offres")
    public List<AppelOffre> rechercherAppelsOffres(@RequestParam(required = false) String q) {
        String motCle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return appelOffreRepository.findByStatutOrderByCreatedAtDesc(AppelOffreStatus.PUBLIE).stream()
                .filter(ao -> motCle.isEmpty()
                        || contient(ao.getTitre(), motCle)
                        || contient(ao.getCahierDesChargesTexte(), motCle))
                .toList();
    }

    @GetMapping("/candidatures")
    @PreAuthorize("hasRole('ENTREPRISE_BTP')")
    public List<CandidatureAO> mesCandidatures() {
        return candidatureRepository.findByCandidatIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId());
    }

    /**
     * Le candidat est TOUJOURS l'entreprise connectee (jamais une valeur du body).
     * Candidater est une mise en relation formelle : regle de gestion n4, niveau KYC minimum requis.
     */
    @PostMapping("/appels-offres/{appelOffreId}/candidatures")
    @PreAuthorize("hasRole('ENTREPRISE_BTP')")
    @Transactional
    public CandidatureAO soumettreCandidature(@PathVariable String appelOffreId,
                                              @Valid @RequestBody CandidatureRequest req) {
        String entrepriseId = currentUserProvider.getCurrentUserId();
        AppelOffre ao = appelOffreRepository.findById(appelOffreId)
                .orElseThrow(() -> new RessourceIntrouvableException("Appel d'offres introuvable"));
        EntrepriseBTP entreprise = getProfil(entrepriseId);
        kycService.exigerNiveau(entreprise, KycLevel.NIVEAU_3_PIECE_IDENTITE);

        if (ao.getStatut() != AppelOffreStatus.PUBLIE) {
            throw new IllegalStateException("Cet appel d'offres n'accepté plus de candidatures");
        }
        if (ao.getDateLimiteDepot() != null && ao.getDateLimiteDepot().isBefore(Instant.now())) {
            throw new IllegalStateException("La date limité de depot est dépassée");
        }
        if (candidatureRepository.existsByAppelOffreIdAndCandidatId(appelOffreId, entrepriseId)) {
            throw new IllegalStateException("Vous avez déjà candidate a cet appel d'offres");
        }

        CandidatureAO candidature = new CandidatureAO();
        candidature.setAppelOffre(ao);
        candidature.setCandidat(entreprise);
        candidature.setPropositionTechnique(req.propositionTechnique());
        candidature.setMontantPropositionFcfa(req.montantPropositionFcfa());
        candidature.setStatut(CandidatureStatus.SOUMISE);
        CandidatureAO saved = candidatureRepository.save(candidature);
        notificationService.notifier(ao.getInstitution(), "Nouvelle candidature",
                "Une candidature a été déposée pour \"" + ao.getTitre() + "\".");
        return saved;
    }

    private static boolean contient(String texte, String motCle) {
        return texte != null && texte.toLowerCase(Locale.ROOT).contains(motCle);
    }
}
