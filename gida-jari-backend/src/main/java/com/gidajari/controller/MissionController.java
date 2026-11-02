package com.gidajari.controller;

import com.gidajari.dto.JalonPreuveDto;
import com.gidajari.dto.Vues.ProjetVue;
import com.gidajari.entity.Jalon;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.EscrowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Missions des prestataires (artisans et entreprises BTP) : chantiers sur
 * lesquels ils sont engages, et envoi de preuve photo geolocalisee GPS
 * pour la validation d'un jalon (roadmap 50% profil Artisan).
 */
@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ARTISAN', 'ENTREPRISE_BTP')")
public class MissionController {

    private final EscrowService escrowService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<ProjetVue> mesMissions() {
        return escrowService.missionsDe(currentUserProvider.getCurrentUserId()).stream()
                .map(t -> new ProjetVue(t.getProjet(), t))
                .toList();
    }

    @PostMapping("/jalons/preuve")
    public Jalon soumettrePreuveJalon(@Valid @RequestBody JalonPreuveDto dto) {
        return escrowService.soumettrePreuve(dto.jalonId(), currentUserProvider.getCurrentUser(),
                dto.photoUrl(), dto.latitude(), dto.longitude());
    }
}
