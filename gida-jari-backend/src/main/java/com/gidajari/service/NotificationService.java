package com.gidajari.service;

import com.gidajari.entity.NotificationLog;
import com.gidajari.entity.User;
import com.gidajari.entity.enums.NotificationChannel;
import com.gidajari.integration.SmsGateway;
import com.gidajari.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notifications utilisateur (entite NotificationLog, P2).
 * Chaque evenement metier important (jalon soumis/valide, fonds liberes,
 * litige, candidature retenue...) est journalise et envoye par SMS - canal
 * le plus fiable sur le terrain (2G/3G, telephones basiques).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository notificationRepository;
    private final SmsGateway smsGateway;

    @Transactional
    public void notifier(User destinataire, String titre, String contenu) {
        if (destinataire == null) {
            return;
        }
        boolean envoye = smsGateway.envoyer(destinataire.getPhone(), "Gida Jari - " + titre + " : " + contenu);
        NotificationLog log = new NotificationLog();
        log.setDestinataire(destinataire);
        log.setCanal(NotificationChannel.SMS);
        log.setTitre(titre);
        log.setContenu(contenu);
        log.setEnvoyeAvecSucces(envoye);
        notificationRepository.save(log);
    }

    public List<NotificationLog> mesNotifications(String userId) {
        return notificationRepository.findTop50ByDestinataireIdOrderByCreatedAtDesc(userId);
    }

    public long nombreNonLues(String userId) {
        return notificationRepository.countByDestinataireIdAndLuFalse(userId);
    }

    @Transactional
    public void marquerToutesLues(String userId) {
        notificationRepository.findTop50ByDestinataireIdOrderByCreatedAtDesc(userId).forEach(n -> n.setLu(true));
    }
}
