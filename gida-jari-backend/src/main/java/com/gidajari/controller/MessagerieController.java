package com.gidajari.controller;

import com.gidajari.dto.Requetes.MessageRequest;
import com.gidajari.dto.Requetes.NouveauMessageRequest;
import com.gidajari.entity.Conversation;
import com.gidajari.entity.Message;
import com.gidajari.security.CurrentUserProvider;
import com.gidajari.service.MessagerieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Messagerie interne entre utilisateurs (entites Conversation / Message). */
@RestController
@RequestMapping("/api/messagerie")
@RequiredArgsConstructor
public class MessagerieController {

    private final MessagerieService messagerieService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/conversations")
    public List<Conversation> mesConversations() {
        return messagerieService.mesConversations(currentUserProvider.getCurrentUserId());
    }

    /** Ecrit a un utilisateur (cree la conversation si besoin). */
    @PostMapping("/messages")
    public Message ecrire(@Valid @RequestBody NouveauMessageRequest req) {
        return messagerieService.ecrire(currentUserProvider.getCurrentUser(), req.destinataireId(), req.projetId(), req.contenu());
    }

    @GetMapping("/conversations/{id}/messages")
    public List<Message> messages(@PathVariable String id) {
        return messagerieService.messages(id, currentUserProvider.getCurrentUser());
    }

    @PostMapping("/conversations/{id}/messages")
    public Message repondre(@PathVariable String id, @Valid @RequestBody MessageRequest req) {
        return messagerieService.repondre(id, currentUserProvider.getCurrentUser(), req.contenu());
    }
}
