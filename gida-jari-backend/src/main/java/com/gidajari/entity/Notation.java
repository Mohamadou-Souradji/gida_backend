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
 * Entite P2 - Notation multi-criteres (5 criteres ponderes).
 * Voir Architecture Globale V3, section 5.2.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "notation")
@Getter
@Setter
@NoArgsConstructor
public class Notation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projet_id", nullable = false)
    private Projet projet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_par_id", nullable = false)
    private User notePar;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_pour_id", nullable = false)
    private User notePour;

    // 5 criteres ponderes (echelle 1-5)
    private Integer qualite;
    private Integer delai;
    private Integer communication;
    private Integer respectBudget;
    private Integer proprete;

    private Double scoreGlobal;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
