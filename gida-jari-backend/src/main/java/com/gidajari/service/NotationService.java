package com.gidajari.service;

import com.gidajari.dto.Requetes.NotationRequest;
import com.gidajari.entity.EscrowTransaction;
import com.gidajari.entity.Notation;
import com.gidajari.entity.User;
import com.gidajari.entity.enums.EscrowStatus;
import com.gidajari.repository.NotationRepository;
import com.gidajari.repository.UserRepository;
import com.gidajari.service.exception.AccesRefuseException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

/**
 * Notation multi-criteres - 5 criteres ponderes (Architecture Globale V3, section 5.2).
 * Une notation n'est possible qu'entre les deux parties d'un contrat termine
 * (sequestre COMPLETED ou litige tranche), une seule fois par projet et par sens.
 * Le score de profil (profileScore) est la moyenne des scores recus.
 */
@Service
@RequiredArgsConstructor
public class NotationService {

    private final NotationRepository notationRepository;
    private final UserRepository userRepository;
    private final EscrowService escrowService;

    @Value("${gida-jari.notation.poids.qualite:0.30}")
    private double poidsQualite;
    @Value("${gida-jari.notation.poids.delai:0.20}")
    private double poidsDelai;
    @Value("${gida-jari.notation.poids.communication:0.15}")
    private double poidsCommunication;
    @Value("${gida-jari.notation.poids.respect-budget:0.20}")
    private double poidsBudget;
    @Value("${gida-jari.notation.poids.proprete:0.15}")
    private double poidsProprete;

    @Transactional
    public Notation noter(User auteur, NotationRequest req) {
        EscrowTransaction t = escrowService.dernierSequestre(req.projetId())
                .orElseThrow(() -> new IllegalStateException("Aucun contrat sur ce projet"));
        if (!EnumSet.of(EscrowStatus.COMPLETED, EscrowStatus.RESOLVED_CLIENT,
                EscrowStatus.RESOLVED_PROVIDER, EscrowStatus.REFUNDED).contains(t.getStatut())) {
            throw new IllegalStateException("La notation est ouverte une fois le chantier terminé");
        }
        String payeurId = t.getPayeur().getId();
        String beneficiaireId = t.getBeneficiaire().getId();
        boolean sensValide = (auteur.getId().equals(payeurId) && req.notePourId().equals(beneficiaireId))
                || (auteur.getId().equals(beneficiaireId) && req.notePourId().equals(payeurId));
        if (!sensValide) {
            throw new AccesRefuseException("Seules les parties du contrat peuvent se noter mutuellement");
        }
        if (notationRepository.existsByProjetIdAndNoteParIdAndNotePourId(req.projetId(), auteur.getId(), req.notePourId())) {
            throw new IllegalStateException("Vous avez déjà noté cet utilisateur pour ce projet");
        }

        User notePour = userRepository.findById(req.notePourId()).orElseThrow();
        Notation n = new Notation();
        n.setProjet(t.getProjet());
        n.setNotePar(auteur);
        n.setNotePour(notePour);
        n.setQualite(req.qualite());
        n.setDelai(req.delai());
        n.setCommunication(req.communication());
        n.setRespectBudget(req.respectBudget());
        n.setProprete(req.proprete());
        n.setCommentaire(req.commentaire());
        n.setScoreGlobal(calculerScore(req.qualite(), req.delai(), req.communication(), req.respectBudget(), req.proprete()));
        Notation saved = notationRepository.save(n);

        List<Notation> recues = notationRepository.findByNotePourIdOrderByCreatedAtDesc(notePour.getId());
        double moyenne = recues.stream().mapToDouble(Notation::getScoreGlobal).average().orElse(0);
        notePour.setProfileScore(Math.round(moyenne * 100) / 100.0);
        userRepository.save(notePour);
        return saved;
    }

    /** Score pondere sur 5. */
    public double calculerScore(int qualite, int delai, int communication, int budget, int proprete) {
        double somme = poidsQualite + poidsDelai + poidsCommunication + poidsBudget + poidsProprete;
        double score = (qualite * poidsQualite + delai * poidsDelai + communication * poidsCommunication
                + budget * poidsBudget + proprete * poidsProprete) / somme;
        return Math.round(score * 100) / 100.0;
    }

    public List<Notation> notationsRecues(String userId) {
        return notationRepository.findByNotePourIdOrderByCreatedAtDesc(userId);
    }
}
