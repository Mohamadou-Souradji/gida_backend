package com.gidajari;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entree de l'application Gida Jari.
 * "La maison de demain" - Plateforme BTP Niger / Afrique de l'Ouest.
 *
 * @EnableScheduling active la tache @Scheduled d'auto-validation des jalons (48h).
 * Voir: Cahier des Charges Technique, section 3.1 - Tache planifiee (Cron).
 *
 * UserDetailsServiceAutoConfiguration est exclue : l'authentification ne repose
 * pas sur un UserDetailsService Spring (username/password) mais sur l'OTP SMS +
 * JWT stateless geres manuellement (voir SecurityConfig, OTPService,
 * JwtAuthenticationFilter). Sans cette exclusion, Spring Boot genere un
 * utilisateur en memoire avec un mot de passe aleatoire affiche dans les logs
 * au demarrage - inutile et source de confusion ici.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class GidaJariApplication {

    public static void main(String[] args) {
        SpringApplication.run(GidaJariApplication.class, args);
    }
}
