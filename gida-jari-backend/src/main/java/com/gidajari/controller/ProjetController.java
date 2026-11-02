package com.gidajari.controller;

import com.gidajari.dto.Vues.ProjetVue;
import com.gidajari.entity.Projet;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.ProjetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Consultation des projets publies (artisans, entreprises BTP, experts) et detail d'un projet. */
@RestController
@RequestMapping("/api/projets")
@RequiredArgsConstructor
public class ProjetController {

    private final ProjetService projetService;
    private final CurrentUserProvider currentUserProvider;

    /** Projets publies sans prestataire engage. */
    @GetMapping("/ouverts")
    public List<Projet> projetsOuverts() {
        return projetService.projetsOuverts();
    }

    @GetMapping("/{id}")
    public ProjetVue detail(@PathVariable String id) {
        return projetService.vue(id, currentUserProvider.getCurrentUser());
    }
}
