package com.gidajari.repository;

import com.gidajari.entity.AdminMembre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminMembreRepository extends JpaRepository<AdminMembre, String> {
    Optional<AdminMembre> findByPhone(String phone);
    List<AdminMembre> findAllByOrderByCreatedAtAsc();
}
