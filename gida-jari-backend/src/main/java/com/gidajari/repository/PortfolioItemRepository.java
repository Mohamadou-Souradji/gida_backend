package com.gidajari.repository;

import com.gidajari.entity.PortfolioItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, String> {
    List<PortfolioItem> findByProprietaireIdOrderByCreatedAtDesc(String proprietaireId);
}
