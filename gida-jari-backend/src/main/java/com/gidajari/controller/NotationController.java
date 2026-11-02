package com.gidajari.controller;

import com.gidajari.dto.Requetes.NotationRequest;
import com.gidajari.entity.Notation;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.NotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Notation multi-criteres entre client et prestataire apres un chantier (entite Notation, P2). */
@RestController
@RequestMapping("/api/notations")
@RequiredArgsConstructor
public class NotationController {

    private final NotationService notationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public Notation noter(@Valid @RequestBody NotationRequest req) {
        return notationService.noter(currentUserProvider.getCurrentUser(), req);
    }

    @GetMapping("/utilisateur/{userId}")
    public List<Notation> notationsRecues(@PathVariable String userId) {
        return notationService.notationsRecues(userId);
    }
}
