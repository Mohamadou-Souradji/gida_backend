package com.gidajari.service;

import com.gidajari.entity.OTPVerification;
import com.gidajari.entity.enums.OtpChannel;
import com.gidajari.integration.SmsGateway;
import com.gidajari.repository.OTPVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service OTP SMS - authentification par numero de telephone (regle n5).
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
    private final SecureRandom random = new SecureRandom();

    @Value("${gida-jari.otp.longueur:6}")
    private int longueur;

    @Value("${gida-jari.otp.expiration-minutes:5}")
    private int expirationMinutes;

    @Value("${gida-jari.otp.tentatives-max:5}")
    private int tentativesMax;

    @Value("${gida-jari.otp.demandes-max-par-10-minutes:3}")
    private int demandesMax;

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

    @Transactional
    public void genererEtEnvoyer(String phone, OtpChannel canal) {
        long recentes = otpRepository.countByPhoneAndCreatedAtAfter(phone, Instant.now().minus(10, ChronoUnit.MINUTES));
        if (recentes >= demandesMax) {
            throw new IllegalStateException("Trop de demandes de code. Réessayez dans quelques minutes.");
        }

        String code = genererCode();

        OTPVerification otp = new OTPVerification();
        otp.setPhone(phone);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setCanal(canal);
        otp.setExpireLe(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES));
        otpRepository.save(otp);

        smsGateway.envoyer(phone, "Votre code Gida Jari : " + code + " (valable " + expirationMinutes + " min)");
        if (smsGateway.estSimulation()) {
            // Mode developpement uniquement : aucun SMS reel n'est envoye.
            System.out.println(">>> CODE OTP (dev uniquement) pour " + phone + " : " + code);
        }
    }

    /** Verifie le code ; incremente le compteur d'essais et bloque apres tentatives-max echecs. */
    @Transactional(noRollbackFor = IllegalStateException.class)
    public boolean verifier(String phone, String code) {
        OTPVerification otp = otpRepository.findTopByPhoneAndUtiliseFalseOrderByCreatedAtDesc(phone).orElse(null);
        if (otp == null || otp.getExpireLe().isBefore(Instant.now())) {
            return false;
        }
        if (otp.getTentatives() >= tentativesMax) {
            throw new IllegalStateException("Trop d'essais : demandez un nouveau code.");
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setTentatives(otp.getTentatives() + 1);
            otpRepository.save(otp);
            return false;
        }
        otp.setUtilise(true);
        otpRepository.save(otp);
        return true;
    }

    private String genererCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < longueur; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
