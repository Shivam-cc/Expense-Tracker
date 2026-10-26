-- Per-user display currency (ISO 4217). Existing users keep the INR formatting they had.

ALTER TABLE users ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'INR' NOT NULL;
