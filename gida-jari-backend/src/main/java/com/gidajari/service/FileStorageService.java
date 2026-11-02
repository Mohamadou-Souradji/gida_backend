package com.gidajari.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stockage local des fichiers uploades (photos de jalon, documents KYC,
 * pieces justificatives NIF/RCCM, portfolio). Pour la production, remplacer
 * par un stockage objet (S3-compatible, Supabase Storage...) derriere la meme
 * methode - le reste de l'application ne depend que de l'URL retournee.
 *
 * Seules les extensions autorisees (images, PDF) sont acceptees et le nom
 * de fichier est toujours regenere (UUID) : pas d'execution ni d'ecrasement possible.
 */
@Service
public class FileStorageService {

    @Value("${gida-jari.fichiers.dossier-stockage:./uploads}")
    private String dossierStockage;

    @Value("${gida-jari.fichiers.url-publique-base:/files}")
    private String urlPubliqueBase;

    @Value("${gida-jari.fichiers.extensions-autorisees:jpg,jpeg,png,webp,pdf}")
    private String extensionsAutorisees;

    /** Enregistre un fichier uploade et retourne l'URL relative pour le consulter. */
    public String sauvegarder(MultipartFile fichier) {
        String extension = extraireExtension(fichier.getOriginalFilename());
        Set<String> autorisees = Arrays.stream(extensionsAutorisees.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (!autorisees.contains(extension)) {
            throw new IllegalArgumentException("Type de fichier non autorisé (" + extensionsAutorisees + ")");
        }
        try {
            Path dossier = Path.of(dossierStockage).toAbsolutePath().normalize();
            Files.createDirectories(dossier);
            String nomFichier = UUID.randomUUID() + "." + extension;
            Path destination = dossier.resolve(nomFichier).normalize();
            try (InputStream in = fichier.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return urlPubliqueBase + "/" + nomFichier;
        } catch (IOException e) {
            throw new IllegalStateException("Échec de l'enregistrement du fichier", e);
        }
    }

    private String extraireExtension(String nomOriginal) {
        String nom = StringUtils.cleanPath(nomOriginal == null ? "" : nomOriginal);
        int point = nom.lastIndexOf('.');
        return point >= 0 ? nom.substring(point + 1).toLowerCase(Locale.ROOT) : "";
    }
}
