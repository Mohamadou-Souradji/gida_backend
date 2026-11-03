package com.gidajari.integration;

/**
 * Envoi d'emails (code de connexion). Implementation choisie par gida-jari.email.mode :
 * "simulation" (logs, par defaut) ou "smtp" (n'importe quel serveur SMTP : Gmail, Brevo, OVH...).
 */
public interface EmailGateway {

    /** @return true si le serveur a accepte le message. */
    boolean envoyer(String destinataire, String sujet, String texte, String html);

    /** true si aucun email reel n'est envoye (le code OTP est alors affiche en console). */
    boolean estSimulation();
}
