-- Longer notes, and indexes for the access patterns the API actually uses.

ALTER TABLE expenses ALTER COLUMN notes SET DATA TYPE VARCHAR(1000);

-- Listing/filtering/summaries are always scoped to one user and ordered or ranged by date.
CREATE INDEX IF NOT EXISTS idx_expenses_user_date ON expenses (user_id, date);
-- Deleting a category nulls expenses.category_id; this keeps that lookup (and FK checks) from scanning the table.
CREATE INDEX IF NOT EXISTS idx_expenses_category ON expenses (category_id);
CREATE INDEX IF NOT EXISTS idx_categories_user ON categories (user_id);
