-- Global (user_id IS NULL) default categories available to every account.
-- Idempotent: skips any name that already exists as a global category.
INSERT INTO categories (name, icon, color, user_id)
SELECT v.name, v.icon, v.color, CAST(NULL AS BIGINT)
FROM (VALUES
    ('Food', '🍔', '#3b82f6'),
    ('Groceries', '🛒', '#22c55e'),
    ('Coffee', '☕', '#f97316'),
    ('Train', '🚆', '#a855f7'),
    ('Car', '🚗', '#ec4899'),
    ('Flights', '✈️', '#14b8a6'),
    ('Shopping', '🛍️', '#eab308'),
    ('Home', '🏠', '#64748b'),
    ('Bills', '💡', '#3b82f6'),
    ('Health', '💊', '#22c55e'),
    ('Fitness', '🏋️', '#f97316'),
    ('Entertainment', '🎬', '#a855f7'),
    ('Education', '📚', '#ec4899'),
    ('Gifts', '🎁', '#14b8a6'),
    ('Subscriptions', '📱', '#eab308'),
    ('Other', '📦', '#64748b')
) AS v (name, icon, color)
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.name = v.name AND c.user_id IS NULL
);
