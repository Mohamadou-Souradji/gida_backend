package com.gidajari.repository;

import com.gidajari.entity.Jalon;
import com.gidajari.entity.enums.JalonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface JalonRepository extends JpaRepository<Jalon, String> {

    /** Utilise par le scheduler d'auto-validation (48h sans action du client). */
    List<Jalon> findByStatutAndSoumisLeBefore(JalonStatus statut, Instant seuil);

    List<Jalon> findByProjetIdOrderByOrdreAsc(String projetId);

    long countByStatut(JalonStatus statut);

    long countByStatutAndSoumisLeBefore(JalonStatus statut, Instant seuil);
}
