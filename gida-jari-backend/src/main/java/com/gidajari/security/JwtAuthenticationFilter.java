package com.gidajari.security;

import com.gidajari.service.AdminEquipeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtre extrayant et validant le JWT depuis l'en-tete Authorization.
 * Le principal est l'id utilisateur ; les autorites sont ROLE_<PROFIL> et,
 * le cas echeant, ROLE_ADMIN + ROLE_ADMIN_<ROLE> (utilisees par @PreAuthorize dans les controleurs).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminEquipeService adminEquipeService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            jwtTokenProvider.lireClaims(header.substring(7)).ifPresent(claims -> {
                List<GrantedAuthority> autorites = new ArrayList<>();
                Object profil = claims.get("profileType");
                if (profil != null) {
                    autorites.add(new SimpleGrantedAuthority("ROLE_" + profil));
                }
                if (Boolean.TRUE.equals(claims.get("admin", Boolean.class))) {
                    // Le role est relu a chaque requete : un retrait ou un changement de role
                    // par un super-admin s'applique immediatement, sans attendre l'expiration du jeton.
                    adminEquipeService.roleDeUtilisateur(claims.getSubject()).ifPresent(role -> {
                        autorites.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        autorites.add(new SimpleGrantedAuthority("ROLE_ADMIN_" + role.name()));
                    });
                }
                var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, autorites);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
