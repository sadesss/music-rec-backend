const SESSION_KEY = "musicrec_session";
let session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");

const admin$ = (id) => document.getElementById(id);

function setAdminStatus(text) {
    const el = admin$("adminStatus");
    if (el) {
        el.textContent = text;
    }
}

function clearSessionAndRedirect() {
    localStorage.removeItem(SESSION_KEY);
    window.location.href = "/auth";
}

function logAdmin(message, isError = false) {
    const container = admin$("adminLog");
    if (!container) return;

    const row = document.createElement("div");
    row.className = `terminal-line ${isError ? "error" : "ok"}`;
    row.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;

    if (container.children.length === 1 && container.textContent.trim() === "Лог пуст.") {
        container.innerHTML = "";
    }

    container.prepend(row);
    setAdminStatus(message);
}

function escapeHtml(str) {
    return String(str ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

async function authFetch(url, options = {}) {
    const headers = {
        ...(options.headers || {})
    };

    if (session?.sessionToken) {
        headers["X-Session-Token"] = session.sessionToken;
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (response.status === 401 || response.status === 403) {
        clearSessionAndRedirect();
        throw new Error("Сессия недействительна или доступ запрещён");
    }

    if (!response.ok) {
        throw new Error(await response.text());
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        return response.json();
    }

    return response.text();
}

async function ensureAdmin() {
    if (!session?.sessionToken) {
        clearSessionAndRedirect();
        throw new Error("Нет активной сессии");
    }

    const me = await authFetch("/api/v1/auth/me", { method: "GET" });

    if (me.role !== "ADMIN") {
        clearSessionAndRedirect();
        throw new Error("Требуется роль ADMIN");
    }

    const nameEl = admin$("adminUserName");
    if (nameEl) {
        nameEl.textContent = `${me.displayName} (${me.email})`;
    }

    return me;
}

async function apiGet(url) {
    return authFetch(url, { method: "GET" });
}

async function apiPostJson(url, body) {
    return authFetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
}

async function importJamendo() {
    const body = {
        datasetRoot: admin$("datasetRoot").value.trim(),
        audioRoot: admin$("audioRoot").value.trim(),
        limit: Number(admin$("importLimit").value || 100)
    };

    const response = await apiPostJson("/api/admin/v1/import/jamendo", body);
    logAdmin(`Импорт завершён: imported=${response.importedCount}, skipped=${response.skippedCount}`);
    await loadTracks();
}

async function uploadTrack() {
    const file = admin$("trackFile").files[0];
    if (!file) {
        throw new Error("Выбери mp3 файл");
    }

    const metadata = {
        title: admin$("trackTitle").value.trim(),
        artist: admin$("trackArtist").value.trim() || null,
        album: admin$("trackAlbum").value.trim() || null,
        genre: admin$("trackGenre").value.trim() || null,
        durationSeconds: admin$("trackDuration").value ? Number(admin$("trackDuration").value) : null,
        metadataText: JSON.stringify({ source: "admin-ui" })
    };

    if (!metadata.title) {
        throw new Error("Укажи название трека");
    }

    const fd = new FormData();
    fd.append("file", file);
    fd.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));

    const response = await authFetch("/api/admin/v1/tracks/upload", {
        method: "POST",
        body: fd
    });

    logAdmin(`Трек загружен: ${response.trackId}`);
    await loadTracks();
}

async function analyzeTrack() {
    const trackId = admin$("analyzeTrackId").value.trim();
    if (!trackId) {
        throw new Error("Укажи trackId для анализа");
    }

    const response = await authFetch(`/api/admin/v1/tracks/${encodeURIComponent(trackId)}/analyze`, {
        method: "POST"
    });

    logAdmin(`Анализ завершён: features=${response.featuresUpserted}`);
}

async function trainModel() {
    const notes = admin$("trainNotes").value.trim();
    const response = await apiPostJson("/api/admin/v1/train", { notes });
    logAdmin(`Обучение завершено. modelVersion=${response.modelVersion}`);
    renderMetrics(response);
}

async function loadMetrics() {
    const response = await apiGet("/api/admin/v1/metrics");
    renderMetrics(response);
    logAdmin("Метрики обновлены");
}

function renderMetrics(metricsResponse) {
    const panel = admin$("metricsPanel");
    const metrics = metricsResponse.metrics || {};
    const items = Object.entries(metrics);

    panel.innerHTML = `
        <div class="detail-item"><div class="detail-key">Model version</div><div class="detail-value">${escapeHtml(metricsResponse.modelVersion || "unknown")}</div></div>
        <div class="detail-item"><div class="detail-key">Metrics JSON</div><div class="detail-value">${escapeHtml(metricsResponse.metricsJsonPath || "—")}</div></div>
        ${items.map(([k, v]) => `
            <div class="detail-item">
                <div class="detail-key">${escapeHtml(k)}</div>
                <div class="detail-value">${escapeHtml(String(v))}</div>
            </div>
        `).join("") || `<div class="detail-item"><div class="detail-key">Состояние</div><div class="detail-value">Метрики пока отсутствуют.</div></div>`}
    `;
}

async function loadTracks() {
    const tracks = await fetch("/api/v1/tracks").then(async r => {
        if (!r.ok) throw new Error(await r.text());
        return r.json();
    });

    const list = admin$("adminTracksList");

    if (!tracks.length) {
        list.innerHTML = `<div class="terminal-line">Треков пока нет.</div>`;
        return;
    }

    list.innerHTML = tracks.map(track => `
        <div class="track-card static-track-card">
            <div class="track-card-title">${escapeHtml(track.title || "Без названия")}</div>
            <div class="track-card-meta">${escapeHtml(track.artist || "Неизвестный исполнитель")}</div>
            <div class="track-card-submeta">${escapeHtml(track.album || "Без альбома")}</div>
            <div class="track-card-submeta">${escapeHtml(track.id)}</div>
        </div>
    `).join("");
}

async function logout() {
    try {
        await authFetch("/api/v1/auth/logout", { method: "POST" });
    } catch (_) {
        // ignore
    } finally {
        clearSessionAndRedirect();
    }
}

function bindAdminEvents() {
    admin$("importJamendoBtn").addEventListener("click", async () => {
        try {
            await importJamendo();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("uploadTrackBtn").addEventListener("click", async () => {
        try {
            await uploadTrack();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("analyzeTrackBtn").addEventListener("click", async () => {
        try {
            await analyzeTrack();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("trainModelBtn").addEventListener("click", async () => {
        try {
            await trainModel();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("loadMetricsBtn").addEventListener("click", async () => {
        try {
            await loadMetrics();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("loadTracksBtn").addEventListener("click", async () => {
        try {
            await loadTracks();
            logAdmin("Список треков обновлён");
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("logoutBtn").addEventListener("click", logout);
}

document.addEventListener("DOMContentLoaded", async () => {
    bindAdminEvents();

    try {
        await ensureAdmin();
        await loadTracks();
        await loadMetrics();
        logAdmin("Панель администратора инициализирована");
    } catch (e) {
        logAdmin(e.message, true);
    }
});