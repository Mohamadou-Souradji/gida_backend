-- Gida Jari - Schema initial (V1)
-- Genere a partir des 17 entites du MCD (Cahier des Charges Technique, section 1.1)
-- Strategie d'heritage JOINED : "users" (colonnes communes) + une table par profil (PK = FK vers users.id)
-- A completer/valider par l'equipe technique avant toute mise en production.

CREATE TABLE users (
    id                 VARCHAR(36) PRIMARY KEY,
    phone              VARCHAR(20) NOT NULL UNIQUE,
    profile_type       VARCHAR(30) NOT NULL,
    kyc_level          VARCHAR(40) NOT NULL DEFAULT 'NIVEAU_0_NON_VERIFIE',
    wallet_balance     BIGINT NOT NULL DEFAULT 0,
    profile_score      DOUBLE PRECISION DEFAULT 0,
    actif              BOOLEAN NOT NULL DEFAULT TRUE,
    phone_verifie      BOOLEAN NOT NULL DEFAULT FALSE,
    langue_preferee    VARCHAR(5) DEFAULT 'fr',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ
);

CREATE TABLE client_particulier (
    id          VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    nom         VARCHAR(100),
    prenom      VARCHAR(100),
    adresse     VARCHAR(255),
    type_client VARCHAR(50)
);

CREATE TABLE artisan (
    id                     VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    nom                    VARCHAR(100),
    prenom                 VARCHAR(100),
    corps_de_metier        VARCHAR(50),
    zone_intervention      VARCHAR(100),
    tarif_journalier_fcfa  BIGINT,
    disponibilite          VARCHAR(20) DEFAULT 'UNAVAILABLE',
    badge                  VARCHAR(30)
);

CREATE TABLE entreprise_btp (
    id                 VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    nom_commercial     VARCHAR(150),
    nif                VARCHAR(50),
    rccm               VARCHAR(50),
    domaine_activite   VARCHAR(150),
    effectif           INTEGER
);

CREATE TABLE ingenieur_architecte (
    id                       VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    nom                      VARCHAR(100),
    prenom                   VARCHAR(100),
    specialite               VARCHAR(100),
    numero_ordre             VARCHAR(50),
    agrement_ministeriel     VARCHAR(100),
    grille_tarifaire_json    TEXT
);

CREATE TABLE fournisseur_materiaux (
    id                     VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    nom_boutique           VARCHAR(150),
    nif                    VARCHAR(50),
    categories             VARCHAR(255),
    zones_livraison        VARCHAR(255),
    frais_livraison_fcfa   BIGINT
);

CREATE TABLE ong_institution (
    id                  VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    raison_sociale      VARCHAR(150),
    nif_ou_rccm         VARCHAR(50),
    type_organisation   VARCHAR(100),
    statut_legal        VARCHAR(100),
    odd_cible           VARCHAR(150),
    logo_url            VARCHAR(255)
);

