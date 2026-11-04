ALTER TABLE dms.document_audit
    DROP CONSTRAINT IF EXISTS document_audit_approval_status_check;

ALTER TABLE dms.document_audit
    ADD CONSTRAINT document_audit_approval_status_check
    CHECK (approval_status IN ('A', 'R', 'D', 'I', 'P', 'APPROVED', 'UNAUTHORIZED'));
