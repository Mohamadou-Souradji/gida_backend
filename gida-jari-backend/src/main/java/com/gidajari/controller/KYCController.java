package com.gidajari.controller;

import com.gidajari.dto.Requetes.KycSubmissionRequest;
import com.gidajari.entity.KYCSubmission;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.KYCService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * KYC progressif (regle de gestion n4) - commun aux 6 profils.
 * La validation des documents se fait par un administrateur : voir AdminController.
 */
@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KYCController {

    private final KYCService kycService;
    private final CurrentUserProvider currentUserProvider;

    /** Soumet un document KYC pour l'utilisateur CONNECTE. */
    @PostMapping("/soumissions")
    public KYCSubmission soumettre(@Valid @RequestBody KycSubmissionRequest req) {
        KYCSubmission submission = new KYCSubmission();
        submission.setUser(currentUserProvider.getCurrentUser());
        submission.setNiveauVise(req.niveauVise());
        submission.setTypeDocument(req.typeDocument());
        submission.setDocumentUrl(req.documentUrl());
        return kycService.soumettre(submission);
    }

    /** Historique des soumissions KYC de l'utilisateur CONNECTE. */
    @GetMapping("/mes-soumissions")
    public List<KYCSubmission> mesSoumissions() {
        return kycService.mesSoumissions(currentUserProvider.getCurrentUserId());
    }
}
