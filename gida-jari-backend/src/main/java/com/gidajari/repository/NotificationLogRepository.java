package com.gidajari.repository;

import com.gidajari.entity.NotificationLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {
    List<NotificationLog> findTop50ByDestinataireIdOrderByCreatedAtDesc(String destinataireId);
    long countByDestinataireIdAndLuFalse(String destinataireId);
}
