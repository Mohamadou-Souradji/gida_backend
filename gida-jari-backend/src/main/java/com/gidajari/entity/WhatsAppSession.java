package com.gidajari.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entite P2 - Session du pont de communication WhatsApp, pour les
 * utilisateurs peu familiers avec l'usage intensif d'une application dediee.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "whatsapp_session")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String waPhoneId;
    private String etatConversation; // machine a etat du bot WhatsApp (ex: MENU_PRINCIPAL, ATTENTE_DEVIS...)

    private Instant derniereInteraction = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
