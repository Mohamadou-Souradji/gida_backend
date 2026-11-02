package com.gidajari.repository;

import com.gidajari.entity.Projet;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjetRepository extends JpaRepository<Projet, String> {
    List<Projet> findByClientIdOrderByCreatedAtDesc(String clientId);
    List<Projet> findByPublieTrueOrderByCreatedAtDesc();

    long countByPublieTrue();

    /** Recherche back-office : titre, localisation ou telephone du client (q = motif LIKE en minuscules). */
    @Query("select p from Projet p join p.client c where lower(p.titre) like :q "
            + "or lower(coalesce(p.localisation, '')) like :q or c.phone like :q order by p.createdAt desc")
    List<Projet> rechercherAdmin(@Param("q") String q);
}
