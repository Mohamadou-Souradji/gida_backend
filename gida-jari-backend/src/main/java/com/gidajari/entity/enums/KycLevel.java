package com.gidajari.entity.enums;

/**
 * KYC progressif - 7 niveaux de verification.
 * Voir Architecture Globale V3, section 5.1.
 */
public enum KycLevel {
    NIVEAU_0_NON_VERIFIE,
    NIVEAU_1_TELEPHONE_VERIFIE,
    NIVEAU_2_IDENTITE_DECLAREE,
    NIVEAU_3_PIECE_IDENTITE,
    NIVEAU_4_JUSTIFICATIF_DOMICILE,
    NIVEAU_5_DOCUMENTS_PROFESSIONNELS,
    NIVEAU_6_VERIFICATION_TERRAIN
}
