const admin$ = (id) => document.getElementById(id);

function logAdmin(message, isError = false) {
    const row = document.createElement("div");
    row.className = `terminal-line ${isError ? "error" : "ok"}`;
    row.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
    admin$("adminLog").prepend(row);
}

function escapeHtml(str) {
    return str
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

async function apiGet(url) {
    const r = await fetch(url);
    if (!r.ok) {
        throw new Error(`GET ${url} failed: ${r.status} ${await r.text()}`);
    }
    return r.json();
}

async function apiPostJson(url, body) {
    const r = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });

    if (!r.ok) {
        throw new Error(`POST ${url} failed: ${r.status} ${await r.text()}`);
    }
    return r.json();
}

async function importJamendo() {
    const body = {
        datasetRoot: admin$("datasetRoot").value.trim(),
        audioRoot: admin$("audioRoot").value.trim(),
        limit: Number(admin$("importLimit").value)
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

    const r = await fetch("/api/admin/v1/tracks/upload", {
        method: "POST",
        body: fd
    });

    if (!r.ok) {
        throw new Error(`Upload failed: ${r.status} ${await r.text()}`);
    }

    const response = await r.json();
    logAdmin(`Трек загружен: ${response.trackId}`);
    await loadTracks();
}

async function analyzeTrack() {
    const trackId = admin$("analyzeTrackId").value.trim();
    if (!trackId) {
        throw new Error("Укажи trackId для анализа");
    }

    const r = await fetch(`/api/admin/v1/tracks/${encodeURIComponent(trackId)}/analyze`, { method: "POST" });
    if (!r.ok) {
        throw new Error(`Analyze failed: ${r.status} ${await r.text()}`);
    }

    const response = await r.json();
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
        `).join("") || `<div class="empty-state-small">Метрики пока отсутствуют.</div>`}
    `;
}

async function loadTracks() {
    const tracks = await apiGet("/api/v1/tracks");
    const list = admin$("adminTracksList");

    if (!tracks.length) {
        list.innerHTML = `<div class="empty-state-small">Треков пока нет.</div>`;
        return;
    }

    list.innerHTML = tracks.map(track => `
        <div class="track-card static-track-card">
            <div class="track-card-title">${escapeHtml(track.title || "Без названия")}</div>
            <div class="track-card-meta">${escapeHtml(track.artist || "Неизвестный исполнитель")}</div>
            <div class="track-card-submeta">${escapeHtml(track.id)}</div>
        </div>
    `).join("");
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
}

document.addEventListener("DOMContentLoaded", async () => {
    bindAdminEvents();
    try {
        await loadTracks();
        await loadMetrics();
    } catch (e) {
        logAdmin(e.message, true);
    }
});
