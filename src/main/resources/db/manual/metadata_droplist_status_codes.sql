BEGIN;

ALTER TABLE dms.app_metadata_droplist
    DROP CONSTRAINT IF EXISTS chk_app_metadata_droplist_status;

UPDATE dms.app_metadata_droplist
SET status = CASE UPPER(status)
    WHEN 'ACTIVE' THEN 'A'
    WHEN 'APPROVED' THEN 'A'
    WHEN 'INACTIVE' THEN 'A'
    WHEN 'PENDING' THEN 'P'
    WHEN 'REJECTED' THEN 'R'
    ELSE UPPER(status)
END,
is_active = CASE WHEN UPPER(status) = 'INACTIVE' THEN FALSE ELSE is_active END
WHERE UPPER(status) IN ('ACTIVE', 'APPROVED', 'INACTIVE', 'PENDING', 'REJECTED');

ALTER TABLE dms.app_metadata_droplist
    ALTER COLUMN status SET DEFAULT 'A';

ALTER TABLE dms.app_metadata_droplist
    ADD CONSTRAINT chk_app_metadata_droplist_status
    CHECK (status IN ('I', 'P', 'A', 'R', 'D'));

COMMIT;
