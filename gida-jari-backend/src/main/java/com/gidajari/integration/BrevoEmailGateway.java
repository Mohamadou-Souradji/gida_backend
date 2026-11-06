package com.gidajari.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envoi d'emails via l'API HTTP de Brevo (port 443). Utile quand l'hebergeur bloque
 * les ports SMTP (25, 465, 587), comme Render sur le plan gratuit.
 * Activation : gida-jari.email.mode=brevo, cle API dans GJ_BREVO_API_KEY.
 * L'adresse d'expedition doit etre un expediteur valide dans le compte Brevo.
 */
@Component
@ConditionalOnProperty(name = "gida-jari.email.mode", havingValue = "brevo")
@Slf4j
public class BrevoEmailGateway implements EmailGateway {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient client;
    private final String expediteur;
    private final String nomExpediteur;

    public BrevoEmailGateway(@Value("${GJ_BREVO_API_KEY:}") String apiKey,
                             @Value("${gida-jari.email.expediteur}") String expediteur,
                             @Value("${gida-jari.email.nom-expediteur:Gida Jari}") String nomExpediteur) {
        this.client = RestClient.builder().baseUrl(URL).defaultHeader("api-key", apiKey).build();
        this.expediteur = expediteur;
        this.nomExpediteur = nomExpediteur;
        log.info("Envoi d'emails par API Brevo, expediteur {}", expediteur);
    }

    @Override
    public boolean envoyer(String destinataire, String sujet, String texte, String html) {
        try {
            Map<String, Object> corps = new HashMap<>();
            corps.put("sender", Map.of("name", nomExpediteur, "email", expediteur));
            corps.put("to", List.of(Map.of("email", destinataire)));
            corps.put("subject", sujet);
            corps.put("textContent", texte);
            if (html != null) {
                corps.put("htmlContent", html);
            }
            client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("Echec d'envoi de l'email vers {} via l'API Brevo : {}", destinataire, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean estSimulation() {
        return false;
    }
}
