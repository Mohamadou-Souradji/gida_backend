package com.gidajari.repository;

import com.gidajari.entity.AppelOffre;
import com.gidajari.entity.enums.AppelOffreStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppelOffreRepository extends JpaRepository<AppelOffre, String> {
    List<AppelOffre> findByStatut(AppelOffreStatus statut);
    List<AppelOffre> findByStatutOrderByCreatedAtDesc(AppelOffreStatus statut);
    List<AppelOffre> findByInstitutionIdOrderByCreatedAtDesc(String institutionId);
}
