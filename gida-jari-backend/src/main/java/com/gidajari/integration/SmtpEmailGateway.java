package com.gidajari.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Client SMTP minimal, sans dependance externe (JDK uniquement) : STARTTLS (port 587),
 * SSL direct (port 465) ou connexion en clair (serveur interne), authentification AUTH PLAIN,
 * message MIME multipart texte + HTML en UTF-8. Le certificat du serveur et son nom d'hote
 * sont verifies.
 *
 * Fonctionne avec Gmail (mot de passe d'application), Brevo, Mailjet, OVH, Zoho, ou le
 * serveur mail de votre domaine. Configuration : gida-jari.email.smtp.* (variables GJ_SMTP_*).
 */
@Component
@ConditionalOnProperty(name = "gida-jari.email.mode", havingValue = "smtp")
@Slf4j
public class SmtpEmailGateway implements EmailGateway {

    private static final int DELAI_MS = 15_000;

    private final String hote;
    private final int port;
    private final String securite;
    private final String utilisateur;
    private final String motDePasse;
    private final String expediteur;
    private final String nomExpediteur;

    public SmtpEmailGateway(@Value("${gida-jari.email.smtp.hote}") String hote,
                            @Value("${gida-jari.email.smtp.port}") int port,
                            @Value("${gida-jari.email.smtp.securite:starttls}") String securite,
                            @Value("${gida-jari.email.smtp.utilisateur:}") String utilisateur,
                            @Value("${gida-jari.email.smtp.mot-de-passe:}") String motDePasse,
                            @Value("${gida-jari.email.expediteur}") String expediteur,
                            @Value("${gida-jari.email.nom-expediteur:Gida Jari}") String nomExpediteur) {
        this.hote = hote;
        this.port = port;
        this.securite = securite.trim().toLowerCase(Locale.ROOT);
        this.utilisateur = utilisateur;
        this.motDePasse = motDePasse;
        this.expediteur = expediteur;
        this.nomExpediteur = nomExpediteur;
        if (!this.securite.equals("starttls") && !this.securite.equals("ssl") && !this.securite.equals("aucune")) {
            throw new IllegalArgumentException("gida-jari.email.smtp.securite doit valoir starttls, ssl ou aucune");
        }
        log.info("Envoi d'emails par SMTP : {}:{} ({}), expediteur {}", hote, port, this.securite, expediteur);
    }

    @Override
    public boolean envoyer(String destinataire, String sujet, String texte, String html) {
        try {
            transmettre(destinataire, construireMessage(destinataire, sujet, texte, html));
            return true;
        } catch (Exception e) {
            log.error("Echec d'envoi de l'email vers {} via {}:{} : {}", destinataire, hote, port, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean estSimulation() {
        return false;
    }

    // ------------------------------------------------------------------ dialogue SMTP

    private void transmettre(String destinataire, String message) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(hote, port), DELAI_MS);
        socket.setSoTimeout(DELAI_MS);
        if (securite.equals("ssl")) {
            socket = chiffrer(socket);
        }
        try (Socket s = socket) {
            Session session = new Session(s);
            session.attendre(220);
            session.commande("EHLO " + nomLocal(), 250);
            if (securite.equals("starttls")) {
                session.commande("STARTTLS", 220);
                session = new Session(chiffrer(s));
                session.commande("EHLO " + nomLocal(), 250);
            }
            if (utilisateur != null && !utilisateur.isBlank()) {
                String jeton = Base64.getEncoder().encodeToString(
                        ("\0" + utilisateur + "\0" + motDePasse).getBytes(StandardCharsets.UTF_8));
                session.commande("AUTH PLAIN " + jeton, 235);
            }
            session.commande("MAIL FROM:<" + expediteur + ">", 250);
            session.commande("RCPT TO:<" + destinataire + ">", 250, 251);
            session.commande("DATA", 354);
            session.ecrire(message + "\r\n.");
            session.attendre(250);
            try {
                session.commande("QUIT", 221);
            } catch (IOException ignore) {
                // le message est deja accepte
            }
        }
    }

    /** Passe la connexion en TLS en verifiant le certificat ET le nom d'hote du serveur. */
    private SSLSocket chiffrer(Socket brut) throws IOException {
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                .createSocket(brut, hote, port, true);
        SSLParameters parametres = ssl.getSSLParameters();
        parametres.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(parametres);
        ssl.setSoTimeout(DELAI_MS);
        ssl.startHandshake();
        return ssl;
    }

    private String nomLocal() {
        int arobase = expediteur.indexOf('@');
        return arobase > 0 ? expediteur.substring(arobase + 1) : "localhost";
    }

    /** Lecture / ecriture des commandes SMTP (reponses multi-lignes "250-..." gerees). */
    private static final class Session {
        private final BufferedReader lecteur;
        private final Writer ecrivain;

        Session(Socket s) throws IOException {
            this.lecteur = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            this.ecrivain = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.US_ASCII));
        }

        void ecrire(String ligne) throws IOException {
            ecrivain.write(ligne + "\r\n");
            ecrivain.flush();
        }

        void commande(String ligne, int... attendus) throws IOException {
            ecrire(ligne);
            attendre(attendus);
        }

        void attendre(int... attendus) throws IOException {
            String ligne;
            String derniere;
            do {
                ligne = lecteur.readLine();
                if (ligne == null) {
                    throw new IOException("connexion fermee par le serveur SMTP");
                }
                derniere = ligne;
            } while (ligne.length() > 3 && ligne.charAt(3) == '-');
            int code = derniere.length() >= 3 ? Integer.parseInt(derniere.substring(0, 3)) : -1;
            for (int a : attendus) {
                if (a == code) {
                    return;
                }
            }
            // Ne jamais journaliser la commande AUTH (elle contient le mot de passe)
            throw new IOException("reponse SMTP inattendue : " + derniere);
        }
    }

    // ------------------------------------------------------------------ message MIME

    public String construireMessage(String destinataire, String sujet, String texte, String html) {
        String frontiere = "gj-" + UUID.randomUUID();
        StringBuilder m = new StringBuilder();
        m.append("From: ").append(encoderEntete(nomExpediteur)).append(" <").append(expediteur).append(">\r\n");
        m.append("To: <").append(destinataire).append(">\r\n");
        m.append("Subject: ").append(encoderEntete(sujet)).append("\r\n");
        m.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append("\r\n");
        m.append("Message-ID: <").append(UUID.randomUUID()).append('@').append(nomLocal()).append(">\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: multipart/alternative; boundary=\"").append(frontiere).append("\"\r\n\r\n");
        partie(m, frontiere, "text/plain", texte);
        if (html != null) {
            partie(m, frontiere, "text/html", html);
        }
        m.append("--").append(frontiere).append("--");
        return m.toString();
    }

    private static void partie(StringBuilder m, String frontiere, String type, String contenu) {
        m.append("--").append(frontiere).append("\r\n");
        m.append("Content-Type: ").append(type).append("; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        // Base64 decoupe en lignes de 76 caracteres : aucune ligne ne commence par "." (pas de dot-stuffing)
        m.append(Base64.getMimeEncoder().encodeToString(contenu.getBytes(StandardCharsets.UTF_8))).append("\r\n");
    }

    private static String encoderEntete(String valeur) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(valeur.getBytes(StandardCharsets.UTF_8)) + "?=";
    }
}
