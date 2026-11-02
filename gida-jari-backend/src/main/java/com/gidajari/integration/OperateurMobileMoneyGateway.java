package com.gidajari.integration;

import com.gidajari.entity.enums.OperateurMobileMoney;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Point d'integration des API operateurs reelles (Airtel Money, Moov Money,
 * Zamani Cash, Nita, Amana). Chaque operateur a son propre contrat d'API :
 * implementer ici l'appel HTTP correspondant (une methode par operateur).
 *
 * En attendant les acces partenaires, la collecte est enregistree comme
 * "en attente" : la transaction reste PENDING_PAYMENT jusqu'au callback
 * operateur sur POST /api/public/paiements/callback.
 */
@Component
@ConditionalOnProperty(name = "gida-jari.mobile-money.mode", havingValue = "operateur")
@Slf4j
public class OperateurMobileMoneyGateway implements MobileMoneyGateway {

    @Override
    public Resultat initierCollecte(OperateurMobileMoney operateur, String numero, long montantFcfa, String libelle) {
        String ref = operateur.name() + "-" + UUID.randomUUID();
        log.warn("Collecte {} FCFA via {} : API operateur a brancher (ref {}), en attente du callback",
                montantFcfa, operateur, ref);
        return new Resultat(ref, false);
    }

    @Override
    public Resultat verser(OperateurMobileMoney operateur, String numero, long montantFcfa, String libelle) {
        String ref = operateur.name() + "-" + UUID.randomUUID();
        log.warn("Versement {} FCFA via {} vers {} : API operateur a brancher (ref {})",
                montantFcfa, operateur, numero, ref);
        return new Resultat(ref, false);
    }
}
