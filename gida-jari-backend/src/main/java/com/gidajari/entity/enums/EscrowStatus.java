package com.gidajari.entity.enums;

/**
 * Machine a etats de l'escrow (sequestre).
 * Voir Cahier des Charges Technique, section 1.3 - Diagramme d'etats.
 *
 * DRAFT -> PENDING_PAYMENT -> FUNDED -> PARTIAL_RELEASE -> COMPLETED
 *                                          |
 *                                          v
 *                                      DISPUTED -> RESOLVED_CLIENT
 *                                               -> RESOLVED_PROVIDER
 *                                               -> REFUNDED
 */
public enum EscrowStatus {
    DRAFT,
    PENDING_PAYMENT,
    FUNDED,
    PARTIAL_RELEASE,
    COMPLETED,
    DISPUTED,
    RESOLVED_CLIENT,
    RESOLVED_PROVIDER,
    REFUNDED
}
