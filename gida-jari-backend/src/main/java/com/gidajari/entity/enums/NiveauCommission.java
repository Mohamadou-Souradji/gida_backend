package com.gidajari.entity.enums;

/**
 * Niveaux de commission plateforme, preleves uniquement a la liberation
 * effective des fonds (commission differee). Voir Cahier des Charges,
 * regle de gestion n2.
 */
public enum NiveauCommission {
    STANDARD, // 2%
    PREMIUM,  // 1.5%
    PRO       // 1%
}
