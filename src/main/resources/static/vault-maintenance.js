(() => {
  "use strict";

  const API = "/api/vault/maintenance";
  const DROPDOWNS = ["VAULT_LOCATION", "VAULT_BRANCH", "VAULT_ROOM", "VAULT_RACK", "VAULT_SHELF"];
  const state = {
    csrf: "",
    page: 0,
    size: 25,
    total: 0,
    busy: false,
    user: null,
    pending: [],
    editor: null,
    confirmCallback: null,
    search: {}
  };
  const byId = (id) => document.getElementById(id);
  const form = byId("search-form");

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
  }

  function showAlert(message, type = "success") {
    const alert = byId("global-alert");
    alert.className = `alert${type === "error" ? " alert-error" : ""}`;
    alert.textContent = message;
    alert.hidden = false;
  }

  function toast(message, type = "success") {
    const node = document.createElement("div");
    node.className = `toast${type === "error" ? " toast-error" : ""}`;
    node.textContent = message;
    byId("toast-region").append(node);
    window.setTimeout(() => node.remove(), 4800);
  }

  async function fetchJson(url, options = {}, retry = true) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    if (!["GET", "HEAD", "OPTIONS"].includes(method) && state.csrf) {
      headers.set("X-CSRF-TOKEN", state.csrf);
    }

    let response;
    try {
      response = await fetch(url, { ...options, method, headers, credentials: "same-origin" });
    } catch {
      throw new Error("Unable to reach the application. Check your connection and try again.");
    }

    if (response.status === 403 && retry && !["GET", "HEAD", "OPTIONS"].includes(method)) {
      await loadCsrf();
      return fetchJson(url, options, false);
    }
    if (response.status === 204) return null;

    const contentType = response.headers.get("content-type") || "";
    let result;
    if (contentType.includes("application/json")) {
      result = await response.json();
    } else {
      result = await response.text();
    }
    if (!response.ok) {
      if (response.status === 401) throw new Error("Your session has expired. Sign in again to continue.");
      const message = result && typeof result === "object"
        ? (result.error || result.message || result.detail)
        : "";
      throw new Error(message || `Request failed (${response.status}). Please try again.`);
    }
    return result;
  }

  async function loadCsrf() {
    const token = await fetchJson("/api/auth/csrf", {}, false);
    state.csrf = token.token;
  }

  async function loadSession() {
    try {
      const session = await fetchJson("/api/auth/session", {}, false);
      state.user = session.user || null;
      if (state.user) {
        byId("user-name").textContent = state.user.displayName;
        byId("user-avatar").textContent = state.user.initials || "U";
        byId("user-chip").hidden = false;
      }
    } catch (error) {
      if (error.message.includes("session has expired")) {
        showAlert(error.message, "error");
      } else {
        showAlert("Unable to load your signed-in profile. Some review actions may be unavailable.", "error");
      }
    }
  }

  async function loadDropdowns() {
    const results = await Promise.allSettled(DROPDOWNS.map(async (type) => {
      const response = await fetchJson(`/api/metadata/drop-list?md_type=${encodeURIComponent(type)}`);
      const options = Array.isArray(response.items) ? response.items : [];
      document.querySelectorAll(`[data-dropdown="${type}"]`).forEach((select) => {
        const first = select.options[0];
        select.replaceChildren(first);
        options.forEach((item) => {
          const option = document.createElement("option");
          option.value = item.md_code;
          option.textContent = `${item.md_desc} (${item.md_code})`;
          select.append(option);
        });
      });
    }));
    const failed = results.filter((result) => result.status === "rejected");
    if (failed.length) {
      const message = failed[0].reason?.message || "A reference list could not be loaded.";
      showAlert(`Dropdown data could not be loaded: ${message} Refresh the page to retry.`, "error");
    }
  }

  function setLoading(kind, loading) {
    const body = byId(kind === "records" ? "records-body" : "pending-body");
    const wrap = byId(kind === "records" ? "records-table-wrap" : "pending-table-wrap");
    const stateCard = byId(kind === "records" ? "records-state" : "pending-state");
    if (loading) {
      stateCard.hidden = true;
      wrap.hidden = false;
      body.innerHTML = Array.from({ length: 4 }, () =>
        `<tr class="skeleton-row"><td colspan="6"><span class="skeleton"></span></td></tr>`).join("");
    }
  }

  function statusLabel(status) {
    return ({ A: "Approved", I: "Draft", P: "Pending", R: "Rejected", D: "Closed" })[status] || status || "Unknown";
  }

  function statusClass(status) {
    return ({ A: "approved", I: "draft", P: "pending", R: "rejected", D: "closed" })[status] || "closed";
  }

  function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return escapeHtml(value);
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
  }

  function formatAction(action) {
    return ({ I: "New tray", U: "Edit" })[action] || "—";
  }

  function renderRecord(row) {
    const main = !row.wbId;
    const typeLabel = main ? "Approved record" : "Workbench request";
    const status = row.status || "";
    const canEdit = main ? status === "A" : ["I", "R", "D"].includes(status);
    const identifier = main ? row.vaultId : row.wbId;
    const kind = main ? "main" : "workbench";
    const locationLine = [row.vaultLocation, row.vaultBranch].filter(Boolean).join(" · ");
    const roomLine = [row.vaultRoom, row.vaultRack, row.vaultShelf].filter(Boolean).join(" / ");
    const actions = [
      canEdit ? `<button class="link-button" data-action="edit" data-kind="${kind}" data-id="${escapeHtml(identifier)}">Edit</button>` : "",
      !main && ["I", "R"].includes(status) ? `<button class="link-button" data-action="close" data-id="${escapeHtml(identifier)}">Close</button>` : "",
      `<button class="link-button" data-action="audit" data-kind="${kind}" data-id="${escapeHtml(identifier)}">History</button>`
    ].filter(Boolean).join("");
    return `<tr>
      <td class="tray-cell"><span class="tray-name">${escapeHtml(row.vaultTrayName)}</span><span class="tray-code">${escapeHtml(row.vaultTrayCode)}</span><span class="record-kind">${typeLabel}</span></td>
      <td class="location-cell"><span class="location-primary">${escapeHtml(locationLine || "—")}</span><span class="location-secondary">${escapeHtml(roomLine || "—")}</span></td>
      <td><span class="status-badge status-${statusClass(status)}">${escapeHtml(statusLabel(status))}</span></td>
      <td><span class="${row.actionType ? "action-badge" : "muted"}">${escapeHtml(formatAction(row.actionType))}</span><span class="location-secondary">v${escapeHtml(row.version ?? "—")}</span></td>
      <td><span class="location-primary">${escapeHtml(row.updatedBy || row.createdBy || "—")}</span><span class="location-secondary">${escapeHtml(formatDate(row.updatedDatetime || row.createdDatetime))}</span></td>
      <td class="row-actions">${actions || '<span class="muted">—</span>'}</td>
    </tr>`;
  }

  function buildSearchRequest() {
    const data = new FormData(form);
    const request = {};
    for (const key of ["vaultTrayCode", "vaultTrayName", "vaultShelf", "vaultRack", "vaultRoom", "vaultBranch", "vaultLocation", "status", "createdBy"]) {
      const value = String(data.get(key) || "").trim();
      if (value) request[key] = value;
    }
    const active = String(data.get("isActive") || "");
    if (active) request.isActive = active === "true";
    state.size = Number(data.get("size")) || 25;
    request.page = state.page;
    request.size = state.size;
    return request;
  }

  async function loadRecords() {
    setLoading("records", true);
    try {
      state.search = buildSearchRequest();
      const page = await fetchJson(`${API}/search`, {
        method: "POST",
        body: JSON.stringify(state.search)
      });
      const items = Array.isArray(page.items) ? page.items : [];
      state.total = Number(page.total) || 0;
      byId("result-count").textContent = `${state.total} ${state.total === 1 ? "record" : "records"}`;
      byId("page-summary").textContent = `Page ${state.page + 1} of ${Math.max(1, Math.ceil(state.total / state.size))}`;
      byId("previous-page").disabled = state.page <= 0;
      byId("next-page").disabled = (state.page + 1) * state.size >= state.total;
      byId("records-body").innerHTML = items.map(renderRecord).join("");
      byId("records-table-wrap").hidden = !items.length;
      byId("records-state").hidden = items.length > 0;
      if (!items.length) {
        const hasFilter = Object.keys(state.search).some((key) => !["page", "size"].includes(key));
        showState("records-state", "No vault records found", hasFilter
          ? "Try changing or clearing the filters to see more results."
          : "Add a vault tray to begin maintaining your storage locations.", "⌕");
      }
    } catch (error) {
      byId("records-body").innerHTML = "";
      byId("records-table-wrap").hidden = true;
      showState("records-state", "Could not load vault records", error.message, "!");
    }
  }

  function showState(id, title, description, symbol) {
    const node = byId(id);
    node.innerHTML = `<span class="state-symbol" aria-hidden="true">${escapeHtml(symbol)}</span><strong>${escapeHtml(title)}</strong><p>${escapeHtml(description)}</p>`;
    node.hidden = false;
  }

  async function loadPending() {
    setLoading("pending", true);
    try {
      const response = await fetchJson(`${API}/pending`);
      state.pending = Array.isArray(response.items) ? response.items : [];
      const count = state.pending.length;
      byId("pending-count").textContent = String(count);
      byId("pending-count").hidden = count === 0;
      byId("queue-total").textContent = String(count);
      byId("pending-body").innerHTML = state.pending.map(renderPending).join("");
      byId("pending-table-wrap").hidden = count === 0;
      byId("pending-state").hidden = count > 0;
      if (!count) showState("pending-state", "Your review queue is clear", "Submitted vault requests will appear here.", "✓");
    } catch (error) {
      byId("pending-body").innerHTML = "";
      byId("pending-table-wrap").hidden = true;
      showState("pending-state", "Could not load review queue", error.message, "!");
    }
  }

  function renderPending(row) {
    const ownRequest = state.user?.username && row.submittedBy
      && state.user.username.toLowerCase() === row.submittedBy.toLowerCase();
    const reviewActions = ownRequest
      ? `<span class="muted">Submitted by you</span>`
      : `<button class="link-button" data-action="approve" data-id="${escapeHtml(row.wbId)}">Approve</button><button class="link-button" data-action="reject" data-id="${escapeHtml(row.wbId)}">Reject</button>`;
    return `<tr>
      <td class="tray-cell"><span class="tray-name">${escapeHtml(row.vaultTrayName)}</span><span class="tray-code">${escapeHtml(row.vaultTrayCode)}</span><span class="record-kind">${escapeHtml(formatAction(row.actionType))}</span></td>
      <td><span class="status-badge status-pending">Pending</span><span class="location-secondary">v${escapeHtml(row.version ?? "—")}</span></td>
      <td>${escapeHtml(row.submittedBy || "—")}</td>
      <td>${escapeHtml(formatDate(row.submittedDatetime))}</td>
      <td>${escapeHtml(row.version ?? "—")}</td>
      <td class="row-actions">${reviewActions}<button class="link-button" data-action="audit" data-kind="workbench" data-id="${escapeHtml(row.wbId)}">History</button></td>
    </tr>`;
  }

  function setFormValues(record = {}) {
    for (const key of ["vaultTrayCode", "vaultTrayName", "vaultShelf", "vaultRack", "vaultRoom", "vaultBranch", "vaultLocation"]) {
      const field = byId(`form-${key}`);
      field.value = record[key] || "";
    }
    byId("form-isActive").checked = record.isActive !== false;
  }

  function getFormValues() {
    const data = new FormData(byId("record-form"));
    return {
      vaultTrayCode: byId("form-vaultTrayCode").value.trim(),
      vaultTrayName: String(data.get("vaultTrayName") || "").trim(),
      vaultShelf: String(data.get("vaultShelf") || ""),
      vaultRack: String(data.get("vaultRack") || ""),
      vaultRoom: String(data.get("vaultRoom") || ""),
      vaultBranch: String(data.get("vaultBranch") || ""),
      vaultLocation: String(data.get("vaultLocation") || ""),
      isActive: byId("form-isActive").checked
    };
  }

  function openEditor(editor) {
    state.editor = editor;
    byId("record-form").reset();
    byId("record-error").hidden = true;
    byId("form-vaultTrayCode").disabled = Boolean(editor.immutableCode);
    byId("form-intro").textContent = editor.kind === "new"
      ? "Enter the tray details. Save a draft or send it for approval."
      : editor.kind === "main"
        ? "Create an edit request for this approved tray. Changes take effect after approval."
        : "Update this workbench request. Save it as a draft or send it back for approval.";
    byId("record-dialog-title").textContent = editor.kind === "new" ? "Add vault tray"
      : editor.kind === "main" ? "Edit approved tray" : "Edit workbench request";
    byId("record-eyebrow").textContent = editor.kind === "main" ? "APPROVED RECORD" : editor.kind === "workbench" ? "WORKBENCH REQUEST" : "NEW VAULT TRAY";
    byId("save-draft").textContent = editor.kind === "new" ? "Save draft" : "Save as draft";
    setFormValues(editor.record || {});
    if (editor.immutableCode) byId("form-vaultTrayCode").value = editor.record.vaultTrayCode;
    byId("record-dialog").showModal();
    byId("form-vaultTrayName").focus();
  }

  async function editRecord(kind, id) {
    try {
      const path = kind === "main" ? `${API}/records/${encodeURIComponent(id)}` : `${API}/workbench/${encodeURIComponent(id)}`;
      const record = await fetchJson(path);
      openEditor({ kind, id, record, immutableCode: true });
    } catch (error) {
      toast(error.message, "error");
    }
  }

  async function saveRecord(submit) {
    const current = state.editor;
    if (!current) return;
    const nativeForm = byId("record-form");
    byId("record-error").hidden = true;
    if (!nativeForm.reportValidity()) return;
    if (!current.immutableCode && !byId("form-vaultTrayCode").value.trim()) return;
    const payload = getFormValues();
    let path;
    let method;
    if (current.kind === "new") {
      path = `${API}/records${submit ? "" : "/draft"}`;
      method = "POST";
    } else if (current.kind === "main") {
      path = submit ? `${API}/records/${encodeURIComponent(current.id)}` : `${API}/records/${encodeURIComponent(current.id)}/draft`;
      method = submit ? "PUT" : "POST";
    } else {
      path = submit ? `${API}/workbench/${encodeURIComponent(current.id)}/submit` : `${API}/workbench/${encodeURIComponent(current.id)}/draft`;
      method = submit ? "POST" : "PUT";
      if (submit) {
        await saveDraftBeforeSubmit(current, payload);
        return;
      }
    }

    setEditorBusy(true, submit ? "Submitting…" : "Saving…");
    try {
      const result = await fetchJson(path, { method, body: JSON.stringify(payload) });
      byId("record-dialog").close();
      toast(submit ? "Vault request submitted for approval." : "Vault draft saved.");
      const wbId = current.kind === "workbench" ? current.id : result?.wbId;
      if (wbId) currentSearchReset();
      await loadRecords();
      await loadPending();
    } catch (error) {
      const message = error.message || "Unable to save the vault request.";
      byId("record-error").textContent = message;
      byId("record-error").hidden = false;
    } finally {
      setEditorBusy(false);
    }
  }

  async function saveDraftBeforeSubmit(current, payload) {
    setEditorBusy(true, "Saving and submitting…");
    try {
      await fetchJson(`${API}/workbench/${encodeURIComponent(current.id)}/draft`, {
        method: "PUT", body: JSON.stringify(payload)
      });
      await fetchJson(`${API}/workbench/${encodeURIComponent(current.id)}/submit`, { method: "POST" });
      byId("record-dialog").close();
      toast("Vault request submitted for approval.");
      await loadRecords();
      await loadPending();
    } catch (error) {
      byId("record-error").textContent = error.message;
      byId("record-error").hidden = false;
    } finally {
      setEditorBusy(false);
    }
  }

  function currentSearchReset() {
    state.page = 0;
  }

  function setEditorBusy(busy, buttonText = "") {
    state.busy = busy;
    byId("save-draft").disabled = busy;
    byId("submit-record").disabled = busy;
    if (busy) {
      byId("save-draft").dataset.label = byId("save-draft").textContent;
      byId("submit-record").dataset.label = byId("submit-record").textContent;
      byId("submit-record").textContent = buttonText;
    } else {
      byId("save-draft").textContent = byId("save-draft").dataset.label || (state.editor?.kind === "new" ? "Save draft" : "Save as draft");
      byId("submit-record").textContent = byId("submit-record").dataset.label || "Submit for approval";
    }
  }

  function openConfirmation(title, message, label, callback, danger = false) {
    byId("confirm-title").textContent = title;
    byId("confirm-message").textContent = message;
    const button = byId("confirm-action");
    button.textContent = label;
    button.className = `button ${danger ? "button-danger" : "button-primary"}`;
    state.confirmCallback = callback;
    byId("confirm-dialog").showModal();
    button.focus();
  }

  async function approveRequest(wbId) {
    openConfirmation("Approve vault request", "This change will become the approved vault record. Continue?", "Approve request", async () => {
      await fetchJson(`${API}/workbench/${encodeURIComponent(wbId)}/approve`, { method: "POST" });
      toast("Vault request approved.");
      await Promise.all([loadRecords(), loadPending()]);
    });
  }

  function openReject(wbId) {
    byId("reject-form").reset();
    byId("rejection-error").hidden = true;
    byId("reject-form").dataset.wbId = wbId;
    byId("reject-dialog").showModal();
    byId("rejection-reason").focus();
  }

  async function rejectRequest(wbId, reason) {
    await fetchJson(`${API}/workbench/${encodeURIComponent(wbId)}/reject`, {
      method: "POST", body: JSON.stringify({ reason })
    });
    byId("reject-dialog").close();
    toast("Vault request rejected.");
    await Promise.all([loadRecords(), loadPending()]);
  }

  async function showAudit(kind, id) {
    const endpoint = kind === "main" ? `${API}/records/${encodeURIComponent(id)}/audit` : `${API}/workbench/${encodeURIComponent(id)}/audit`;
    const title = kind === "main" ? `Vault record ${id}` : "Workbench request history";
    byId("audit-title").textContent = `Audit history · ${title}`;
    byId("audit-content").innerHTML = `<div class="state-card"><span class="skeleton"></span></div>`;
    byId("audit-dialog").showModal();
    try {
      const items = await fetchJson(endpoint);
      if (!Array.isArray(items) || !items.length) {
        byId("audit-content").innerHTML = `<div class="state-card"><span class="state-symbol" aria-hidden="true">◷</span><strong>No audit events yet</strong><p>Activity will appear here as this record moves through its workflow.</p></div>`;
        return;
      }
      byId("audit-content").innerHTML = `<div class="audit-list">${items.map((item) => `
        <article class="audit-item">
          <span class="audit-dot" aria-hidden="true"></span>
          <div>
            <div class="audit-title"><span>${escapeHtml(item.actionDescription || item.actionType || "Activity")}</span><time class="audit-date">${escapeHtml(formatDate(item.actionAt))}</time></div>
            <div class="audit-meta">${escapeHtml(item.actionBy || "Unknown actor")} · ${escapeHtml(item.actionType || "—")}${item.workflowCode ? ` · ${escapeHtml(item.workflowCode)}` : ""}</div>
            ${item.remarks ? `<div class="audit-remarks">${escapeHtml(item.remarks)}</div>` : ""}
          </div>
        </article>`).join("")}</div>`;
    } catch (error) {
      byId("audit-content").innerHTML = `<div class="alert alert-error" role="alert">${escapeHtml(error.message)}</div>`;
    }
  }

  async function closeWorkbench(wbId) {
    openConfirmation("Close workbench request", "This will mark the draft as closed. You can reopen it later by editing or submitting it.", "Close request", async () => {
      await fetchJson(`${API}/workbench/${encodeURIComponent(wbId)}`, { method: "DELETE" });
      toast("Workbench request closed.");
      await Promise.all([loadRecords(), loadPending()]);
    }, true);
  }

  function showReviewTab(review) {
    byId("records-panel").hidden = review;
    byId("review-panel").hidden = !review;
    byId("records-tab").classList.toggle("is-active", !review);
    byId("records-tab").setAttribute("aria-selected", String(!review));
    byId("review-tab").classList.toggle("is-active", review);
    byId("review-tab").setAttribute("aria-selected", String(review));
    if (review) loadPending();
  }

  document.addEventListener("click", async (event) => {
    const close = event.target.closest("[data-close]");
    if (close) {
      byId(close.dataset.close).close();
      return;
    }
    const action = event.target.closest("[data-action]");
    if (action) {
      const { action: name, id, kind } = action.dataset;
      try {
        if (name === "edit") await editRecord(kind, id);
        if (name === "audit") await showAudit(kind, id);
        if (name === "approve") await approveRequest(id);
        if (name === "reject") openReject(id);
        if (name === "close") await closeWorkbench(id);
      } catch (error) {
        toast(error.message, "error");
      }
      return;
    }
  });

  byId("new-record").addEventListener("click", () => openEditor({ kind: "new", record: {} }));
  byId("records-tab").addEventListener("click", () => showReviewTab(false));
  byId("review-tab").addEventListener("click", () => showReviewTab(true));
  byId("refresh-records").addEventListener("click", loadRecords);
  byId("refresh-pending").addEventListener("click", loadPending);
  byId("search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    state.page = 0;
    loadRecords();
  });
  byId("search-form").addEventListener("reset", () => {
    window.setTimeout(() => {
      state.page = 0;
      loadRecords();
    });
  });
  byId("clear-search").addEventListener("click", () => form.reset());
  byId("record-form").addEventListener("submit", (event) => event.preventDefault());
  byId("previous-page").addEventListener("click", () => {
    if (state.page > 0) {
      state.page -= 1;
      loadRecords();
    }
  });
  byId("next-page").addEventListener("click", () => {
    if ((state.page + 1) * state.size < state.total) {
      state.page += 1;
      loadRecords();
    }
  });
  byId("save-draft").addEventListener("click", () => saveRecord(false));
  byId("submit-record").addEventListener("click", () => saveRecord(true));
  byId("confirm-action").addEventListener("click", async () => {
    const callback = state.confirmCallback;
    state.confirmCallback = null;
    byId("confirm-dialog").close();
    if (callback) {
      try { await callback(); } catch (error) { toast(error.message, "error"); }
    }
  });
  byId("reject-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const reason = byId("rejection-reason").value.trim();
    if (!reason) {
      byId("rejection-error").hidden = false;
      byId("rejection-reason").focus();
      return;
    }
    byId("rejection-error").hidden = true;
    try {
      await rejectRequest(event.currentTarget.dataset.wbId, reason);
    } catch (error) {
      toast(error.message, "error");
    }
  });
  document.querySelectorAll("dialog").forEach((dialog) => {
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) dialog.close();
    });
  });

  async function initialize() {
    try {
      await loadCsrf();
      await loadSession();
      await loadDropdowns();
      await Promise.all([loadRecords(), loadPending()]);
    } catch (error) {
      showAlert(error.message, "error");
    }
  }

  initialize();
})();