CREATE TABLE projet (
    id                  VARCHAR(36) PRIMARY KEY,
    client_id           VARCHAR(36) NOT NULL REFERENCES users(id),
    titre               VARCHAR(200) NOT NULL,
    description         TEXT,
    type_projet         VARCHAR(50),
    localisation        VARCHAR(150),
    budget_estime_fcfa  BIGINT,
    publie              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE jalon (
    id                 VARCHAR(36) PRIMARY KEY,
    projet_id          VARCHAR(36) NOT NULL REFERENCES projet(id) ON DELETE CASCADE,
    libelle            VARCHAR(200),
    ordre              INTEGER,
    statut             VARCHAR(20) NOT NULL DEFAULT 'A_FAIRE',
    montant_fcfa       BIGINT,
    preuve_photo_urls  TEXT,
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    soumis_le          TIMESTAMPTZ,
    valide_le          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE escrow_transaction (
    id                     VARCHAR(36) PRIMARY KEY,
    projet_id              VARCHAR(36) NOT NULL REFERENCES projet(id),
    payeur_id              VARCHAR(36) NOT NULL REFERENCES users(id),
    beneficiaire_id        VARCHAR(36) NOT NULL REFERENCES users(id),
    montant_total_fcfa     BIGINT NOT NULL,
    montant_libere_fcfa    BIGINT NOT NULL DEFAULT 0,
    niveau_commission      VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    statut                 VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    operateur_mobile_money VARCHAR(30),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ
);

CREATE TABLE escrow_milestone (
    id                       VARCHAR(36) PRIMARY KEY,
    escrow_transaction_id    VARCHAR(36) NOT NULL REFERENCES escrow_transaction(id) ON DELETE CASCADE,
    jalon_id                 VARCHAR(36) REFERENCES jalon(id),
    montant_fcfa             BIGINT,
    libere                   BOOLEAN NOT NULL DEFAULT FALSE,
    libere_le                TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE kyc_submission (
    id                       VARCHAR(36) PRIMARY KEY,
    user_id                  VARCHAR(36) NOT NULL REFERENCES users(id),
    niveau_vise              VARCHAR(40),
    type_document            VARCHAR(50),
    document_url             VARCHAR(255),
    statut                   VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    commentaire_validation   TEXT,
    valide_par_admin_id      VARCHAR(36) REFERENCES users(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    traite_le                TIMESTAMPTZ
);

CREATE TABLE portfolio_item (
    id              VARCHAR(36) PRIMARY KEY,
    proprietaire_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    titre           VARCHAR(150),
    description     TEXT,
    photo_url       VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE otp_verification (
    id          VARCHAR(36) PRIMARY KEY,
    phone       VARCHAR(20) NOT NULL,
    code_hash   VARCHAR(255) NOT NULL,
    canal       VARCHAR(20) NOT NULL DEFAULT 'SMS',
    utilise     BOOLEAN NOT NULL DEFAULT FALSE,
    tentatives  INTEGER NOT NULL DEFAULT 0,
    expire_le   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_phone ON otp_verification(phone);

CREATE TABLE notation (
    id               VARCHAR(36) PRIMARY KEY,
    projet_id        VARCHAR(36) NOT NULL REFERENCES projet(id),
    note_par_id      VARCHAR(36) NOT NULL REFERENCES users(id),
    note_pour_id     VARCHAR(36) NOT NULL REFERENCES users(id),
    qualite          INTEGER,
    delai            INTEGER,
    communication    INTEGER,
    respect_budget   INTEGER,
    proprete         INTEGER,
    score_global     DOUBLE PRECISION,
    commentaire      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation (
    id               VARCHAR(36) PRIMARY KEY,
    participant_a_id VARCHAR(36) NOT NULL REFERENCES users(id),
    participant_b_id VARCHAR(36) NOT NULL REFERENCES users(id),
    projet_id        VARCHAR(36) REFERENCES projet(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE message (
    id               VARCHAR(36) PRIMARY KEY,
    conversation_id  VARCHAR(36) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    expediteur_id    VARCHAR(36) NOT NULL REFERENCES users(id),
    contenu          TEXT,
    lu               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE catalog_produit (
    id                   VARCHAR(36) PRIMARY KEY,
    fournisseur_id       VARCHAR(36) NOT NULL REFERENCES fournisseur_materiaux(id) ON DELETE CASCADE,
    nom                  VARCHAR(150),
    categorie            VARCHAR(100),
    unite                VARCHAR(20),
    prix_unitaire_fcfa   BIGINT,
    stock_disponible     INTEGER,
    actif                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE appel_offre (
    id                        VARCHAR(36) PRIMARY KEY,
    institution_id            VARCHAR(36) NOT NULL REFERENCES ong_institution(id),
    titre                     VARCHAR(200),
    cahier_des_charges_texte  TEXT,
    date_limite_depot         TIMESTAMPTZ,
    statut                    VARCHAR(30) NOT NULL DEFAULT 'BROUILLON',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE candidature_ao (
    id                        VARCHAR(36) PRIMARY KEY,
    appel_offre_id            VARCHAR(36) NOT NULL REFERENCES appel_offre(id) ON DELETE CASCADE,
    candidat_id               VARCHAR(36) NOT NULL REFERENCES users(id),
    proposition_technique     TEXT,
    montant_proposition_fcfa  BIGINT,
    statut                    VARCHAR(20) NOT NULL DEFAULT 'SOUMISE',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_log (
    id                  VARCHAR(36) PRIMARY KEY,
    destinataire_id     VARCHAR(36) NOT NULL REFERENCES users(id),
    canal               VARCHAR(20),
    titre               VARCHAR(150),
    contenu             TEXT,
    envoye_avec_succes  BOOLEAN,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE whatsapp_session (
    id                    VARCHAR(36) PRIMARY KEY,
    user_id               VARCHAR(36) NOT NULL REFERENCES users(id),
    wa_phone_id           VARCHAR(50),
    etat_conversation     VARCHAR(50),
    derniere_interaction  TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin_action_log (
    id           VARCHAR(36) PRIMARY KEY,
    admin_id     VARCHAR(36) NOT NULL REFERENCES users(id),
    action       VARCHAR(50),
    cible_type   VARCHAR(50),
    cible_id     VARCHAR(36),
    detail       TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
