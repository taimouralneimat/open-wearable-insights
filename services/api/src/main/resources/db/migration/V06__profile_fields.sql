-- Adds the first real "who is this" fields to accounts. Previously the
-- account row carried nothing personal (email, created_at, local_only) —
-- every controller hardcoded account_id=1 with no visible identity behind
-- it. display_name and primary_goal are the minimum needed for the app to
-- feel like it belongs to someone, and for primary_goal to eventually
-- tailor coaching emphasis (not yet wired into scoring — display/context only).
ALTER TABLE accounts ADD COLUMN display_name TEXT;
ALTER TABLE accounts ADD COLUMN primary_goal TEXT;
