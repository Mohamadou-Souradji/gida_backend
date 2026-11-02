package com.gidajari.service.exception;

import com.gidajari.entity.enums.KycLevel;
import lombok.Getter;

/**
 * Leve quand une action necessitant un niveau KYC minimum (mise en relation,
 * paiement - regle de gestion n4) est tentee par un utilisateur dont le
 * niveau KYC est insuffisant.
 */
@Getter
public class KycInsuffisantException extends RuntimeException {

    private final KycLevel niveauActuel;
    private final KycLevel niveauRequis;

    public KycInsuffisantException(KycLevel niveauActuel, KycLevel niveauRequis) {
        super("Niveau KYC insuffisant : requis " + niveauRequis + ", actuel " + niveauActuel);
        this.niveauActuel = niveauActuel;
        this.niveauRequis = niveauRequis;
    }
}
