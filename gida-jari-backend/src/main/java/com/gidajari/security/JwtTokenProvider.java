package com.gidajari.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Generation et validation des tokens JWT.
 * Voir Cahier des Charges Technique, section 3.1 - Spring Security + JWT.
 *
 * Le token porte : sub = id utilisateur, profileType (pour les autorisations par profil)
 * et admin (role d'administration de la plateforme).
 */
@Component
public class JwtTokenProvider {

    @Value("${gida-jari.jwt.secret}")
    private String secret;

    @Value("${gida-jari.jwt.expiration-ms:3600000}")
    private long expirationMs; // 1 heure par défaut

    @Value("${gida-jari.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs; // 7 jours par défaut

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Génère un access token JWT (courte durée : 1h par défaut). */
    public String genererAccessToken(String userId, String profileType, boolean admin) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("profileType", profileType)
                .claim("admin", admin)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /** Génère un refresh token JWT (longue durée : 7 jours par défaut). */
    public String genererRefreshToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpirationMs))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /** Génère un token temporaire pour l'inscription (10 minutes). */
    public String genererTokenTemporaire(String phone, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(phone)
                .claim("email", email)
                .claim("type", "temporary")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 600000)) // 10 minutes
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /** Claims du token s'il est valide (signature + expiration), sinon vide. */
    public Optional<Claims> lireClaims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String getUserIdFromToken(String token) {
        return lireClaims(token).map(Claims::getSubject).orElse(null);
    }

    public boolean estValide(String token) {
        return lireClaims(token).isPresent();
    }
}
