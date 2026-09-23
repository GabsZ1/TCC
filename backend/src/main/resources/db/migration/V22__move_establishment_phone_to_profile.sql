INSERT INTO establishment_profiles (establishment_id, phone)
SELECT id, phone
FROM establishments
WHERE phone IS NOT NULL AND BTRIM(phone) <> ''
ON CONFLICT (establishment_id) DO UPDATE
SET phone = COALESCE(establishment_profiles.phone, EXCLUDED.phone),
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE establishments DROP COLUMN phone;

