package com.gidajari.repository;

import com.gidajari.entity.ClientParticulier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientParticulierRepository extends JpaRepository<ClientParticulier, String> {
}
