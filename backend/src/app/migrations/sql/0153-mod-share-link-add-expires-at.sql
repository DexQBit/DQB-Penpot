ALTER TABLE share_link ADD COLUMN IF NOT EXISTS expires_at timestamptz NULL;
