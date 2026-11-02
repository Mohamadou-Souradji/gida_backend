package com.gidajari.controller;

import com.gidajari.dto.Requetes.ProduitRequest;
import com.gidajari.entity.CatalogProduit;
import com.gidajari.entity.FournisseurMateriaux;
import com.gidajari.repository.CatalogProduitRepository;
import com.gidajari.repository.FournisseurMateriauxRepository;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.KYCService;
import com.gidajari.service.exception.RessourceIntrouvableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Profil 6 - Fournisseur de materiaux de construction.
 * Roadmap 50% :
 *  - Configuration de la boutique et parametrage des zones/frais de livraison
 *  - Interface CRUD d'administration du catalogue de produits (articles, prix, stocks)
 */
@RestController
@RequestMapping("/api/fournisseurs")
@RequiredArgsConstructor
public class SupplierController {

    private final FournisseurMateriauxRepository fournisseurRepository;
    private final CatalogProduitRepository catalogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final KYCService kycService;

    @GetMapping("/{id}")
    public FournisseurMateriaux getProfil(@PathVariable String id) {
        return fournisseurRepository.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Fournisseur introuvable"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FOURNISSEUR_MATERIAUX')")
    @Transactional
    public FournisseurMateriaux mettreAJourProfil(@PathVariable String id, @RequestBody FournisseurMateriaux maj) {
        currentUserProvider.exigerProprietaire(id);
        FournisseurMateriaux existant = getProfil(id);
        existant.setNomBoutique(maj.getNomBoutique());
        existant.setNif(maj.getNif());
        existant.setCategories(maj.getCategories());
        existant.setZonesLivraison(maj.getZonesLivraison());
        existant.setFraisLivraisonFcfa(maj.getFraisLivraisonFcfa());
        FournisseurMateriaux saved = fournisseurRepository.save(existant);
        kycService.verifierIdentiteDeclaree(saved);
        return saved;
    }

    /** Catalogue d'un fournisseur donne (consultation par les clients/entreprises). */
    @GetMapping("/{id}/catalogue")
    public List<CatalogProduit> listerCatalogue(@PathVariable String id) {
        return catalogRepository.findByFournisseurId(id);
    }

    /** Recherche de produits actifs dans tous les catalogues, du moins cher au plus cher. */
    @GetMapping("/catalogue/recherche")
    public List<CatalogProduit> rechercherProduits(@RequestParam(defaultValue = "") String q) {
        return catalogRepository.findByActifTrueAndNomContainingIgnoreCaseOrderByPrixUnitaireFcfaAsc(q.trim());
    }

    /** Ajoute un produit au catalogue du fournisseur CONNECTE (id resolu depuis le JWT). */
    @PostMapping("/catalogue")
    @PreAuthorize("hasRole('FOURNISSEUR_MATERIAUX')")
    public CatalogProduit ajouterProduit(@Valid @RequestBody ProduitRequest req) {
        FournisseurMateriaux fournisseur = getProfil(currentUserProvider.getCurrentUserId());
        CatalogProduit produit = new CatalogProduit();
        produit.setFournisseur(fournisseur);
        appliquer(produit, req);
        return catalogRepository.save(produit);
    }

    @PutMapping("/catalogue/{produitId}")
    @PreAuthorize("hasRole('FOURNISSEUR_MATERIAUX')")
    public CatalogProduit modifierProduit(@PathVariable String produitId, @Valid @RequestBody ProduitRequest req) {
        CatalogProduit existant = getProduitProprietaire(produitId);
        appliquer(existant, req);
        return catalogRepository.save(existant);
    }

    @DeleteMapping("/catalogue/{produitId}")
    @PreAuthorize("hasRole('FOURNISSEUR_MATERIAUX')")
    public ResponseEntity<Void> supprimerProduit(@PathVariable String produitId) {
        catalogRepository.delete(getProduitProprietaire(produitId));
        return ResponseEntity.noContent().build();
    }

    private CatalogProduit getProduitProprietaire(String produitId) {
        CatalogProduit p = catalogRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable"));
        currentUserProvider.exigerProprietaire(p.getFournisseur().getId());
        return p;
    }

    private static void appliquer(CatalogProduit p, ProduitRequest req) {
        p.setNom(req.nom());
        p.setCategorie(req.categorie());
        p.setUnite(req.unite());
        p.setPrixUnitaireFcfa(req.prixUnitaireFcfa());
        p.setStockDisponible(req.stockDisponible() != null ? req.stockDisponible() : 0);
        p.setActif(req.actif() == null || req.actif());
    }
}
