package com.gidajari.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** SMS ecrits dans les logs uniquement - developpement et demonstrations. */
@Component
@ConditionalOnProperty(name = "gida-jari.sms-provider.mode", havingValue = "simulation", matchIfMissing = true)
@Slf4j
public class SimulationSmsGateway implements SmsGateway {

    @Override
    public boolean envoyer(String telephone, String message) {
        log.info(">>> SMS (simulation) vers {} : {}", telephone, message);
        return true;
    }

    @Override
    public boolean estSimulation() {
        return true;
    }
}
