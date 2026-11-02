package com.gidajari.entity.enums;

/**
 * Roles de l'equipe d'administration (back-office).
 *
 * SUPER_ADMIN           : tout, y compris la gestion de l'equipe.
 * OPERATEUR_KYC         : validation / refus des documents KYC.
 * GESTIONNAIRE_FINANCES : arbitrage des litiges, suivi du sequestre et des retraits.
 * LECTEUR               : consultation seule (tableau de bord, projets, transactions, journal).
 *
 * Tous les roles voient le tableau de bord, les projets, les transactions et le journal.
 */
public enum AdminRole {
    SUPER_ADMIN,
    OPERATEUR_KYC,
    GESTIONNAIRE_FINANCES,
    LECTEUR
}
