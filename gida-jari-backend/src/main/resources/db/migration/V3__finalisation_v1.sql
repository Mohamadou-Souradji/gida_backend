-- Gida Jari - V3 : finalisation du socle V1 (parcours escrow complet, litiges, notifications)

ALTER TABLE jalon ADD COLUMN commentaire_client TEXT;

ALTER TABLE escrow_milestone ADD COLUMN commission_fcfa BIGINT;
ALTER TABLE escrow_milestone ADD COLUMN montant_net_fcfa BIGINT;

ALTER TABLE escrow_transaction ADD COLUMN numero_payeur VARCHAR(20);
ALTER TABLE escrow_transaction ADD COLUMN reference_paiement VARCHAR(100);
ALTER TABLE escrow_transaction ADD COLUMN motif_litige TEXT;
ALTER TABLE escrow_transaction ADD COLUMN litige_ouvert_par_id VARCHAR(36) REFERENCES users(id);

ALTER TABLE notification_log ADD COLUMN lu BOOLEAN NOT NULL DEFAULT FALSE;

-- Index utiles aux nouveaux ecrans (missions, notifications, messagerie, catalogue)
CREATE INDEX idx_projet_client ON projet(client_id);
CREATE INDEX idx_jalon_projet ON jalon(projet_id);
CREATE INDEX idx_escrow_projet ON escrow_transaction(projet_id);
CREATE INDEX idx_escrow_beneficiaire ON escrow_transaction(beneficiaire_id);
CREATE INDEX idx_milestone_jalon ON escrow_milestone(jalon_id);
CREATE INDEX idx_notification_destinataire ON notification_log(destinataire_id);
CREATE INDEX idx_message_conversation ON message(conversation_id);
CREATE INDEX idx_catalog_fournisseur ON catalog_produit(fournisseur_id);
CREATE INDEX idx_candidature_ao ON candidature_ao(appel_offre_id);
CREATE INDEX idx_notation_note_pour ON notation(note_pour_id);
