-- Cron schedule for a saved workflow (null = not scheduled). Standard 5-field cron, UTC.
ALTER TABLE workflows ADD COLUMN cron_schedule TEXT;
