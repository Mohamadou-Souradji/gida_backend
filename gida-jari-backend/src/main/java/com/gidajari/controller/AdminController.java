package com.gidajari.controller;

import com.gidajari.dto.AdminVues.*;
import com.gidajari.dto.Requetes.KycDecisionRequest;
import com.gidajari.dto.Requetes.ResolutionLitigeRequest;
import com.gidajari.entity.AdminActionLog;
import com.gidajari.entity.EscrowTransaction;
import com.gidajari.entity.KYCSubmission;
import com.gidajari.entity.enums.EscrowStatus;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.AdminJournalService;
import com.gidajari.service.AdminPilotageService;
import com.gidajari.service.EscrowService;
import com.gidajari.service.KYCService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Back-office Gida Jari. Tous les membres de l'equipe (ROLE_ADMIN) consultent ;
 * les decisions sont reservees a certains roles :
 *  - KYC : SUPER_ADMIN, OPERATEUR_KYC
 *  - litiges et export financier : SUPER_ADMIN, GESTIONNAIRE_FINANCES
 *  - equipe : SUPER_ADMIN (voir AdminEquipeController)
 * Chaque decision est tracee dans AdminActionLog (entite P3).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    static final String KYC = "hasAnyRole('ADMIN_SUPER_ADMIN','ADMIN_OPERATEUR_KYC')";
    static final String FINANCES = "hasAnyRole('ADMIN_SUPER_ADMIN','ADMIN_GESTIONNAIRE_FINANCES')";

    private static final DateTimeFormatter DATE_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final KYCService kycService;
    private final EscrowService escrowService;
    private final AdminJournalService journal;
    private final AdminPilotageService pilotage;
    private final CurrentUserProvider currentUserProvider;

    /** Role de la session et actions permises (le frontend adapte ses menus). */
    @GetMapping("/moi")
    public MoiAdmin moi() {
        String role = currentUserProvider.roleAdmin();
        List<String> permissions = new ArrayList<>();
        if ("SUPER_ADMIN".equals(role) || "OPERATEUR_KYC".equals(role)) {
            permissions.add("KYC_DECIDER");
        }
        if ("SUPER_ADMIN".equals(role) || "GESTIONNAIRE_FINANCES".equals(role)) {
            permissions.add("LITIGES_TRANCHER");
            permissions.add("FINANCES_EXPORTER");
        }
        if ("SUPER_ADMIN".equals(role)) {
            permissions.add("EQUIPE_GERER");
        }
        return new MoiAdmin(role, permissions);
    }

    // ---------------------------------------------------------------- pilotage (lecture, tous les roles)

    @GetMapping("/tableau-de-bord")
    public TableauDeBord tableauDeBord() {
        return pilotage.tableauDeBord();
    }

    @GetMapping("/projets")
    public Page<ProjetLigne> projets(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) EtatProjet etat,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int taille) {
        return pilotage.projets(q, etat, page, taille);
    }

    @GetMapping("/projets/{id}")
    public ProjetDetail projet(@PathVariable String id) {
        return pilotage.projet(id);
    }

    @GetMapping("/transactions")
    public Page<TransactionLigne> transactions(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) EscrowStatus statut,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int taille) {
        return pilotage.transactions(q, statut, page, taille);
    }

    @GetMapping("/transactions/{id}")
    public TransactionDetail transaction(@PathVariable String id) {
        return pilotage.transaction(id);
    }

    /** Export CSV (separateur ";" et BOM UTF-8 pour une ouverture directe dans Excel). */
    @PreAuthorize(FINANCES)
    @GetMapping("/transactions/export")
    public ResponseEntity<byte[]> exporterTransactions(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) EscrowStatus statut) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Date;Projet;Client;Téléphone client;Prestataire;Téléphone prestataire;Montant (FCFA);"
                + "Libéré (FCFA);Commissions (FCFA);Niveau;Statut;Opérateur;Référence\n");
        for (TransactionLigne t : pilotage.toutesTransactions(q, statut)) {
            csv.append(String.join(";",
                    t.createdAt() == null ? "" : DATE_CSV.format(t.createdAt().atZone(java.time.ZoneId.of("Africa/Niamey"))),
                    cellule(t.projetTitre()),
                    cellule(t.payeur() == null ? null : t.payeur().nom()), cellule(t.payeur() == null ? null : t.payeur().phone()),
                    cellule(t.beneficiaire() == null ? null : t.beneficiaire().nom()),
                    cellule(t.beneficiaire() == null ? null : t.beneficiaire().phone()),
                    String.valueOf(t.montantTotalFcfa() == null ? 0 : t.montantTotalFcfa()),
                    String.valueOf(t.montantLibereFcfa() == null ? 0 : t.montantLibereFcfa()),
                    String.valueOf(t.commissionsFcfa()),
                    cellule(t.niveauCommission()), cellule(t.statut()), cellule(t.operateurMobileMoney()),
                    cellule(t.referencePaiement()))).append('\n');
        }
        String nom = "gida-jari-transactions-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String cellule(String v) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        // Neutralise les formules (injection CSV) : =, +, -, @ en debut de cellule
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("[+-]?\\d+")) {
            s = "'" + s;
        }
        return s.contains(";") || s.contains("\"") ? "\"" + s + "\"" : s;
    }

    @GetMapping("/retraits")
    public Page<RetraitLigne> retraits(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int taille) {
        return pilotage.retraits(q, page, taille);
    }

    // ---------------------------------------------------------------- KYC

    @GetMapping("/kyc/en-attente")
    public List<KYCSubmission> kycEnAttente() {
        return kycService.enAttente();
    }

    @PreAuthorize(KYC)
    @PatchMapping("/kyc/{id}/approuver")
    public KYCSubmission approuver(@PathVariable String id) {
        return kycService.approuver(id, currentUserProvider.getCurrentUser());
    }

    @PreAuthorize(KYC)
    @PatchMapping("/kyc/{id}/rejeter")
    public KYCSubmission rejeter(@PathVariable String id, @RequestBody KycDecisionRequest req) {
        String commentaire = req.commentaire() == null || req.commentaire().isBlank()
                ? "Document non conforme" : req.commentaire();
        return kycService.rejeter(id, currentUserProvider.getCurrentUser(), commentaire);
    }

    // ---------------------------------------------------------------- litiges

    @GetMapping("/litiges")
    public List<EscrowTransaction> litiges() {
        return escrowService.litigesOuverts();
    }

    @PreAuthorize(FINANCES)
    @PatchMapping("/litiges/{transactionId}/resoudre")
    public EscrowTransaction resoudre(@PathVariable String transactionId, @Valid @RequestBody ResolutionLitigeRequest req) {
        return escrowService.resoudreLitige(transactionId, req.resolution(), currentUserProvider.getCurrentUser(), req.commentaire());
    }

    // ---------------------------------------------------------------- journal

    @GetMapping("/journal")
    public List<AdminActionLog> journal() {
        return journal.dernieresActions();
    }
}
