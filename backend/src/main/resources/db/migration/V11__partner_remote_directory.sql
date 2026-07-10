-- Optional default remote directory (drop folder) for a partner, e.g. "/inbound". Used as the
-- destination folder when sending files to this partner; can be overridden per transfer. NULL
-- means the partner's root directory.
ALTER TABLE partners ADD COLUMN remote_directory TEXT;
