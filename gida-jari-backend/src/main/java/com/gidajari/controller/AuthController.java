package com.gidajari.controller;

import com.gidajari.dto.AuthResponseDto;
import com.gidajari.dto.OtpRequestDto;
import com.gidajari.dto.OtpVerifyDto;
import com.gidajari.entity.*;
import com.gidajari.entity.enums.AdminRole;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.entity.enums.OtpChannel;
import com.gidajari.repository.UserRepository;
import com.gidajari.security.JwtTokenProvider;
import com.gidajari.service.AdminEquipeService;
import com.gidajari.service.KYCService;
import com.gidajari.service.OTPService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authentification par numero de telephone + code a usage unique envoye par email (ou SMS) - regle n5.
 * Fonctionnalite commune aux 6 profils - voir Cahier des Charges, section 2 (V1 - 50%).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OTPService otpService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KYCService kycService;

    private final AdminEquipeService adminEquipeService;

    /**
     * Etape 1 - demande d'envoi du code.
     *
     * Canal email (par defaut) : le telephone reste l'identifiant du compte, le code part sur
     * l'email du compte. A l'inscription (ou pour un ancien compte sans email), le serveur
     * repond d'abord emailRequis=true sans rien envoyer ; le frontend redemande alors avec l'email.
     */
    @PostMapping("/otp/demander")
    public ResponseEntity<Map<String, Object>> demanderOtp(@Valid @RequestBody OtpRequestDto dto) {
        String phone = OTPService.normaliserTelephone(dto.phone());
        Optional<User> existant = userRepository.findByPhone(phone);
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("phone", phone);
        reponse.put("nouvelUtilisateur", existant.isEmpty());

        if (!otpService.parEmail()) {
            otpService.genererEtEnvoyer(phone, null);
            reponse.put("canal", "SMS");
            reponse.put("emailRequis", false);
            return ResponseEntity.accepted().body(reponse);
        }

        reponse.put("canal", "EMAIL");
        String email = existant.map(User::getEmail).orElse(null);
        if (email == null) {
            if (dto.email() == null || dto.email().isBlank()) {
                reponse.put("emailRequis", true);
                return ResponseEntity.ok(reponse);
            }
            email = OTPService.normaliserEmail(dto.email());
            verifierEmailLibre(email, phone);
        }
        otpService.genererEtEnvoyer(phone, email);
        reponse.put("emailRequis", false);
        reponse.put("emailMasque", OTPService.masquerEmail(email));
        return ResponseEntity.accepted().body(reponse);
    }

    /** Un email ne peut appartenir qu'a un seul compte (HTTP 409 sinon). */
    private void verifierEmailLibre(String email, String phone) {
        userRepository.findByEmail(email)
                .filter(u -> !u.getPhone().equals(phone))
                .ifPresent(u -> {
                    throw new IllegalStateException("Cette adresse email est déjà utilisée par un autre compte");
                });
    }

    /**
     * Etape 2 - verification du code OTP.
     * Si l'utilisateur n'existe pas encore pour ce numero, il est cree avec
     * le profileType fourni (inscription) et l'email qui a recu le code. Sinon, connexion simple.
     * Le contact (email, ou telephone en mode SMS) etant verifie, le niveau KYC 1 est accorde.
     */
    @PostMapping("/otp/verifier")
    @Transactional
    public ResponseEntity<?> verifierOtp(@Valid @RequestBody OtpVerifyDto dto) {
        String phone = OTPService.normaliserTelephone(dto.phone());
        Optional<OTPVerification> valide = otpService.verifier(phone, dto.code());
        if (valide.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("erreur", "OTP_INVALIDE", "message", "Code incorrect ou expire"));
        }
        OTPVerification otp = valide.get();

        boolean estNouveau = !userRepository.existsByPhone(phone);
        if (estNouveau && dto.profileType() == null) {
            throw new IllegalArgumentException("Choisissez votre profil pour creer votre compte");
        }
        if (otp.getEmail() != null) {
            verifierEmailLibre(otp.getEmail(), phone);
        }
        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User nouveau = creerUtilisateurSelonProfil(dto);
            nouveau.setPhone(phone);
            return nouveau;
        });
        if (!user.isActif()) {
            return ResponseEntity.status(403).body(Map.of("erreur", "COMPTE_SUSPENDU", "message", "Compte suspendu"));
        }
        if (otp.getCanal() == OtpChannel.EMAIL) {
            if (user.getEmail() == null) {
                user.setEmail(otp.getEmail());
            }
            user.setEmailVerifie(true);
        } else {
            user.setPhoneVerifie(true);
        }
        user = userRepository.save(user);
        kycService.elever(user, KycLevel.NIVEAU_1_TELEPHONE_VERIFIE);

        // profileType est une colonne discriminante (insertable=false) : non renseignee
        // sur l'entite juste apres creation, d'ou l'usage de dto.profileType() dans ce cas.
        String profileType = estNouveau ? dto.profileType().name() : user.getProfileType().name();
        Optional<AdminRole> roleAdmin = adminEquipeService.roleDuTelephone(phone);
        boolean admin = roleAdmin.isPresent();

        String token = jwtTokenProvider.genererToken(user.getId(), profileType, admin);
        return ResponseEntity.ok(new AuthResponseDto(token, user.getId(), profileType, user.getKycLevel().name(), admin,
                roleAdmin.map(Enum::name).orElse(null)));
    }

    private User creerUtilisateurSelonProfil(OtpVerifyDto dto) {
        if (dto.profileType() == null) {
            throw new IllegalArgumentException("Choisissez votre profil pour creer votre compte");
        }
        return switch (dto.profileType()) {
            case CLIENT_PARTICULIER -> new ClientParticulier();
            case ARTISAN -> new Artisan();
            case ENTREPRISE_BTP -> new EntrepriseBTP();
            case INGENIEUR_ARCHITECTE -> new IngenieurArchitecte();
            case FOURNISSEUR_MATERIAUX -> new FournisseurMateriaux();
            case ONG_INSTITUTION -> new ONGInstitution();
        };
    }
}
