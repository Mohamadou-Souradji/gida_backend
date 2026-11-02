package com.gidajari.security;

import com.gidajari.entity.User;
import com.gidajari.repository.UserRepository;
import com.gidajari.service.exception.AccesRefuseException;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resout l'utilisateur authentifie a partir du contexte de securite.
 *
 * JwtAuthenticationFilter place l'id utilisateur (extrait du JWT) comme
 * "principal". Les controleurs utilisent cette classe plutot que de faire
 * confiance a un id transmis dans l'URL ou le corps de la requete.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String userId)) {
            throw new AccesRefuseException("Aucun utilisateur authentifié");
        }
        return userId;
    }

    public User getCurrentUser() {
        String id = getCurrentUserId();
        return userRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable: " + id));
    }

    public boolean estAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** Role d'administration de la session (SUPER_ADMIN, OPERATEUR_KYC...), ou null. */
    public String roleAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_ADMIN_"))
                .map(a -> a.substring("ROLE_ADMIN_".length()))
                .findFirst().orElse(null);
    }

    /** Leve 403 si l'utilisateur connecte n'est pas le proprietaire attendu. */
    public void exigerProprietaire(String proprietaireId) {
        if (proprietaireId == null || !proprietaireId.equals(getCurrentUserId())) {
            throw new AccesRefuseException("Cette ressource appartient a un autre utilisateur");
        }
    }
}
