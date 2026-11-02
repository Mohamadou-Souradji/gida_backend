package com.gidajari.service;

import com.gidajari.dto.Requetes.JalonRequest;
import com.gidajari.dto.Requetes.ProjetRequest;
import com.gidajari.dto.Vues.ProjetVue;
import com.gidajari.entity.Jalon;
import com.gidajari.entity.Projet;
import com.gidajari.entity.User;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.repository.JalonRepository;
import com.gidajari.repository.ProjetRepository;
import com.gidajari.service.exception.AccesRefuseException;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Service Projet - creation, publication de chantiers et definition des jalons (profil Client). */
@Service
@RequiredArgsConstructor
public class ProjetService {

    private final ProjetRepository projetRepository;
    private final JalonRepository jalonRepository;
    private final KYCService kycService;
    private final EscrowService escrowService;

    @Transactional
    public Projet creer(User client, ProjetRequest req) {
        Projet p = new Projet();
        p.setClient(client);
        p.setTitre(req.titre());
        p.setDescription(req.description());
        p.setTypeProjet(req.typeProjet());
        p.setLocalisation(req.localisation());
        p.setBudgetEstimeFcfa(req.budgetEstimeFcfa());
        p.setPublie(false);
        Projet saved = projetRepository.save(p);
        if (req.jalons() != null && !req.jalons().isEmpty()) {
            ajouterJalons(saved, req.jalons());
        }
        return saved;
    }

    /**
     * Publier un projet le rend visible aux artisans/entreprises (mise en
     * relation) : regle de gestion n4, niveau KYC minimum requis.
     */
    @Transactional
    public Projet publier(String projetId, User client) {
        Projet p = getProprietaire(projetId, client);
        kycService.exigerNiveau(client, KycLevel.NIVEAU_2_IDENTITE_DECLAREE);
        p.setPublie(true);
        return projetRepository.save(p);
    }

    /** Definition des jalons techniques (impossible une fois un prestataire engage). */
    @Transactional
    public List<Jalon> ajouterJalons(String projetId, User client, List<JalonRequest> jalons) {
        Projet projet = getProprietaire(projetId, client);
        if (escrowService.dernierSequestre(projetId).isPresent()) {
            throw new IllegalStateException("Les jalons ne peuvent plus être modifiés : un prestataire est engagé");
        }
        return ajouterJalons(projet, jalons);
    }

    @Transactional
    public void supprimerJalon(String jalonId, User client) {
        Jalon jalon = jalonRepository.findById(jalonId)
                .orElseThrow(() -> new RessourceIntrouvableException("Jalon introuvable: " + jalonId));
        Projet projet = getProprietaire(jalon.idDuProjet(), client);
        if (escrowService.dernierSequestre(projet.getId()).isPresent()) {
            throw new IllegalStateException("Les jalons ne peuvent plus être modifiés : un prestataire est engagé");
        }
        projet.getJalons().remove(jalon);
        jalonRepository.delete(jalon);
    }

    private List<Jalon> ajouterJalons(Projet projet, List<JalonRequest> requetes) {
        int ordre = jalonRepository.findByProjetIdOrderByOrdreAsc(projet.getId()).size();
        List<Jalon> jalons = requetes.stream().map(r -> {
            Jalon j = new Jalon();
            j.setProjet(projet);
            j.setLibelle(r.libelle());
            j.setMontantFcfa(r.montantFcfa());
            return j;
        }).toList();
        for (Jalon j : jalons) {
            j.setOrdre(++ordre);
            projet.getJalons().add(j);
        }
        return jalonRepository.saveAll(jalons);
    }

    public List<ProjetVue> mesProjets(String clientId) {
        return projetRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(p -> new ProjetVue(p, escrowService.dernierSequestre(p.getId()).orElse(null)))
                .toList();
    }

    /** Projets publies et encore sans prestataire engage (vitrine pour artisans, entreprises, experts). */
    public List<Projet> projetsOuverts() {
        return projetRepository.findByPublieTrueOrderByCreatedAtDesc().stream()
                .filter(p -> escrowService.sequestreActif(p.getId()).isEmpty()
                        && escrowService.dernierSequestre(p.getId()).isEmpty())
                .toList();
    }

    /** Detail d'un projet : visible par son client, son prestataire engage, ou par tous s'il est publie. */
    public ProjetVue vue(String projetId, User lecteur) {
        Projet p = getProjet(projetId);
        var escrow = escrowService.dernierSequestre(projetId).orElse(null);
        boolean estClient = p.getClient().getId().equals(lecteur.getId());
        boolean estPrestataire = escrow != null && escrow.getBeneficiaire().getId().equals(lecteur.getId());
        if (!p.isPublie() && !estClient && !estPrestataire) {
            throw new AccesRefuseException("Projet non publié");
        }
        return new ProjetVue(p, escrow);
    }

    public Projet getProjet(String projetId) {
        return projetRepository.findById(projetId)
                .orElseThrow(() -> new RessourceIntrouvableException("Projet introuvable: " + projetId));
    }

    private Projet getProprietaire(String projetId, User client) {
        Projet p = getProjet(projetId);
        if (!p.getClient().getId().equals(client.getId())) {
            throw new AccesRefuseException("Ce projet appartient a un autre client");
        }
        return p;
    }
}
