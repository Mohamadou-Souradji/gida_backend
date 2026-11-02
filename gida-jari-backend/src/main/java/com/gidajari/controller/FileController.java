package com.gidajari.controller;

import com.gidajari.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Upload de fichiers generique - utilise pour les photos de jalon
 * (preuve geolocalisee), les documents KYC et les pieces justificatives
 * (NIF, RCCM...). Retourne une URL a stocker dans l'entite concernee.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<Map<String, String>> uploader(@RequestParam("fichier") MultipartFile fichier) {
        if (fichier.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String url = fileStorageService.sauvegarder(fichier);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
