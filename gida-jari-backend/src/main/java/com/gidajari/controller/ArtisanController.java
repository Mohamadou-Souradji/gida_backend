package com.gidajari.controller;

import com.gidajari.entity.Artisan;
import com.gidajari.entity.enums.DisponibiliteStatus;
import com.gidajari.repository.ArtisanRepository;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.KYCService;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Profil 2 - Artisan.
 * Roadmap 50% :
 *  - Inscription, corps de metier, zone d'intervention
 *  - Bascule d'etat de disponibilite (AVAILABLE_NOW / UNAVAILABLE)
 *  - Envoi de preuve photo geolocalisee GPS pour la validation d'un jalon -> MissionController
 */
@RestController
@RequestMapping("/api/artisans")
@RequiredArgsConstructor
public class ArtisanController {

    private final ArtisanRepository artisanRepository;
    private final CurrentUserProvider currentUserProvider;
    private final KYCService kycService;

    @GetMapping("/{id}")
    public Artisan getProfil(@PathVariable String id) {
        return artisanRepository.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Artisan introuvable"));
    }

    /**
     * Compléter le profil artisan avec les informations spécifiques.
     * L'utilisateur doit être connecté.
     */
    @PatchMapping("/complete-profile")
    @PreAuthorize("hasRole('ARTISAN')")
    @Transactional
    public Artisan completerProfil(@RequestBody Artisan dto) {
        String userId = currentUserProvider.getCurrentUserId();
        Artisan existant = artisanRepository.findById(userId)
                .orElseThrow(() -> new RessourceIntrouvableException("Artisan introuvable"));
        
        // Mettre à jour les champs spécifiques
        if (dto.getCorpsDeMetier() != null) {
            existant.setCorpsDeMetier(dto.getCorpsDeMetier());
        }
        if (dto.getZoneIntervention() != null) {
            existant.setZoneIntervention(dto.getZoneIntervention());
        }
        if (dto.getTarifJournalierFcfa() != null) {
            existant.setTarifJournalierFcfa(dto.getTarifJournalierFcfa());
        }
        
        Artisan saved = artisanRepository.save(existant);
        kycService.verifierIdentiteDeclaree(saved);
        return saved;
    }

    /** Bascule la disponibilite de l'artisan CONNECTE (id resolu depuis le JWT, pas depuis l'URL). */
    @PatchMapping("/disponibilite")
    @PreAuthorize("hasRole('ARTISAN')")
    public Artisan basculerDisponibilite(@RequestParam DisponibiliteStatus statut) {
        Artisan existant = getProfil(currentUserProvider.getCurrentUserId());
        existant.setDisponibilite(statut);
        return artisanRepository.save(existant);
    }

    /** Recherche d'artisans (client) : corps de metier et zone facultatifs, classes par score. */
    @GetMapping("/recherche")
    public List<Artisan> rechercher(@RequestParam(required = false) String corpsDeMetier,
                                    @RequestParam(required = false) String zoneIntervention,
                                    @RequestParam(defaultValue = "false") boolean disponibleSeulement) {
        return artisanRepository.rechercher(
                corpsDeMetier == null ? "" : corpsDeMetier.trim(),
                zoneIntervention == null ? "" : zoneIntervention.trim(),
                disponibleSeulement);
    }
}
