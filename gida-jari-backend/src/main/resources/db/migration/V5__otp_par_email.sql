-- Gida Jari - V5 : code de connexion (OTP) envoye par email.
-- Le telephone reste l'identifiant du compte ; l'email (unique, en minuscules) recoit le code.

ALTER TABLE users ADD COLUMN email VARCHAR(160);
ALTER TABLE users ADD COLUMN email_verifie BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);

-- Email auquel le code a ete envoye (sert a creer le compte a la verification).
ALTER TABLE otp_verification ADD COLUMN email VARCHAR(160);
