package com.gidajari.controller;

import com.gidajari.dto.Requetes.MotifRequest;
import com.gidajari.entity.EscrowTransaction;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.EscrowService;
import com.gidajari.service.exception.AccesRefuseException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Sequestre : consultation par les parties et ouverture de litige (client ou prestataire). */
@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
public class EscrowController {

    private final EscrowService escrowService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{id}")
    public EscrowTransaction detail(@PathVariable String id) {
        EscrowTransaction t = escrowService.getOrThrow(id);
        String moi = currentUserProvider.getCurrentUserId();
        if (!t.getPayeur().getId().equals(moi) && !t.getBeneficiaire().getId().equals(moi)
                && !currentUserProvider.estAdmin()) {
            throw new AccesRefuseException("Vous n'êtes pas partie a ce contrat");
        }
        return t;
    }

    @PostMapping("/{id}/litige")
    public EscrowTransaction ouvrirLitige(@PathVariable String id, @Valid @RequestBody MotifRequest req) {
        return escrowService.ouvrirLitige(id, currentUserProvider.getCurrentUser(), req.motif());
    }
}
