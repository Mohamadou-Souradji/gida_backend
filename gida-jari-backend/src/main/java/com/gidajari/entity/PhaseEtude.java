package com.gidajari.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Phase individuelle d'une PropositionEtude (ex: esquisse, avant-projet, execution). */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "phase_etude")
@Getter
@Setter
@NoArgsConstructor
public class PhaseEtude {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposition_id", nullable = false)
    private PropositionEtude proposition;

    private Integer ordre;
    private String libelle;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Long montantFcfa;
}
