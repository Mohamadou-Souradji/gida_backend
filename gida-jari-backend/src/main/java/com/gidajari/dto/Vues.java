package com.gidajari.dto;

import com.gidajari.entity.EscrowTransaction;
import com.gidajari.entity.Projet;

/** Reponses composees de l'API (en plus des entites serialisees directement). */
public final class Vues {

    private Vues() {
    }

    /** Projet + son sequestre actif/dernier (null si aucun prestataire n'est encore engage). */
    public record ProjetVue(Projet projet, EscrowTransaction escrow) {}

    /** Informations du compte connecte (dont le solde, jamais expose publiquement). */
    public record CompteVue(String id, String phone, String profileType, String kycLevel,
                            Long walletBalance, Double profileScore, String languePreferee,
                            boolean admin, String adminRole, long notificationsNonLues) {}
}
