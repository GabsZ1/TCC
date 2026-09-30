-- V33 was already applied in local databases. Its inferred creator and source
-- cannot be trusted: a Customer may have been linked to a User after an internal booking.
-- Keep audit generated after V33, and leave unknown historical authors/origins unset.
UPDATE appointments
SET source = NULL, created_by_user_id = NULL
WHERE created_at::timestamp <= (
    SELECT installed_on FROM flyway_schema_history WHERE version = '33' ORDER BY installed_rank DESC LIMIT 1
);

-- Legacy updated_at can predate the transition. Keep backfilled timestamps
-- within the earliest valid point without changing newer audited transitions.
UPDATE appointments
SET no_show_at = GREATEST(no_show_at, starts_at + INTERVAL '15 minutes')
WHERE status = 'NO_SHOW'
  AND created_at::timestamp <= (
      SELECT installed_on FROM flyway_schema_history WHERE version = '33' ORDER BY installed_rank DESC LIMIT 1
  );

UPDATE appointments
SET invalidated_at = GREATEST(invalidated_at, completed_at)
WHERE status = 'INVALIDATED'
  AND created_at::timestamp <= (
      SELECT installed_on FROM flyway_schema_history WHERE version = '33' ORDER BY installed_rank DESC LIMIT 1
  );
