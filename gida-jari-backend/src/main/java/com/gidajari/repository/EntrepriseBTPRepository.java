package com.gidajari.repository;

import com.gidajari.entity.EntrepriseBTP;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntrepriseBTPRepository extends JpaRepository<EntrepriseBTP, String> {
    List<EntrepriseBTP> findAllByOrderByProfileScoreDesc();
}
