package com.gidajari.integration;

/**
 * Envoi de SMS (OTP, notifications). Implementation choisie par
 * gida-jari.sms-provider.mode : "simulation" (logs) ou "http" (agregateur SMS).
 */
public interface SmsGateway {

    /** @return true si le SMS a ete accepte par le fournisseur. */
    boolean envoyer(String telephone, String message);

    /** true si aucun SMS reel n'est envoye (le code OTP peut alors etre affiche en console). */
    boolean estSimulation();
}
