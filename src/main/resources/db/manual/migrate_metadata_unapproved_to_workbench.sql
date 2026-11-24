BEGIN;

INSERT INTO dms.app_metadata_droplist_wb (
    md_type, md_code, md_desc, status, is_active, md_parent_type, action_type,
    created_by, created_datetime, submitted_by, submitted_datetime,
    rejected_by, rejected_datetime, rejection_reason,
    updated_by, updated_datetime, version
)
SELECT md_type, md_code, md_desc, status, is_active, md_parent_type, 'I',
       created_by, created_datetime,
       CASE WHEN status = 'P' THEN COALESCE(updated_by, created_by) END,
       CASE WHEN status = 'P' THEN COALESCE(updated_datetime, created_datetime) END,
       CASE WHEN status = 'R' THEN COALESCE(updated_by, created_by) END,
       CASE WHEN status = 'R' THEN COALESCE(updated_datetime, created_datetime) END,
       CASE WHEN status = 'R' THEN 'Migrated rejected metadata record' END,
       updated_by, updated_datetime, 1
FROM dms.app_metadata_droplist
WHERE status IN ('I', 'P', 'R');

DELETE FROM dms.app_metadata_droplist
WHERE status IN ('I', 'P', 'R');

ALTER TABLE dms.app_metadata_droplist
    DROP CONSTRAINT IF EXISTS chk_app_metadata_droplist_status;

ALTER TABLE dms.app_metadata_droplist
    ALTER COLUMN status SET DEFAULT 'A';

ALTER TABLE dms.app_metadata_droplist
    ADD CONSTRAINT chk_app_metadata_droplist_status
    CHECK (status IN ('A', 'D'));

COMMIT;
