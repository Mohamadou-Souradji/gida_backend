package com.gidajari.repository;

import com.gidajari.entity.RetraitCompte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
/** Le parametre q est un motif LIKE deja en minuscules ("%" = tout). */
public interface RetraitCompteRepository extends JpaRepository<RetraitCompte, String> {

    @Query("select r from RetraitCompte r join r.user u where (u.phone like :q or r.numero like :q "
            + "or lower(coalesce(r.reference, '')) like :q) order by r.createdAt desc")
    Page<RetraitCompte> rechercher(@Param("q") String q, Pageable pageable);

    @Query("select coalesce(sum(r.montantFcfa), 0) from RetraitCompte r")
    long totalMontant();

    @Query("select r.createdAt, r.montantFcfa from RetraitCompte r where r.createdAt >= :depuis")
    List<Object[]> montantsDepuis(@Param("depuis") Instant depuis);
}
