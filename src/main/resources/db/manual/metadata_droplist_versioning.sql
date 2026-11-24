BEGIN;

ALTER TABLE dms.app_metadata_droplist
    ADD COLUMN IF NOT EXISTS version int4;

UPDATE dms.app_metadata_droplist main
SET version = COALESCE((
    SELECT MAX(archive.version) + 1
    FROM dms.app_metadata_droplist_au archive
    WHERE archive.md_type = main.md_type
      AND archive.md_code = main.md_code
), 1)
WHERE main.version IS NULL;

ALTER TABLE dms.app_metadata_droplist
    ALTER COLUMN version SET DEFAULT 1;

ALTER TABLE dms.app_metadata_droplist
    ALTER COLUMN version SET NOT NULL;

UPDATE dms.app_metadata_droplist_wb workbench
SET version = CASE workbench.action_type
    WHEN 'U' THEN main.version + 1
    WHEN 'D' THEN main.version
    ELSE 1
END
FROM dms.app_metadata_droplist main
WHERE workbench.md_type = main.md_type
  AND workbench.md_code = main.md_code
  AND workbench.action_type IN ('U', 'D');

UPDATE dms.app_metadata_droplist_wb
SET version = 1
WHERE action_type = 'I';

COMMIT;
