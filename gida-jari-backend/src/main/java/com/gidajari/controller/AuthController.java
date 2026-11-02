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

import java.util.Map;
import java.util.Optional;

/**
 * Authentification par numero de telephone + OTP SMS (regle de gestion n5).
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

    /** Etape 1 - demande d'envoi d'un code OTP par SMS. */
    @PostMapping("/otp/demander")
    public ResponseEntity<Map<String, Object>> demanderOtp(@Valid @RequestBody OtpRequestDto dto) {
        String phone = OTPService.normaliserTelephone(dto.phone());
        otpService.genererEtEnvoyer(phone, OtpChannel.SMS);
        return ResponseEntity.accepted().body(Map.of(
                "phone", phone,
                "nouvelUtilisateur", !userRepository.existsByPhone(phone)));
    }

    /**
     * Etape 2 - verification du code OTP.
     * Si l'utilisateur n'existe pas encore pour ce numero, il est cree avec
     * le profileType fourni (inscription). Sinon, connexion simple.
     * Le telephone etant verifie, le niveau KYC 1 est accorde.
     */
    @PostMapping("/otp/verifier")
    @Transactional
    public ResponseEntity<?> verifierOtp(@Valid @RequestBody OtpVerifyDto dto) {
        String phone = OTPService.normaliserTelephone(dto.phone());
        if (!otpService.verifier(phone, dto.code())) {
            return ResponseEntity.status(401).body(Map.of("erreur", "OTP_INVALIDE", "message", "Code incorrect ou expire"));
        }

        boolean estNouveau = !userRepository.existsByPhone(phone);
        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User nouveau = creerUtilisateurSelonProfil(dto);
            nouveau.setPhone(phone);
            nouveau.setPhoneVerifie(true);
            return userRepository.save(nouveau);
        });
        if (!user.isActif()) {
            return ResponseEntity.status(403).body(Map.of("erreur", "COMPTE_SUSPENDU", "message", "Compte suspendu"));
        }
        user.setPhoneVerifie(true);
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
