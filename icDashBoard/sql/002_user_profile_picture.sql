-- 002: Add profile_picture_url to users table
-- Stores the URL to the user's profile picture (Gravatar, Microsoft Graph, etc.)

ALTER TABLE users ADD COLUMN profile_picture_url VARCHAR(500) NULL AFTER full_name;
