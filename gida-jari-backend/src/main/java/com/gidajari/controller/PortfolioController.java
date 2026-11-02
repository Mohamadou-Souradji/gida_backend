package com.gidajari.controller;

import com.gidajari.dto.Requetes.PortfolioRequest;
import com.gidajari.entity.PortfolioItem;
import com.gidajari.repository.PortfolioItemRepository;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.exception.RessourceIntrouvableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Portfolio des realisations (entite PortfolioItem, P1 - agregation User o-- PortfolioItem). */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioItemRepository portfolioRepository;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<PortfolioItem> monPortfolio() {
        return portfolioRepository.findByProprietaireIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId());
    }

    @GetMapping("/utilisateur/{userId}")
    public List<PortfolioItem> portfolioDe(@PathVariable String userId) {
        return portfolioRepository.findByProprietaireIdOrderByCreatedAtDesc(userId);
    }

    @PostMapping
    public PortfolioItem ajouter(@Valid @RequestBody PortfolioRequest req) {
        PortfolioItem item = new PortfolioItem();
        item.setProprietaire(currentUserProvider.getCurrentUser());
        item.setTitre(req.titre());
        item.setDescription(req.description());
        item.setPhotoUrl(req.photoUrl());
        return portfolioRepository.save(item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        PortfolioItem item = portfolioRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Element introuvable"));
        currentUserProvider.exigerProprietaire(item.getProprietaire().getId());
        portfolioRepository.delete(item);
        return ResponseEntity.noContent().build();
    }
}
