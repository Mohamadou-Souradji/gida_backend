package com.gidajari.integration;

import com.gidajari.entity.enums.OperateurMobileMoney;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Mobile Money simule : chaque operation est confirmee immediatement. */
@Component
@ConditionalOnProperty(name = "gida-jari.mobile-money.mode", havingValue = "simulation", matchIfMissing = true)
@Slf4j
public class SimulationMobileMoneyGateway implements MobileMoneyGateway {

    @Override
    public Resultat initierCollecte(OperateurMobileMoney operateur, String numero, long montantFcfa, String libelle) {
        String ref = "SIM-COL-" + UUID.randomUUID();
        log.info(">>> Mobile Money (simulation) collecte {} FCFA via {} depuis {} [{}] ref={}",
                montantFcfa, operateur, numero, libelle, ref);
        return new Resultat(ref, true);
    }

    @Override
    public Resultat verser(OperateurMobileMoney operateur, String numero, long montantFcfa, String libelle) {
        String ref = "SIM-VER-" + UUID.randomUUID();
        log.info(">>> Mobile Money (simulation) versement {} FCFA via {} vers {} [{}] ref={}",
                montantFcfa, operateur, numero, libelle, ref);
        return new Resultat(ref, true);
    }
}
