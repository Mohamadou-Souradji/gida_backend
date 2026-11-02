-- Gida Jari - V4 : back-office (equipe d'administration a roles distincts, historique des retraits)

-- Membres de l'equipe d'administration. Les numeros de GJ_ADMIN_PHONES restent super-admins
-- d'office (amorcage) ; les autres sont ajoutes depuis l'ecran "Equipe" par un super-admin.
CREATE TABLE admin_membre (
    id             VARCHAR(36) PRIMARY KEY,
    phone          VARCHAR(20) NOT NULL UNIQUE,
    nom            VARCHAR(120),
    role           VARCHAR(30) NOT NULL,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    ajoute_par_id  VARCHAR(36) REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

-- Retraits du solde Gida Jari vers Mobile Money (suivi financier du back-office).
CREATE TABLE retrait_compte (
    id             VARCHAR(36) PRIMARY KEY,
    user_id        VARCHAR(36) NOT NULL REFERENCES users(id),
    operateur      VARCHAR(30) NOT NULL,
    numero         VARCHAR(20) NOT NULL,
    montant_fcfa   BIGINT NOT NULL,
    reference      VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_retrait_user ON retrait_compte(user_id);
CREATE INDEX idx_retrait_date ON retrait_compte(created_at);
CREATE INDEX idx_escrow_statut ON escrow_transaction(statut);
CREATE INDEX idx_escrow_date ON escrow_transaction(created_at);
CREATE INDEX idx_projet_date ON projet(created_at);
CREATE INDEX idx_users_date ON users(created_at);
CREATE INDEX idx_kyc_statut ON kyc_submission(statut);
