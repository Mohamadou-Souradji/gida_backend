-- Migration V6 : Système d'authentification avec JWT (access + refresh tokens) et mot de passe
-- Ajoute le support du mot de passe et des refresh tokens

-- Ajouter la colonne password_hash dans la table users
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

-- Créer la table refresh_tokens
CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expire_le TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

-- Index pour améliorer les performances
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expire_le ON refresh_tokens(expire_le);

-- Commentaires
COMMENT ON COLUMN users.password_hash IS 'Hash BCrypt du mot de passe de l''utilisateur';
COMMENT ON TABLE refresh_tokens IS 'Tokens de rafraîchissement pour renouveler l''access token JWT';
COMMENT ON COLUMN refresh_tokens.token IS 'Token JWT de rafraîchissement (longue durée : 7 jours)';
COMMENT ON COLUMN refresh_tokens.expire_le IS 'Date d''expiration du refresh token';
COMMENT ON COLUMN refresh_tokens.revoked IS 'Si true, le token a été révoqué (déconnexion, changement mot de passe)';
