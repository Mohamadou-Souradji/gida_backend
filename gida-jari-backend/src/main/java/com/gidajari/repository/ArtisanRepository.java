package com.gidajari.repository;

import com.gidajari.entity.Artisan;
import com.gidajari.entity.enums.DisponibiliteStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtisanRepository extends JpaRepository<Artisan, String> {
    List<Artisan> findByCorpsDeMetierAndZoneInterventionAndDisponibilite(
            String corpsDeMetier, String zoneIntervention, DisponibiliteStatus disponibilite);

    /**
     * Recherche souple, insensible a la casse. Passer "" (jamais null) pour ignorer
     * un critere : evite l'erreur PostgreSQL "could not determine data type of parameter".
     */
    @Query("select a from Artisan a where lower(coalesce(a.corpsDeMetier, '')) like lower(concat('%', :metier, '%')) "
            + "and lower(coalesce(a.zoneIntervention, '')) like lower(concat('%', :zone, '%')) "
            + "and (:disponibleSeulement = false or a.disponibilite = com.gidajari.entity.enums.DisponibiliteStatus.AVAILABLE_NOW) "
            + "order by a.profileScore desc")
    List<Artisan> rechercher(@Param("metier") String metier, @Param("zone") String zone,
                             @Param("disponibleSeulement") boolean disponibleSeulement);
}
