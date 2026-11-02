package com.gidajari.repository;

import com.gidajari.entity.WhatsAppSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, String> {
}
