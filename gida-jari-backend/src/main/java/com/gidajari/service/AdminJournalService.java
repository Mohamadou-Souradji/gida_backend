package com.gidajari.service;

import com.gidajari.entity.AdminActionLog;
import com.gidajari.entity.User;
import com.gidajari.repository.AdminActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Journal d'audit des actions d'administration (entite P3 AdminActionLog). */
@Service
@RequiredArgsConstructor
public class AdminJournalService {

    private final AdminActionLogRepository repository;

    @Transactional
    public void journaliser(User admin, String action, String cibleType, String cibleId, String detail) {
        AdminActionLog log = new AdminActionLog();
        log.setAdmin(admin);
        log.setAction(action);
        log.setCibleType(cibleType);
        log.setCibleId(cibleId);
        log.setDetail(detail);
        repository.save(log);
    }

    public List<AdminActionLog> dernieresActions() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }
}
