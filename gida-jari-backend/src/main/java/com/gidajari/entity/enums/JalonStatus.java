package com.gidajari.entity.enums;

/**
 * Etat d'un jalon de chantier. L'auto-validation a 48h (tache @Scheduled)
 * fait transiter SOUMIS -> VALIDE si le client ne se prononce pas.
 */
public enum JalonStatus {
    A_FAIRE,
    SOUMIS,
    VALIDE,
    REJETE,
    LITIGE
}
