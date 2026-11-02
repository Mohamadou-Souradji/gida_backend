package com.gidajari.integration;

import com.gidajari.entity.enums.OperateurMobileMoney;

/**
 * Passerelle Mobile Money (regle n1 : les fonds transitent exclusivement par le
 * compte de sequestre Gida Jari). Une implementation par mode :
 * "simulation" (confirmation immediate) ou integration operateur reelle.
 */
public interface MobileMoneyGateway {

    /** Resultat d'une demande de collecte ou de versement. */
    record Resultat(String reference, boolean confirme) {}

    /**
     * Debite le numero du client vers le compte de sequestre.
     * confirme = true si l'operateur a deja confirme (sinon attendre le callback).
     */
    Resultat initierCollecte(OperateurMobileMoney operateur, String numero, long montantFcfa, String libelle);

    /** Verse des fonds du compte Gida Jari vers un numero (liberation, remboursement, retrait). */
    Resultat verser(OperateurMobileMoney operateur, String numero, long montantFcfa, String libelle);
}
