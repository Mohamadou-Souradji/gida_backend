package com.gidajari.repository;

import com.gidajari.entity.RefreshToken;
import com.gidajari.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    
    Optional<RefreshToken> findByToken(String token);
    
    void deleteByUser(User user);
    
    void deleteByExpireLeBefore(Instant now);
}
