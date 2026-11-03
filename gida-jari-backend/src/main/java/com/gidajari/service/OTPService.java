package com.gidajari.service;

import com.gidajari.entity.OTPVerification;
import com.gidajari.entity.enums.OtpChannel;
import com.gidajari.integration.EmailGateway;
import com.gidajari.integration.SmsGateway;
import com.gidajari.repository.OTPVerificationRepository;
import com.gidajari.service.exception.EnvoiImpossibleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service OTP - authentification par numero de telephone (regle n5).
 * Le code part par email (canal par defaut, gida-jari.otp.canal=email) ou par SMS (canal=sms).
 * Le code n'est jamais stocke en clair (hash BCrypt), expire apres quelques
 * minutes, est a usage unique et le nombre d'essais / de demandes est limite.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OTPService {

    private final OTPVerificationRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsGateway smsGateway;
    private final EmailGateway emailGateway;
    private final SecureRandom random = new SecureRandom();

    @Value("${gida-jari.otp.longueur:6}")
    private int longueur;

    @Value("${gida-jari.otp.expiration-minutes:5}")
    private int expirationMinutes;

    @Value("${gida-jari.otp.tentatives-max:5}")
    private int tentativesMax;

    @Value("${gida-jari.otp.demandes-max-par-10-minutes:3}")
    private int demandesMax;

    /** "email" (par defaut) ou "sms". */
    @Value("${gida-jari.otp.canal:email}")
    private String canal = "email";

    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("^[a-z0-9._%+'-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");

    /** true si le code est envoye par email. */
    public boolean parEmail() {
        return !"sms".equalsIgnoreCase(canal.trim());
    }

    /** Met l'email en minuscules et verifie son format. */
    public static String normaliserEmail(String email) {
        String e = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (e.length() > 160 || !EMAIL.matcher(e).matches()) {
            throw new IllegalArgumentException("Adresse email invalide : " + email);
        }
        return e;
    }

    /** a***e@gmail.com : montre ou le code est parti sans exposer l'adresse complete. */
    public static String masquerEmail(String email) {
        if (email == null) {
            return null;
        }
        int arobase = email.indexOf('@');
        if (arobase <= 0) {
            return "***";
        }
        String local = email.substring(0, arobase);
        String debut = local.substring(0, 1);
        String fin = local.length() > 2 ? local.substring(local.length() - 1) : "";
        return debut + "***" + fin + email.substring(arobase);
    }

    /** Normalise un numero : supprime espaces/tirets, ajoute l'indicatif Niger (+227) si absent. */
    public static String normaliserTelephone(String phone) {
        if (phone == null) {
            throw new IllegalArgumentException("Numéro de téléphone requis");
        }
        String p = phone.replaceAll("[\\s.\\-()]", "");
        if (p.startsWith("00")) {
            p = "+" + p.substring(2);
        }
        if (!p.startsWith("+")) {
            p = "+227" + p;
        }
        if (!p.matches("\\+\\d{8,15}")) {
            throw new IllegalArgumentException("Numéro de téléphone invalide : " + phone);
        }
        return p;
    }

    /**
     * Genere un code et l'envoie par email (si email fourni et canal email) ou par SMS.
     * Leve EnvoiImpossibleException (HTTP 503) si le serveur mail refuse : rien n'est enregistre.
     */
    @Transactional
    public void genererEtEnvoyer(String phone, String email) {
        long recentes = otpRepository.countByPhoneAndCreatedAtAfter(phone, Instant.now().minus(10, ChronoUnit.MINUTES));
        if (recentes >= demandesMax) {
            throw new IllegalStateException("Trop de demandes de code. Réessayez dans quelques minutes.");
        }
        boolean viaEmail = parEmail();
        if (viaEmail && email == null) {
            throw new IllegalArgumentException("Adresse email requise pour recevoir le code");
        }

        String code = genererCode();

        OTPVerification otp = new OTPVerification();
        otp.setPhone(phone);
        otp.setEmail(viaEmail ? email : null);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setCanal(viaEmail ? OtpChannel.EMAIL : OtpChannel.SMS);
        otp.setExpireLe(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES));
        otpRepository.save(otp);

        boolean simulation;
        if (viaEmail) {
            boolean envoye = emailGateway.envoyer(email, code + " est votre code Gida Jari",
                    texteEmail(code), htmlEmail(code));
            if (!envoye) {
                throw new EnvoiImpossibleException("L'email n'a pas pu être envoyé. Réessayez dans un instant.");
            }
            simulation = emailGateway.estSimulation();
        } else {
            smsGateway.envoyer(phone, "Votre code Gida Jari : " + code + " (valable " + expirationMinutes + " min)");
            simulation = smsGateway.estSimulation();
        }
        if (simulation) {
            // Mode developpement uniquement : aucun email / SMS reel n'est envoye.
            System.out.println(">>> CODE OTP (dev uniquement) pour " + phone
                    + (viaEmail ? " (email " + email + ")" : "") + " : " + code);
        }
    }

    private String texteEmail(String code) {
        return "Bonjour,\n\nVotre code de connexion Gida Jari est : " + code + "\n\n"
                + "Il est valable " + expirationMinutes + " minutes et ne peut servir qu'une fois.\n"
                + "Ne le communiquez à personne : l'équipe Gida Jari ne vous le demandera jamais.\n\n"
                + "Si vous n'avez pas demandé ce code, ignorez simplement cet email.\n\n"
                + "Gida Jari - La maison de demain";
    }

    private String htmlEmail(String code) {
        return "<!doctype html><html lang=\"fr\"><body style=\"margin:0;background:#F4F6F9;font-family:Arial,Helvetica,sans-serif;color:#1F2937\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:24px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:480px;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #E2E8F0\">"
                + "<tr><td style=\"background:#1A3C6E;padding:20px 28px;color:#ffffff;font-size:20px;font-weight:bold\">Gida Jari"
                + " <span style=\"color:#C8922A;font-size:13px;font-weight:normal\">La maison de demain</span></td></tr>"
                + "<tr><td style=\"padding:28px\">"
                + "<p style=\"margin:0 0 14px;font-size:15px\">Bonjour,</p>"
                + "<p style=\"margin:0 0 18px;font-size:15px\">Voici votre code de connexion :</p>"
                + "<p style=\"margin:0 0 18px;text-align:center\"><span style=\"display:inline-block;padding:14px 22px;background:#EEF2F8;"
                + "border-radius:10px;font-size:30px;letter-spacing:8px;font-weight:bold;color:#1A3C6E\">" + code + "</span></p>"
                + "<p style=\"margin:0 0 8px;font-size:14px;color:#6B7280\">Il est valable " + expirationMinutes
                + " minutes et ne peut servir qu'une fois. Ne le communiquez à personne : l'équipe Gida Jari ne vous le demandera jamais.</p>"
                + "<p style=\"margin:0;font-size:13px;color:#6B7280\">Si vous n'avez pas demandé ce code, ignorez cet email.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    /**
     * Verifie le code ; incremente le compteur d'essais et bloque apres tentatives-max echecs.
     * @return la demande validee (avec l'email destinataire), ou vide si le code est faux / expire.
     */
    @Transactional(noRollbackFor = IllegalStateException.class)
    public Optional<OTPVerification> verifier(String phone, String code) {
        OTPVerification otp = otpRepository.findTopByPhoneAndUtiliseFalseOrderByCreatedAtDesc(phone).orElse(null);
        if (otp == null || otp.getExpireLe().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (otp.getTentatives() >= tentativesMax) {
            throw new IllegalStateException("Trop d'essais : demandez un nouveau code.");
        }
        if (code == null || !passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setTentatives(otp.getTentatives() + 1);
            otpRepository.save(otp);
            return Optional.empty();
        }
        otp.setUtilise(true);
        otpRepository.save(otp);
        return Optional.of(otp);
    }

    private String genererCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < longueur; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
