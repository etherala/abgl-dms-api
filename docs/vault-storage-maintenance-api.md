# Vault Storage Maintenance API

Base path: `/api/vault/maintenance`. Requests require the application's normal authentication. Actor fields are populated from the authenticated user; do not send a username in request bodies.

## Browser UI

The responsive maintenance screen is available at `/vault-maintenance.html` after signing in to the application. It is implemented with static HTML, CSS, and JavaScript under `src/main/resources/static/`; the API repository has no separate frontend framework or component library. The page uses the authenticated session and Spring CSRF token endpoint, and calls only the API routes documented below. It provides search and pagination, separate main/workbench results, create/edit, pending review, dropdowns, confirmations, and audit history.

Before deployment, run `src/main/resources/db/manual/vault_storage_maintenance.sql`. It removes the invalid archive action constraint, checks and enforces unique workbench tray codes, and advances the workbench identity sequence past existing main/workbench IDs. Run it before enabling writes; resolve any pre-existing duplicate workbench tray codes it reports.

## Statuses and identifiers

- Main-table records use status `A`. A record can be inactive while remaining approved (`status: "A", isActive: false`).
- Workbench statuses: `I` draft, `P` pending approval, `R` rejected, `D` closed/deleted workbench request.
- Workbench action types: `I` create and `U` edit. There is no approved delete action. Deactivate an approved tray by editing it with `isActive: false` and obtaining approval.
- Search results include main and workbench rows. Both can appear for the same tray while a change is pending. `vaultId` identifies the logical tray; `wbId` identifies a workbench request. Use `vaultId` for approved-record endpoints and `wbId` for workbench actions.
- Approval removes the pending workbench row but preserves its audit events under the original `WB:<wbId>` workflow code. The approval audit event links that code to the main `vaultId`, so either audit endpoint can return the complete workflow history after approval.
- Saving or submitting a workbench row with status `R` or `D` reopens it as a draft or pending request. A pending row (`P`) cannot be edited or submitted again.

## Dropdowns

Load these lists from the existing metadata endpoint. It returns active approved metadata as `{ "items": [{ "md_code": "...", "md_desc": "..." }] }`.

| Form field | Request |
|---|---|
| `vaultLocation` | `GET /api/metadata/drop-list?md_type=VAULT_LOCATION` |
| `vaultBranch` | `GET /api/metadata/drop-list?md_type=VAULT_BRANCH` |
| `vaultRoom` | `GET /api/metadata/drop-list?md_type=VAULT_ROOM` |
| `vaultRack` | `GET /api/metadata/drop-list?md_type=VAULT_RACK` |
| `vaultShelf` | `GET /api/metadata/drop-list?md_type=VAULT_SHELF` |

Submit each dropdown's `md_code`, not its description. The API validates that all five selected values are active approved metadata.

## Common form request

All fields are required for create and edit requests. `isActive` is a JSON boolean.

```json
{
  "vaultTrayCode": "TRAY-001",
  "vaultTrayName": "North archive tray",
  "vaultShelf": "SHELF-01",
  "vaultRack": "RACK-02",
  "vaultRoom": "ROOM-01",
  "vaultBranch": "BRANCH-01",
  "vaultLocation": "LOC-01",
  "isActive": true
}
```

Limits: tray code 100 characters, tray name 255, shelf/rack/room/branch 100 each, location 255. `vaultTrayCode` is unique and immutable for an approved record.

## Endpoints

| Method and path | Purpose | Request body |
|---|---|---|
| `POST /search` | Paginated search across main and workbench rows | Search JSON below |
| `GET /records/{vaultId}` | Read an approved/main record | None |
| `POST /records` | Create and submit a new tray for approval | Common form request |
| `POST /records/draft` | Create a new draft | Common form request |
| `POST /records/{vaultId}/draft` | Start an edit draft for an approved tray | Common form request; tray code must match the existing record |
| `PUT /records/{vaultId}` | Submit an edit directly for approval | Common form request; set `isActive: false` to request deactivation |
| `GET /workbench/{wbId}` | Read a draft, pending, rejected, or closed workbench request | None |
| `PUT /workbench/{wbId}/draft` | Save an `I`, `R`, or `D` request as a draft (`I`) | Common form request |
| `POST /workbench/{wbId}/submit` | Submit an `I`, `R`, or `D` request (`P`) | None |
| `GET /pending` | List pending requests | None |
| `POST /workbench/{wbId}/approve` | Approve a pending create/edit | None |
| `POST /workbench/{wbId}/reject` | Reject a pending request | Rejection JSON below |
| `DELETE /workbench/{wbId}` | Close an `I` or `R` workbench request as `D` | None |
| `GET /records/{vaultId}/audit` | Read audit history associated with the approved tray | None |
| `GET /workbench/{wbId}/audit` | Read audit history associated with a workbench request | None |

