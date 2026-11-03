package com.gidajari.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Emails ecrits dans les logs uniquement : developpement, demonstrations, tant que le SMTP n'est pas configure. */
@Component
@ConditionalOnProperty(name = "gida-jari.email.mode", havingValue = "simulation", matchIfMissing = true)
@Slf4j
public class SimulationEmailGateway implements EmailGateway {

    @Override
    public boolean envoyer(String destinataire, String sujet, String texte, String html) {
        log.info(">>> EMAIL (simulation) vers {} : {}", destinataire, sujet);
        return true;
    }

    @Override
    public boolean estSimulation() {
        return true;
    }
}
