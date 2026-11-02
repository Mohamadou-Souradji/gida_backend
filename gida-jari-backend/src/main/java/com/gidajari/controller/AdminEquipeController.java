package com.gidajari.controller;

import com.gidajari.dto.AdminVues.MembreEquipe;
import com.gidajari.dto.Requetes.MembreEquipeModificationRequest;
import com.gidajari.dto.Requetes.MembreEquipeRequest;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.AdminEquipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Equipe d'administration : consultation par tous les admins, gestion par les super-admins.
 * Un membre ajoute se connecte avec son numero (OTP) ; un changement de role s'applique
 * a sa requete suivante.
 */
@RestController
@RequestMapping("/api/admin/equipe")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminEquipeController {

    private static final String SUPER = "hasRole('ADMIN_SUPER_ADMIN')";

    private final AdminEquipeService equipe;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<MembreEquipe> lister() {
        return equipe.lister();
    }

    @PreAuthorize(SUPER)
    @PostMapping
    public List<MembreEquipe> ajouter(@Valid @RequestBody MembreEquipeRequest req) {
        equipe.ajouter(req.phone(), req.nom(), req.role(), currentUserProvider.getCurrentUser());
        return equipe.lister();
    }

    @PreAuthorize(SUPER)
    @PatchMapping("/{id}")
    public List<MembreEquipe> modifier(@PathVariable String id, @Valid @RequestBody MembreEquipeModificationRequest req) {
        equipe.modifier(id, req.role(), req.actif(), req.nom(), currentUserProvider.getCurrentUser());
        return equipe.lister();
    }

    @PreAuthorize(SUPER)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retirer(@PathVariable String id) {
        equipe.retirer(id, currentUserProvider.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