There is deliberately no `DELETE /records/{vaultId}` endpoint. Approved records are not deleted.

### Search request

All fields are optional. Text filters are case-insensitive partial matches; `status` and `isActive` are exact filters. Page is zero-based, size defaults to 25 and is capped at 200.

```json
{
  "vaultTrayCode": "TRAY",
  "vaultTrayName": "archive",
  "vaultShelf": "SHELF-01",
  "vaultRack": "RACK-02",
  "vaultRoom": "ROOM-01",
  "vaultBranch": "BRANCH-01",
  "vaultLocation": "LOC-01",
  "status": "P",
  "isActive": true,
  "createdBy": "admin",
  "page": 0,
  "size": 25
}
```

### Rejection request

```json
{
  "reason": "The selected shelf does not match the archive plan."
}
```

Only a reviewer other than the submitter may approve or reject a request.

## Response examples

### Main/search row

```json
{
  "vaultId": 501,
  "wbId": null,
  "vaultTrayCode": "TRAY-001",
  "vaultTrayName": "North archive tray",
  "vaultShelf": "SHELF-01",
  "vaultRack": "RACK-02",
  "vaultRoom": "ROOM-01",
  "vaultBranch": "BRANCH-01",
  "vaultLocation": "LOC-01",
  "isActive": true,
  "status": "A",
  "actionType": null,
  "createdBy": "admin",
  "createdDatetime": "2026-11-25T09:00:00+05:30",
  "submittedBy": "admin",
  "submittedDatetime": "2026-11-25T09:05:00+05:30",
  "approvedBy": "reviewer",
  "approvedDatetime": "2026-11-25T09:15:00+05:30",
  "rejectedBy": null,
  "rejectedDatetime": null,
  "rejectionReason": null,
  "updatedBy": "admin",
  "updatedDatetime": "2026-11-25T09:15:00+05:30",
  "version": 1
}
```

### Workbench/search row

```json
{
  "vaultId": 501,
  "wbId": "8ca2cc9c-9a7f-4de5-9a31-06e66d6e47d2",
  "vaultTrayCode": "TRAY-001",
  "vaultTrayName": "North archive tray - updated",
  "vaultShelf": "SHELF-02",
  "vaultRack": "RACK-02",
  "vaultRoom": "ROOM-01",
  "vaultBranch": "BRANCH-01",
  "vaultLocation": "LOC-01",
  "isActive": true,
  "status": "P",
  "actionType": "U",
  "createdBy": "admin",
  "createdDatetime": "2026-11-25T10:00:00+05:30",
  "submittedBy": "admin",
  "submittedDatetime": "2026-11-25T10:05:00+05:30",
  "approvedBy": null,
  "approvedDatetime": null,
  "rejectedBy": null,
  "rejectedDatetime": null,
  "rejectionReason": null,
  "updatedBy": "admin",
  "updatedDatetime": "2026-11-25T10:05:00+05:30",
  "version": 2
}
```

For a new request, `vaultId` is generated by the workbench identity and becomes the main-table identifier after approval. For an edit, the workbench row carries the existing `vaultId`; its version is the next version expected on approval.

### Search page

```json
{
  "items": [],
  "total": 0,
  "page": 0,
  "size": 25
}
```

### Pending list

