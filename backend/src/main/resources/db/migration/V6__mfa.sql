-- Multi-factor auth (TOTP). The secret is stored only as vault-encrypted ciphertext.
ALTER TABLE users ADD COLUMN mfa_secret_enc TEXT;
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
