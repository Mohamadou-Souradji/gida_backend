package com.gidajari.service;

import com.gidajari.entity.RefreshToken;
import com.gidajari.entity.User;
import com.gidajari.repository.RefreshTokenRepository;
import com.gidajari.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Gestion des refresh tokens pour renouveler l'access token sans redemander le mot de passe.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${gida-jari.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs; // 7 jours par défaut

    /**
     * Crée un nouveau refresh token pour l'utilisateur.
     * Supprime l'ancien refresh token s'il existe.
     */
    @Transactional
    public RefreshToken creerRefreshToken(User user) {
        // Supprimer les anciens tokens de cet utilisateur
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(jwtTokenProvider.genererRefreshToken(user.getId()));
        refreshToken.setExpireLe(Instant.now().plus(refreshExpirationMs, ChronoUnit.MILLIS));

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Vérifie et retourne le refresh token s'il est valide.
     */
    public Optional<RefreshToken> verifierRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> rt.getExpireLe().isAfter(Instant.now()));
    }

    /**
     * Révoque un refresh token.
     */
    @Transactional
    public void revoquerRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    /**
     * Révoque tous les refresh tokens d'un utilisateur.
     */
    @Transactional
    public void revoquerTousLesTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    /**
     * Nettoyage automatique des tokens expirés (tous les jours à 3h du matin).
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void nettoyerTokensExpires() {
        refreshTokenRepository.deleteByExpireLeBefore(Instant.now());
    }
}
