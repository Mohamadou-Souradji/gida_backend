package com.gidajari.repository;

import com.gidajari.entity.AdminActionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, String> {
    List<AdminActionLog> findTop100ByOrderByCreatedAtDesc();
}
