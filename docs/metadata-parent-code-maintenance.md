# Metadata Maintenance Parent Code Enhancement

## Request fields

Metadata maintenance input and search requests now include `mdParentCode`.

```json
{
  "mdType": "VAULT_BRANCH",
  "mdCode": "BRANCH-01",
  "mdDesc": "Head Office",
  "mdParentType": "REGION",
  "mdParentCode": "REGION-01",
  "isActive": true
}
```

`mdParentType` and `mdParentCode` are a pair:

- Both must be `null`/omitted or both must contain nonblank values.
- The parent code must exist for the same parent type.
- A parent code is valid only when it is active and approved in `dms.app_metadata_droplist`, or exists in `dms.app_sysdata_droplist`.

Validation errors use the existing metadata maintenance conflict conventions:

```json
{
  "error": "mdParentType and mdParentCode must both be supplied or both be null"
}
```

or:

```json
{
  "error": "The parent code is missing, inactive, or not approved for the selected parent type"
}
```

## Search

Search supports `mdParentType` and `mdParentCode` as optional partial, case-insensitive text filters.

```json
{
  "mdType": "VAULT",
  "mdCode": "BRANCH",
  "mdDesc": "Office",
  "mdParentType": "REGION",
  "mdParentCode": "REGION-01",
  "status": "A",
  "isActive": true,
  "createdBy": "admin",
  "page": 0,
  "size": 25
}
```

## Response

Search, detail, pending, and workbench responses now include `mdParentCode`.

```json
{
  "id": 501,
  "wbId": null,
  "mdType": "VAULT_BRANCH",
  "mdCode": "BRANCH-01",
  "mdDesc": "Head Office",
  "isActive": true,
  "mdParentType": "REGION",
  "mdParentCode": "REGION-01",
  "status": "A",
  "createdBy": "admin",
  "createdDatetime": "2026-11-28T05:30:00+05:30",
  "submittedBy": null,
  "submittedDatetime": null,
  "approvedBy": "reviewer",
  "approvedDatetime": "2026-11-28T05:35:00+05:30",
  "rejectedBy": null,
  "rejectedDatetime": null,
  "rejectionReason": null,
  "updatedBy": null,
  "updatedDatetime": null,
  "actionType": null,
  "version": 2
}
```

## Workflow behavior

- Create, draft, edit, submit, and approval paths validate the parent pair before state changes.
- Parent status/activity may change after submission; approval revalidates the workbench parent pair.
- Edit approval copies `mdParentType` and `mdParentCode` to main and archives both exact prior values.
- Create approval copies both values from workbench to main.
- Archive/history SQL retains the parent type and code for approved prior versions.
- Deletion requests preserve the current approved parent pair.

No database migration or backfill is required by this application change because the three table columns and constraints have already been deployed manually.
