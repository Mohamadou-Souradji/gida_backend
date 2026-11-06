package com.gidajari.controller;

import com.gidajari.dto.*;
import com.gidajari.entity.*;
import com.gidajari.entity.enums.AdminRole;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.entity.enums.OtpChannel;
import com.gidajari.repository.UserRepository;
import com.gidajari.security.JwtTokenProvider;
import com.gidajari.service.AdminEquipeService;
import com.gidajari.service.KYCService;
import com.gidajari.service.OTPService;
import com.gidajari.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authentification V2 : OTP + JWT (access + refresh tokens) + mot de passe.
 * 
 * Flow d'inscription :
 * 1. POST /auth/send-otp → Envoie l'OTP par email
 * 2. POST /auth/verify-otp → Vérifie l'OTP, retourne un token temporaire
 * 3. POST /auth/register → Crée le compte avec mot de passe
 * 
 * Flow de connexion :
 * 4. POST /auth/login → Connexion avec email/phone + password
 * 
 * Gestion tokens :
 * 5. POST /auth/refresh → Renouvelle l'access token avec le refresh token
 * 
 * Gestion mot de passe :
 * 6. POST /auth/change-password → Change le mot de passe (utilisateur connecté)
 * 7. POST /auth/forgot-password → Demande de réinitialisation (envoie OTP)
 * 8. POST /auth/reset-password → Réinitialise le mot de passe avec OTP
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
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    /**
     * Étape 1 - Demande d'envoi du code OTP par email.
     * Le téléphone reste l'identifiant du compte, le code part sur l'email.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, Object>> envoyerOtp(@Valid @RequestBody OtpRequestDto dto) {
        String phone = OTPService.normaliserTelephone(dto.phone());
        Optional<User> existant = userRepository.findByPhone(phone);
        
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("phone", phone);
        reponse.put("nouvelUtilisateur", existant.isEmpty());

        // Mode SMS non disponible pour le moment, on force email
        if (!otpService.parEmail()) {
            reponse.put("erreur", "SMS_NON_DISPONIBLE");
            reponse.put("message", "L'envoi par SMS n'est pas encore disponible. Utilisez l'email.");
            return ResponseEntity.badRequest().body(reponse);
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

    /**
     * Étape 2 - Vérification du code OTP.
     * Retourne un token temporaire valide 10 minutes pour permettre l'inscription ou la mise à jour.
     */
    @PostMapping("/verify-otp")
    @Transactional
    public ResponseEntity<?> verifierOtp(@Valid @RequestBody OtpVerifyDto dto) {
        String phone = OTPService.normaliserTelephone(dto.phone());
        Optional<OTPVerification> valide = otpService.verifier(phone, dto.code());
        
        if (valide.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "OTP_INVALIDE", 
                "message", "Code incorrect ou expiré"
            ));
        }
        
        OTPVerification otp = valide.get();
        
        // Vérifier si l'utilisateur existe déjà
        Optional<User> utilisateurExistant = userRepository.findByPhone(phone);
        
        // Si l'utilisateur existe déjà, générer un token temporaire pour la mise à jour
        if (utilisateurExistant.isPresent()) {
            User user = utilisateurExistant.get();
            String tokenTemporaire = jwtTokenProvider.genererTokenTemporaire(phone, otp.getEmail());
            
            return ResponseEntity.ok(Map.of(
                "temporaryToken", tokenTemporaire,
                "phone", phone,
                "emailMasque", OTPService.masquerEmail(otp.getEmail()),
                "compteExistant", true,
                "userId", user.getId(),
                "profileType", user.getProfileType().name()
            ));
        }
        
        // Vérifier que l'email est libre (nouveau compte)
        if (otp.getEmail() != null) {
            verifierEmailLibre(otp.getEmail(), phone);
        }
        
        // Générer un token temporaire pour l'inscription
        String tokenTemporaire = jwtTokenProvider.genererTokenTemporaire(phone, otp.getEmail());
        
        return ResponseEntity.ok(new TemporaryTokenDto(
            tokenTemporaire,
            phone,
            OTPService.masquerEmail(otp.getEmail())
        ));
    }

    /**
     * Étape 3 - Inscription avec informations de base.
     * Crée le compte avec les informations communes : nom, prénom, adresse, mot de passe.
     * L'utilisateur pourra ensuite compléter son profil avec les infos spécifiques.
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> inscription(@Valid @RequestBody RegisterRequestDto dto) {
        // Vérifier les mots de passe
        if (!dto.password().equals(dto.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of(
                "erreur", "MOTS_DE_PASSE_DIFFERENTS",
                "message", "Les mots de passe ne correspondent pas"
            ));
        }

        // Vérifier le token temporaire
        Optional<Claims> claims = jwtTokenProvider.lireClaims(dto.temporaryToken());
        if (claims.isEmpty() || !"temporary".equals(claims.get().get("type"))) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "TOKEN_INVALIDE",
                "message", "Token temporaire invalide ou expiré. Recommencez la vérification OTP."
            ));
        }

        String phone = claims.get().getSubject();
        String email = claims.get().get("email", String.class);

        // Vérifier que l'utilisateur n'existe pas déjà
        if (userRepository.existsByPhone(phone)) {
            return ResponseEntity.status(409).body(Map.of(
                "erreur", "COMPTE_EXISTE",
                "message", "Un compte existe déjà avec ce numéro"
            ));
        }

        // Créer l'utilisateur selon le profil
        User user = creerUtilisateurSelonProfil(dto.profileType());
        user.setPhone(phone);
        user.setEmail(email);
        user.setEmailVerifie(true);
        user.setPhoneVerifie(true);
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        
        // Définir les informations de base communes
        definirInfosBase(user, dto.nom(), dto.prenom(), dto.adresse());
        
        user = userRepository.save(user);
        
        // Élever au niveau KYC 1
        kycService.elever(user, KycLevel.NIVEAU_1_TELEPHONE_VERIFIE);

        // Vérifier si admin
        Optional<AdminRole> roleAdmin = adminEquipeService.roleDuTelephone(phone);
        boolean admin = roleAdmin.isPresent();

        // Générer les tokens
        String accessToken = jwtTokenProvider.genererAccessToken(
            user.getId(), 
            dto.profileType().name(), 
            admin
        );
        RefreshToken refreshToken = refreshTokenService.creerRefreshToken(user);

        return ResponseEntity.ok(new AuthResponseDto(
            accessToken,
            refreshToken.getToken(),
            user.getId(),
            dto.profileType().name(),
            user.getKycLevel().name(),
            admin,
            roleAdmin.map(Enum::name).orElse(null)
        ));
    }

    /**
     * Étape 4 (optionnelle) - Compléter le profil avec les informations spécifiques.
     * L'utilisateur doit être connecté (JWT requis).
     */
    @PostMapping("/complete-profile")
    @Transactional
    public ResponseEntity<?> completerProfil(@Valid @RequestBody UpdateProfileRequestDto dto) {
        // Récupérer l'utilisateur connecté
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "NON_AUTHENTIFIE",
                "message", "Vous devez être connecté pour compléter votre profil"
            ));
        }

        String userId = auth.getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable"));
        
        // Mettre à jour les champs selon le profil
        appliquerMiseAJour(user, dto);
        
        // Mettre à jour le mot de passe si fourni
        if (dto.password() != null && !dto.password().isBlank()) {
            if (!dto.password().equals(dto.confirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erreur", "MOTS_DE_PASSE_DIFFERENTS",
                    "message", "Les mots de passe ne correspondent pas"
                ));
            }
            user.setPasswordHash(passwordEncoder.encode(dto.password()));
        }
        
        user = userRepository.save(user);
        kycService.verifierIdentiteDeclaree(user);

        // Vérifier si admin
        Optional<AdminRole> roleAdmin = adminEquipeService.roleDuTelephone(user.getPhone());
        boolean admin = roleAdmin.isPresent();

        // Générer les tokens
        String accessToken = jwtTokenProvider.genererAccessToken(
            user.getId(), 
            user.getProfileType().name(), 
            admin
        );
        RefreshToken refreshToken = refreshTokenService.creerRefreshToken(user);

        return ResponseEntity.ok(new AuthResponseDto(
            accessToken,
            refreshToken.getToken(),
            user.getId(),
            user.getProfileType().name(),
            user.getKycLevel().name(),
            admin,
            roleAdmin.map(Enum::name).orElse(null)
        ));
    }

    /**
     * Étape 4 - Connexion avec email/téléphone + mot de passe.
     */
    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> connexion(@Valid @RequestBody LoginRequestDto dto) {
        // Trouver l'utilisateur par email ou téléphone
        User user = null;
        
        if (dto.email() != null && !dto.email().isBlank()) {
            String email = OTPService.normaliserEmail(dto.email());
            user = userRepository.findByEmail(email).orElse(null);
        } else if (dto.phone() != null && !dto.phone().isBlank()) {
            String phone = OTPService.normaliserTelephone(dto.phone());
            user = userRepository.findByPhone(phone).orElse(null);
        }

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "IDENTIFIANTS_INVALIDES",
                "message", "Email/téléphone ou mot de passe incorrect"
            ));
        }

        // Vérifier le mot de passe
        if (user.getPasswordHash() == null || 
            !passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "IDENTIFIANTS_INVALIDES",
                "message", "Email/téléphone ou mot de passe incorrect"
            ));
        }

        // Vérifier que le compte est actif
        if (!user.isActif()) {
            return ResponseEntity.status(403).body(Map.of(
                "erreur", "COMPTE_SUSPENDU",
                "message", "Votre compte a été suspendu. Contactez le support."
            ));
        }

        // Vérifier si admin
        Optional<AdminRole> roleAdmin = adminEquipeService.roleDuTelephone(user.getPhone());
        boolean admin = roleAdmin.isPresent();

        // Générer les tokens
        String accessToken = jwtTokenProvider.genererAccessToken(
            user.getId(),
            user.getProfileType().name(),
            admin
        );
        RefreshToken refreshToken = refreshTokenService.creerRefreshToken(user);

        return ResponseEntity.ok(new AuthResponseDto(
            accessToken,
            refreshToken.getToken(),
            user.getId(),
            user.getProfileType().name(),
            user.getKycLevel().name(),
            admin,
            roleAdmin.map(Enum::name).orElse(null)
        ));
    }

    /**
     * Étape 5 - Rafraîchir l'access token avec le refresh token.
     */
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<?> rafraichirToken(@Valid @RequestBody RefreshTokenRequestDto dto) {
        Optional<RefreshToken> refreshToken = refreshTokenService.verifierRefreshToken(dto.refreshToken());
        
        if (refreshToken.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "REFRESH_TOKEN_INVALIDE",
                "message", "Refresh token invalide, expiré ou révoqué. Reconnectez-vous."
            ));
        }

        User user = refreshToken.get().getUser();
        
        if (!user.isActif()) {
            return ResponseEntity.status(403).body(Map.of(
                "erreur", "COMPTE_SUSPENDU",
                "message", "Votre compte a été suspendu"
            ));
        }

        // Vérifier si admin
        Optional<AdminRole> roleAdmin = adminEquipeService.roleDuTelephone(user.getPhone());
        boolean admin = roleAdmin.isPresent();

        // Générer un nouvel access token
        String accessToken = jwtTokenProvider.genererAccessToken(
            user.getId(),
            user.getProfileType().name(),
            admin
        );

        return ResponseEntity.ok(new AuthResponseDto(
            accessToken,
            dto.refreshToken(), // Le refresh token reste le même
            user.getId(),
            user.getProfileType().name(),
            user.getKycLevel().name(),
            admin,
            roleAdmin.map(Enum::name).orElse(null)
        ));
    }

    /**
     * Étape 6 - Changer le mot de passe (utilisateur connecté).
     */
    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changerMotDePasse(@Valid @RequestBody ChangePasswordRequestDto dto) {
        // Récupérer l'utilisateur connecté
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "NON_AUTHENTIFIE",
                "message", "Vous devez être connecté pour changer votre mot de passe"
            ));
        }

        String userId = auth.getName();
        User user = userRepository.findById(userId).orElse(null);
        
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                "erreur", "UTILISATEUR_INTROUVABLE",
                "message", "Utilisateur non trouvé"
            ));
        }

        // Vérifier l'ancien mot de passe
        if (user.getPasswordHash() == null || 
            !passwordEncoder.matches(dto.oldPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "MOT_DE_PASSE_INCORRECT",
                "message", "L'ancien mot de passe est incorrect"
            ));
        }

        // Vérifier que les nouveaux mots de passe correspondent
        if (!dto.newPassword().equals(dto.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of(
                "erreur", "MOTS_DE_PASSE_DIFFERENTS",
                "message", "Les nouveaux mots de passe ne correspondent pas"
            ));
        }

        // Changer le mot de passe
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        // Révoquer tous les refresh tokens existants
        refreshTokenService.revoquerTousLesTokens(user);

        return ResponseEntity.ok(Map.of(
            "message", "Mot de passe changé avec succès. Reconnectez-vous avec votre nouveau mot de passe."
        ));
    }

    /**
     * Étape 7 - Demander la réinitialisation du mot de passe (envoie OTP).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> motDePasseOublie(@Valid @RequestBody ForgotPasswordRequestDto dto) {
        // Trouver l'utilisateur
        User user = null;
        
        if (dto.email() != null && !dto.email().isBlank()) {
            String email = OTPService.normaliserEmail(dto.email());
            user = userRepository.findByEmail(email).orElse(null);
        } else if (dto.phone() != null && !dto.phone().isBlank()) {
            String phone = OTPService.normaliserTelephone(dto.phone());
            user = userRepository.findByPhone(phone).orElse(null);
        }

        // Pour des raisons de sécurité, on ne révèle pas si le compte existe
        if (user == null) {
            return ResponseEntity.accepted().body(Map.of(
                "message", "Si un compte existe avec ces identifiants, un code de vérification a été envoyé."
            ));
        }

        // Envoyer l'OTP
        otpService.genererEtEnvoyer(user.getPhone(), user.getEmail());

        return ResponseEntity.accepted().body(Map.of(
            "message", "Un code de vérification a été envoyé par email",
            "phone", user.getPhone(),
            "emailMasque", OTPService.masquerEmail(user.getEmail())
        ));
    }

    /**
     * Étape 8 - Réinitialiser le mot de passe avec le code OTP.
     */
    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> reinitialiserMotDePasse(@Valid @RequestBody ResetPasswordRequestDto dto) {
        // Vérifier que les mots de passe correspondent
        if (!dto.newPassword().equals(dto.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of(
                "erreur", "MOTS_DE_PASSE_DIFFERENTS",
                "message", "Les mots de passe ne correspondent pas"
            ));
        }

        // Vérifier l'OTP
        String phone = OTPService.normaliserTelephone(dto.phone());
        Optional<OTPVerification> valide = otpService.verifier(phone, dto.code());
        
        if (valide.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "erreur", "OTP_INVALIDE",
                "message", "Code incorrect ou expiré"
            ));
        }

        // Trouver l'utilisateur
        User user = userRepository.findByPhone(phone).orElse(null);
        
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                "erreur", "UTILISATEUR_INTROUVABLE",
                "message", "Aucun compte trouvé avec ce numéro"
            ));
        }

        // Réinitialiser le mot de passe
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        // Révoquer tous les refresh tokens existants
        refreshTokenService.revoquerTousLesTokens(user);

        return ResponseEntity.ok(Map.of(
            "message", "Mot de passe réinitialisé avec succès. Vous pouvez maintenant vous connecter."
        ));
    }

    // ========== Méthodes utilitaires ==========

    /** Un email ne peut appartenir qu'à un seul compte (HTTP 409 sinon). */
    private void verifierEmailLibre(String email, String phone) {
        userRepository.findByEmail(email)
                .filter(u -> !u.getPhone().equals(phone))
                .ifPresent(u -> {
                    throw new IllegalStateException("Cette adresse email est déjà utilisée par un autre compte");
                });
    }

    private User creerUtilisateurSelonProfil(com.gidajari.entity.enums.ProfileType profileType) {
        if (profileType == null) {
            throw new IllegalArgumentException("Type de profil requis");
        }
        switch (profileType) {
            case CLIENT_PARTICULIER:
                return new ClientParticulier();
            case ARTISAN:
                return new Artisan();
            case ENTREPRISE_BTP:
                return new EntrepriseBTP();
            case INGENIEUR_ARCHITECTE:
                return new IngenieurArchitecte();
            case FOURNISSEUR_MATERIAUX:
                return new FournisseurMateriaux();
            case ONG_INSTITUTION:
                return new ONGInstitution();
            default:
                throw new IllegalArgumentException("Type de profil non reconnu");
        }
    }

    /** Définir les informations de base communes à tous les profils */
    private void definirInfosBase(User user, String nom, String prenom, String adresse) {
        if (user instanceof ClientParticulier client) {
            client.setNom(nom);
            client.setPrenom(prenom);
            client.setAdresse(adresse);
        } else if (user instanceof Artisan artisan) {
            artisan.setNom(nom);
            artisan.setPrenom(prenom);
            // L'adresse n'existe pas pour Artisan, on l'ignore
        } else if (user instanceof EntrepriseBTP entreprise) {
            // Pour les entreprises, on stocke nom+prenom dans nomCommercial temporairement
            entreprise.setNomCommercial(nom + " " + prenom);
        } else if (user instanceof IngenieurArchitecte expert) {
            expert.setNom(nom);
            expert.setPrenom(prenom);
        } else if (user instanceof FournisseurMateriaux fournisseur) {
            // Pour les fournisseurs, on stocke dans nomBoutique temporairement
            fournisseur.setNomBoutique(nom + " " + prenom);
        } else if (user instanceof ONGInstitution ong) {
            // Pour les ONG, on stocke dans raisonSociale temporairement
            ong.setRaisonSociale(nom + " " + prenom);
        }
    }

    /** Définir le nom complet selon le type d'utilisateur */
    private void definirNomComplet(User user, String nomComplet) {
        // Pour l'instant, on sépare le nom complet en nom/prénom de manière simple
        // Le frontend pourra plus tard demander nom et prénom séparément
        String[] parties = nomComplet.trim().split("\\s+", 2);
        String nom = parties[0];
        String prenom = parties.length > 1 ? parties[1] : "";
        
        if (user instanceof ClientParticulier) {
            ClientParticulier client = (ClientParticulier) user;
            client.setNom(nom);
            client.setPrenom(prenom);
        } else if (user instanceof Artisan) {
            Artisan artisan = (Artisan) user;
            artisan.setNom(nom);
            artisan.setPrenom(prenom);
        } else if (user instanceof EntrepriseBTP) {
            EntrepriseBTP entreprise = (EntrepriseBTP) user;
            entreprise.setNomCommercial(nomComplet);
        } else if (user instanceof IngenieurArchitecte) {
            IngenieurArchitecte ingenieur = (IngenieurArchitecte) user;
            ingenieur.setNom(nom);
            ingenieur.setPrenom(prenom);
        } else if (user instanceof FournisseurMateriaux) {
            FournisseurMateriaux fournisseur = (FournisseurMateriaux) user;
            fournisseur.setNomBoutique(nomComplet);
        } else if (user instanceof ONGInstitution) {
            ONGInstitution ong = (ONGInstitution) user;
            ong.setRaisonSociale(nomComplet);
        } else {
            throw new IllegalArgumentException("Type de profil non reconnu");
        }
    }

    /**
     * Applique les mises à jour du DTO sur l'entité User selon son type de profil.
     * Seuls les champs non-null du DTO sont appliqués.
     */
    private void appliquerMiseAJour(User user, UpdateProfileRequestDto dto) {
        if (user instanceof ClientParticulier client) {
            if (dto.nom() != null) client.setNom(dto.nom());
            if (dto.prenom() != null) client.setPrenom(dto.prenom());
            if (dto.adresse() != null) client.setAdresse(dto.adresse());
            if (dto.typeClient() != null) client.setTypeClient(dto.typeClient());
        } else if (user instanceof Artisan artisan) {
            if (dto.nom() != null) artisan.setNom(dto.nom());
            if (dto.prenom() != null) artisan.setPrenom(dto.prenom());
            if (dto.corpsDeMetier() != null) artisan.setCorpsDeMetier(dto.corpsDeMetier());
            if (dto.zoneIntervention() != null) artisan.setZoneIntervention(dto.zoneIntervention());
            if (dto.tarifJournalierFcfa() != null) artisan.setTarifJournalierFcfa(dto.tarifJournalierFcfa());
        } else if (user instanceof EntrepriseBTP entreprise) {
            if (dto.nomCommercial() != null) entreprise.setNomCommercial(dto.nomCommercial());
            if (dto.nif() != null) entreprise.setNif(dto.nif());
            if (dto.rccm() != null) entreprise.setRccm(dto.rccm());
            if (dto.domaineActivite() != null) entreprise.setDomaineActivite(dto.domaineActivite());
            if (dto.effectif() != null) entreprise.setEffectif(dto.effectif());
        } else if (user instanceof IngenieurArchitecte expert) {
            if (dto.nom() != null) expert.setNom(dto.nom());
            if (dto.prenom() != null) expert.setPrenom(dto.prenom());
            if (dto.specialite() != null) expert.setSpecialite(dto.specialite());
            if (dto.numeroOrdre() != null) expert.setNumeroOrdre(dto.numeroOrdre());
            if (dto.agrementMinisteriel() != null) expert.setAgrementMinisteriel(dto.agrementMinisteriel());
            if (dto.grilleTarifaireJson() != null) expert.setGrilleTarifaireJson(dto.grilleTarifaireJson());
        } else if (user instanceof FournisseurMateriaux fournisseur) {
            if (dto.nomBoutique() != null) fournisseur.setNomBoutique(dto.nomBoutique());
            if (dto.nif() != null) fournisseur.setNif(dto.nif());
            if (dto.categories() != null) fournisseur.setCategories(dto.categories());
            if (dto.zonesLivraison() != null) fournisseur.setZonesLivraison(dto.zonesLivraison());
            if (dto.fraisLivraisonFcfa() != null) fournisseur.setFraisLivraisonFcfa(dto.fraisLivraisonFcfa());
        } else if (user instanceof ONGInstitution ong) {
            if (dto.raisonSociale() != null) ong.setRaisonSociale(dto.raisonSociale());
            if (dto.nifOuRccm() != null) ong.setNifOuRccm(dto.nifOuRccm());
            if (dto.typeOrganisation() != null) ong.setTypeOrganisation(dto.typeOrganisation());
            if (dto.statutLegal() != null) ong.setStatutLegal(dto.statutLegal());
            if (dto.oddCible() != null) ong.setOddCible(dto.oddCible());
            if (dto.logoUrl() != null) ong.setLogoUrl(dto.logoUrl());
        }
    }
}
