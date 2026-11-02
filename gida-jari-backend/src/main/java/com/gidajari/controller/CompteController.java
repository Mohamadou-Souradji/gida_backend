package com.gidajari.controller;

import com.gidajari.dto.Requetes.LangueRequest;
import com.gidajari.dto.Requetes.RetraitRequest;
import com.gidajari.dto.Vues.CompteVue;
import com.gidajari.entity.NotificationLog;
import com.gidajari.entity.User;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.CompteService;
import com.gidajari.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Compte de l'utilisateur connecte : informations, solde, retrait, langue, notifications. */
@RestController
@RequestMapping("/api/compte")
@RequiredArgsConstructor
public class CompteController {

    private final CurrentUserProvider currentUserProvider;
    private final CompteService compteService;
    private final NotificationService notificationService;

    @GetMapping("/moi")
    public CompteVue moi() {
        return vue(currentUserProvider.getCurrentUser());
    }

    @PatchMapping("/langue")
    public CompteVue changerLangue(@Valid @RequestBody LangueRequest req) {
        return vue(compteService.changerLangue(currentUserProvider.getCurrentUser(), req.langue()));
    }

    /** Retrait du solde Gida Jari vers un numero Mobile Money. */
    @PostMapping("/retrait")
    public CompteVue retirer(@Valid @RequestBody RetraitRequest req) {
        User u = compteService.retirer(currentUserProvider.getCurrentUser(), req.operateur(), req.numero(), req.montantFcfa());
        return vue(u);
    }

    @GetMapping("/notifications")
    public List<NotificationLog> notifications() {
        return notificationService.mesNotifications(currentUserProvider.getCurrentUserId());
    }

    @PostMapping("/notifications/lues")
    public ResponseEntity<Void> marquerLues() {
        notificationService.marquerToutesLues(currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    private CompteVue vue(User u) {
        return new CompteVue(u.getId(), u.getPhone(), u.getProfileType() != null ? u.getProfileType().name() : null,
                u.getKycLevel().name(), u.getWalletBalance(), u.getProfileScore(), u.getLanguePreferee(),
                currentUserProvider.estAdmin(), currentUserProvider.roleAdmin(), notificationService.nombreNonLues(u.getId()));
    }
}
