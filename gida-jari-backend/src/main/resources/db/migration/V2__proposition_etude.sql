-- Gida Jari - V2 : propositions d'etudes par phases (profil Ingenieur/Architecte)
-- Complement au V1, ajoute suite a l'audit de conformite au Cahier des Charges Technique.

CREATE TABLE proposition_etude (
    id                  VARCHAR(36) PRIMARY KEY,
    expert_id           VARCHAR(36) NOT NULL REFERENCES ingenieur_architecte(id),
    projet_id           VARCHAR(36) NOT NULL REFERENCES projet(id),
    titre               VARCHAR(200),
    description         TEXT,
    montant_total_fcfa  BIGINT,
    statut              VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    envoyee_le          TIMESTAMPTZ
);

CREATE TABLE phase_etude (
    id              VARCHAR(36) PRIMARY KEY,
    proposition_id  VARCHAR(36) NOT NULL REFERENCES proposition_etude(id) ON DELETE CASCADE,
    ordre           INTEGER,
    libelle         VARCHAR(150),
    description     TEXT,
    montant_fcfa    BIGINT
);
