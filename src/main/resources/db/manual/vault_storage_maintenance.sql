BEGIN;

ALTER TABLE dms.vault_storage_au
    DROP CONSTRAINT IF EXISTS chk_vault_storage_au_action;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM dms.vault_storage_wb
        GROUP BY vault_tray_code
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce unique workbench tray codes: duplicate vault_tray_code values exist';
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_vault_storage_wb_tray_code
    ON dms.vault_storage_wb (vault_tray_code);

DO $$
DECLARE
    identity_sequence text;
    identity_last_value bigint;
    greatest_existing_id bigint;
BEGIN
    identity_sequence := pg_get_serial_sequence('dms.vault_storage_wb', 'vault_id');
    IF identity_sequence IS NULL THEN
        RAISE EXCEPTION 'Identity sequence for dms.vault_storage_wb.vault_id was not found';
    END IF;

    EXECUTE format('SELECT last_value FROM %s', identity_sequence::regclass)
        INTO identity_last_value;

    SELECT GREATEST(
               COALESCE((SELECT MAX(vault_id) FROM dms.vault_storage_mst), 0),
               COALESCE((SELECT MAX(vault_id) FROM dms.vault_storage_wb), 0),
               COALESCE(identity_last_value, 0),
               1
           )
    INTO greatest_existing_id;

    PERFORM setval(identity_sequence::regclass, greatest_existing_id, TRUE);
END;
$$;

COMMIT;
