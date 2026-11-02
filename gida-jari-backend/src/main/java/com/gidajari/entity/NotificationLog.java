package com.gidajari.entity;

import com.gidajari.entity.enums.NotificationChannel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Entite P2 - Journal des notifications envoyees (SMS/WhatsApp/Push/Email). */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private User destinataire;

    @Enumerated(EnumType.STRING)
    private NotificationChannel canal;

    private String titre;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    private boolean envoyeAvecSucces;

    private boolean lu = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
