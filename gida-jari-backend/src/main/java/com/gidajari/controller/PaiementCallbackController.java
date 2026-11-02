package com.gidajari.controller;

import com.gidajari.dto.Requetes.CallbackPaiementRequest;
import com.gidajari.service.EscrowService;
import com.gidajari.service.exception.AccesRefuseException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Webhook des operateurs Mobile Money : confirme (ou non) la collecte vers le
 * compte de sequestre. Protege par un secret partage (en-tete X-GJ-Signature).
 */
@RestController
@RequestMapping("/api/public/paiements")
@RequiredArgsConstructor
public class PaiementCallbackController {

    private final EscrowService escrowService;

    @Value("${gida-jari.mobile-money.webhook-secret}")
    private String secret;

    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(@RequestHeader(value = "X-GJ-Signature", required = false) String signature,
                                                        @Valid @RequestBody CallbackPaiementRequest req) {
        if (signature == null || !MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new AccesRefuseException("Signature de callback invalide");
        }
        var t = escrowService.confirmerPaiementOperateur(req.reference(), req.succes());
        return ResponseEntity.ok(Map.of("transactionId", t.getId(), "statut", t.getStatut().name()));
    }
}
