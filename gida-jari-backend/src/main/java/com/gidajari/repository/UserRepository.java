package com.gidajari.repository;

import com.gidajari.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);

    // --- Back-office (tableau de bord)
    @Query("select u.profileType, count(u) from User u group by u.profileType")
    List<Object[]> compterParProfil();

    @Query("select u.kycLevel, count(u) from User u group by u.kycLevel")
    List<Object[]> compterParKyc();

    long countByCreatedAtAfter(Instant date);

    long countByActifFalse();

    @Query("select u.createdAt from User u where u.createdAt >= :depuis")
    List<Instant> datesInscriptionDepuis(@Param("depuis") Instant depuis);
}
