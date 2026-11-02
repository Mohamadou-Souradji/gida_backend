package com.gidajari.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Agregateur SMS HTTP generique (Niger) : POST {base-url} avec
 * {"to": "+227...", "message": "..."} et Authorization: Bearer {api-key}.
 * Adapter le corps de la requete au contrat exact du fournisseur retenu.
 */
@Component
@ConditionalOnProperty(name = "gida-jari.sms-provider.mode", havingValue = "http")
@Slf4j
public class HttpSmsGateway implements SmsGateway {

    private final RestClient client;

    public HttpSmsGateway(@Value("${gida-jari.sms-provider.base-url}") String baseUrl,
                          @Value("${gida-jari.sms-provider.api-key}") String apiKey) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public boolean envoyer(String telephone, String message) {
        try {
            client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("to", telephone, "message", message))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("Echec d'envoi SMS vers {} : {}", telephone, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean estSimulation() {
        return false;
    }
}
