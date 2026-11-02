package com.gidajari.service;

import com.gidajari.entity.Conversation;
import com.gidajari.entity.Message;
import com.gidajari.entity.User;
import com.gidajari.repository.ConversationRepository;
import com.gidajari.repository.MessageRepository;
import com.gidajari.repository.ProjetRepository;
import com.gidajari.repository.UserRepository;
import com.gidajari.service.exception.AccesRefuseException;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Messagerie interne (entites Conversation / Message, P2) : les echanges
 * restent sur la plateforme (anti-contournement, trace en cas de litige).
 */
@Service
@RequiredArgsConstructor
public class MessagerieService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ProjetRepository projetRepository;
    private final NotificationService notificationService;

    /** Envoie un message a un utilisateur, en reutilisant la conversation existante entre les deux. */
    @Transactional
    public Message ecrire(User expediteur, String destinataireId, String projetId, String contenu) {
        if (expediteur.getId().equals(destinataireId)) {
            throw new IllegalArgumentException("Impossible de s'ecrire a soi-meme");
        }
        User destinataire = userRepository.findById(destinataireId)
                .orElseThrow(() -> new RessourceIntrouvableException("Destinataire introuvable"));
        Conversation c = conversationRepository.findEntre(expediteur.getId(), destinataireId).stream()
                .findFirst()
                .orElseGet(() -> {
                    Conversation nouvelle = new Conversation();
                    nouvelle.setParticipantA(expediteur);
                    nouvelle.setParticipantB(destinataire);
                    if (projetId != null && !projetId.isBlank()) {
                        projetRepository.findById(projetId).ifPresent(nouvelle::setProjet);
                    }
                    return conversationRepository.save(nouvelle);
                });
        return ajouter(c, expediteur, contenu);
    }

    @Transactional
    public Message repondre(String conversationId, User expediteur, String contenu) {
        Conversation c = getParticipant(conversationId, expediteur);
        return ajouter(c, expediteur, contenu);
    }

    public List<Conversation> mesConversations(String userId) {
        return conversationRepository.findByParticipant(userId);
    }

    /** Messages d'une conversation ; marque comme lus ceux recus par le lecteur. */
    @Transactional
    public List<Message> messages(String conversationId, User lecteur) {
        getParticipant(conversationId, lecteur);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        messages.stream()
                .filter(m -> !m.isLu() && !m.getExpediteur().getId().equals(lecteur.getId()))
                .forEach(m -> m.setLu(true));
        return messages;
    }

    public long nonLus(String conversationId, String userId) {
        return messageRepository.countByConversationIdAndLuFalseAndExpediteurIdNot(conversationId, userId);
    }

    private Message ajouter(Conversation c, User expediteur, String contenu) {
        Message m = new Message();
        m.setConversation(c);
        m.setExpediteur(expediteur);
        m.setContenu(contenu);
        Message saved = messageRepository.save(m);
        User autre = c.getParticipantA().getId().equals(expediteur.getId()) ? c.getParticipantB() : c.getParticipantA();
        notificationService.notifier(autre, "Nouveau message", contenu.length() > 80 ? contenu.substring(0, 80) + "..." : contenu);
        return saved;
    }

    private Conversation getParticipant(String conversationId, User user) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RessourceIntrouvableException("Conversation introuvable"));
        if (!c.getParticipantA().getId().equals(user.getId()) && !c.getParticipantB().getId().equals(user.getId())) {
            throw new AccesRefuseException("Vous ne participez pas a cette conversation");
        }
        return c;
    }
}
