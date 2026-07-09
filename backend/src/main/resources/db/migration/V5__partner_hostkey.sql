-- Host-key pinning: the expected SFTP server key fingerprint for a partner. When set, a
-- connection whose presented host key does not match is rejected (defends against MITM).
ALTER TABLE partners ADD COLUMN host_key_fingerprint TEXT;