```json
{
  "items": [
    {
      "vaultId": 501,
      "wbId": "8ca2cc9c-9a7f-4de5-9a31-06e66d6e47d2",
      "vaultTrayCode": "TRAY-001",
      "vaultTrayName": "North archive tray",
      "vaultShelf": "SHELF-01",
      "vaultRack": "RACK-02",
      "vaultRoom": "ROOM-01",
      "vaultBranch": "BRANCH-01",
      "vaultLocation": "LOC-01",
      "isActive": true,
      "status": "P",
      "actionType": "U",
      "createdBy": "admin",
      "createdDatetime": "2026-11-25T10:00:00+05:30",
      "submittedBy": "admin",
      "submittedDatetime": "2026-11-25T10:05:00+05:30",
      "approvedBy": null,
      "approvedDatetime": null,
      "rejectedBy": null,
      "rejectedDatetime": null,
      "rejectionReason": null,
      "updatedBy": "admin",
      "updatedDatetime": "2026-11-25T10:05:00+05:30",
      "version": 2
    }
  ]
}
```

### Audit item

```json
{
  "auditId": 1234,
  "actionType": "P",
  "workflowType": "VAULT_STORAGE",
  "workflowCode": "WB:8ca2cc9c-9a7f-4de5-9a31-06e66d6e47d2",
  "actionBy": "admin",
  "actionAt": "2026-11-25T10:05:00+05:30",
  "actionDescription": "Submitted vault request",
  "remarks": "Vault record submitted for approval."
}
```

### Business error

Business errors use the applicable HTTP status and an error field, for example:

```json
{
  "error": "The submitter cannot approve or reject their own vault request"
}
```

Expected statuses: `400` invalid input/dropdown/status, `404` unknown `vaultId`/`wbId`, `409` duplicate tray, invalid workflow transition, same-user approval, or stale version. Bean-validation errors may use the application's standard validation error response.

## Workflow and UI rules

1. Load all five dropdown lists; show descriptions and submit codes.
2. The search grid should display tray code/name, location hierarchy, active flag, status, action, version, submitter/time, creator/time, and available workflow actions.
3. Keep main and workbench rows distinct when both exist. Main-row edit uses `vaultId`; workbench save/submit/approve/reject/cancel uses `wbId`.
4. New form actions: **Save draft** calls `POST /records/draft`; **Submit for approval** calls `POST /records`.
5. Approved-row edit actions: **Save draft** calls `POST /records/{vaultId}/draft`; **Submit for approval** calls `PUT /records/{vaultId}`.
6. Workbench draft actions: **Save** calls `PUT /workbench/{wbId}/draft`; **Submit** calls `POST /workbench/{wbId}/submit`. Both support statuses `I`, `R`, and `D`.
7. Approvers use `/pending`, then approve or reject by `wbId`. Require a confirmation dialog for approval and a required reason dialog for rejection. Hide/disable both decisions for the request's submitter; the API enforces this rule regardless.
8. A user can close a draft/rejected request to status `D`. To deactivate a tray, edit it and set `isActive=false`; there is no approved delete action.
9. Provide an audit-history panel for both approved rows and workbench rows. After approval, open the approved-record audit by `vaultId`.
10. Include loading, empty, validation, duplicate, stale-version, permission, and server-error states. Preserve unsaved form state after recoverable errors and show server messages without exposing SQL details.

## Copyable frontend implementation prompt

> Build a polished, responsive Vault Storage Maintenance UI for the existing authenticated application. Use the existing design system and component conventions. Implement a searchable paginated tray grid, create/edit form, reviewer queue, status badges, dropdowns loaded from the five `/api/metadata/drop-list?md_type=...` endpoints, confirmation/rejection dialogs, and audit history. Integrate exactly with the endpoint table and JSON contracts in this document. Keep `vaultId` for approved record operations and `wbId` for workbench operations; never substitute one for the other. Display main and workbench search rows separately if both exist for one tray. Support statuses `I`, `P`, `R`, `A`, and `D`; permit editing/saving/submitting workbench records in `I/R/D`; only allow approve/reject on pending `P`; do not provide approved deletion. Deactivate by submitting an edit with `isActive=false`. Do not send actor usernames; the backend derives identity. Provide accessible labels, keyboard navigation, clear inline validation, loading skeletons, empty states, success/error notifications, and responsive layouts. Use the provided request and response examples as the API source of truth; do not invent endpoints or fields.
