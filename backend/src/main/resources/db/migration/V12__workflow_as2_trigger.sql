-- Lets a workflow auto-run the moment a specific AS2 partner sends us something, instead of
-- needing a manual "Run now" click or a cron schedule. Nullable, no FK constraint -- matches the
-- existing loose reference style used elsewhere (e.g. connectorId inside step config JSON).
ALTER TABLE workflows ADD COLUMN as2_trigger_partner_id UUID NULL;
