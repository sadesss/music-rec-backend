const SESSION_KEY = "musicrec_session";

const state = {
    session: JSON.parse(localStorage.getItem(SESSION_KEY) || "null"),
    me: null,
    tracks: [],
    filteredTracks: [],
    currentTrack: null,
    audioEl: null
};

const $ = (id) => document.getElementById(id);

function setStatus(text) {
    $("playerStatus").textContent = text;
}

function clearSessionAndRedirect() {
    localStorage.removeItem(SESSION_KEY);
    window.location.href = "/auth";
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

    if (state.session?.sessionToken) {
        headers["X-Session-Token"] = state.session.sessionToken;
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (response.status === 401 || response.status === 403) {
        const text = await response.text();
        console.error("AUTH/API ERROR", url, response.status, text);
        throw new Error(`${url} -> ${response.status}: ${text}`);
    }

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `${options.method || "GET"} ${url} failed: ${response.status}`);
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

async function apiGet(url) {
    const r = await fetch(url);
    if (!r.ok) {
        throw new Error(`GET ${url} failed: ${r.status}`);
    }
    return r.json();
}

async function ensureSession() {
    if (!state.session?.sessionToken) {
        clearSessionAndRedirect();
        throw new Error("Нет активной сессии");
    }

    const me = await authFetch("/api/v1/auth/me", {
        method: "GET"
    });

    if (me.role !== "USER" && me.role !== "ADMIN") {
        clearSessionAndRedirect();
        throw new Error("Недостаточно прав для пользовательской страницы");
    }

    state.me = me;
    $("currentUserName").textContent = `${me.displayName} (${me.email})`;
}

async function logout() {
    try {
        await authFetch("/api/v1/auth/logout", {
            method: "POST"
        });
    } catch (_) {
        // ignore
    } finally {
        clearSessionAndRedirect();
    }
}

function showTrackDetails(track) {
    const details = $("trackDetails");
    if (!track) {
        details.innerHTML = `<div class="empty-state-small">Выбери трек, чтобы увидеть его свойства и признаки.</div>`;
        return;
    }

    const features = track.features || {};
    const featuresHtml = Object.keys(features).length
        ? Object.entries(features).map(([k, v]) => `
            <div class="detail-item">
                <div class="detail-key">${escapeHtml(k)}</div>
                <div class="detail-value">${escapeHtml(String(v))}</div>
            </div>
        `).join("")
        : `<div class="empty-state-small">Признаки ещё не рассчитаны.</div>`;

    details.innerHTML = `
        <div class="detail-item"><div class="detail-key">ID</div><div class="detail-value">${escapeHtml(track.id)}</div></div>
        <div class="detail-item"><div class="detail-key">Название</div><div class="detail-value">${escapeHtml(track.title || "—")}</div></div>
        <div class="detail-item"><div class="detail-key">Исполнитель</div><div class="detail-value">${escapeHtml(track.artist || "—")}</div></div>
        <div class="detail-item"><div class="detail-key">Альбом</div><div class="detail-value">${escapeHtml(track.album || "—")}</div></div>
        <div class="detail-item"><div class="detail-key">Жанр</div><div class="detail-value">${escapeHtml(track.originalGenre || "—")}</div></div>
        <div class="detail-item"><div class="detail-key">Длительность</div><div class="detail-value">${track.durationSeconds ?? "—"}</div></div>
        ${featuresHtml}
    `;
}

async function loadTracks() {
    const tracks = await apiGet("/api/v1/tracks");
    state.tracks = tracks;
    applyTrackFilter();
}

function applyTrackFilter() {
    const query = $("trackSearch").value.trim().toLowerCase();

    state.filteredTracks = state.tracks.filter(track => {
        const haystack = `${track.title || ""} ${track.artist || ""} ${track.album || ""}`.toLowerCase();
        return haystack.includes(query);
    });

    renderTracksList();
}

function renderTracksList() {
    const container = $("tracksList");

    if (!state.filteredTracks.length) {
        container.innerHTML = `<div class="empty-state-small">Треки не найдены.</div>`;
        return;
    }

    container.innerHTML = state.filteredTracks.map(track => `
        <button class="track-card" data-track-id="${track.id}">
            <div class="track-card-title">${escapeHtml(track.title || "Без названия")}</div>
            <div class="track-card-meta">${escapeHtml(track.artist || "Неизвестный исполнитель")}</div>
            <div class="track-card-submeta">${escapeHtml(track.album || "Без альбома")}</div>
        </button>
    `).join("");

    container.querySelectorAll(".track-card").forEach(btn => {
        btn.addEventListener("click", () => selectTrack(btn.dataset.trackId));
    });
}

async function selectTrack(trackId) {
    const track = await apiGet(`/api/v1/tracks/${trackId}`);
    state.currentTrack = track;

    $("currentTrackTitle").textContent = track.title || "Без названия";
    $("currentTrackMeta").textContent = `${track.artist || "Неизвестный исполнитель"} • ${track.album || "Без альбома"}`;

    state.audioEl.src = `/api/v1/tracks/${track.id}/stream`;
    showTrackDetails(track);
    setStatus(`Выбран трек: ${track.title || track.id}`);
}

async function sendInteraction(type) {
    if (!state.currentTrack) {
        setStatus("Сначала выбери трек");
        return;
    }

    try {
        await authFetch("/api/v1/interactions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                trackId: state.currentTrack.id,
                type,
                positionMs: Math.floor((state.audioEl.currentTime || 0) * 1000)
            })
        });

        setStatus(`Событие ${type} отправлено`);
    } catch (e) {
        setStatus(`Ошибка interaction: ${e.message}`);
    }
}

async function sendRating(value) {
    if (!state.currentTrack) {
        setStatus("Сначала выбери трек");
        return;
    }

    try {
        await authFetch("/api/v1/ratings", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                trackId: state.currentTrack.id,
                value: Number(value)
            })
        });

        setStatus(`Оценка ${value} сохранена`);
        await loadRecommendations();
    } catch (e) {
        setStatus(`Ошибка rating: ${e.message}`);
    }
}

async function loadRecommendations() {
    try {
        const response = await authFetch("/api/v1/recommendations/me?limit=20", {
            method: "GET"
        });

        renderRecommendations(response.items || []);
        setStatus("Рекомендации обновлены");
    } catch (e) {
        setStatus(`Ошибка рекомендаций: ${e.message}`);
    }
}

function renderRecommendations(items) {
    const container = $("recommendationsList");

    if (!items.length) {
        container.innerHTML = `<div class="empty-state-small">Рекомендаций пока нет.</div>`;
        return;
    }

    container.innerHTML = items.map(item => `
        <button class="recommendation-item" data-track-id="${item.trackId}">
            <div>
                <div class="recommendation-title">${escapeHtml(item.title || item.trackId)}</div>
                <div class="recommendation-meta">
                    ${escapeHtml(item.artist || "Неизвестный исполнитель")}
                    • score: ${item.score ?? "—"}
                    • model: ${escapeHtml(item.modelVersion || "—")}
                </div>
            </div>
            <span class="recommendation-rank">#${item.rank ?? "?"}</span>
        </button>
    `).join("");

    container.querySelectorAll(".recommendation-item").forEach(btn => {
        btn.addEventListener("click", () => selectTrack(btn.dataset.trackId));
    });
}

function bindEvents() {
    $("reloadTracksBtn").addEventListener("click", async () => {
        try {
            await loadTracks();
            setStatus("Список треков обновлён");
        } catch (e) {
            setStatus(`Ошибка загрузки треков: ${e.message}`);
        }
    });

    $("trackSearch").addEventListener("input", applyTrackFilter);
    $("loadRecommendationsBtn").addEventListener("click", loadRecommendations);

    $("playBtn").addEventListener("click", async () => {
        if (!state.audioEl.src) {
            setStatus("Сначала выбери трек");
            return;
        }
        await state.audioEl.play();
        await sendInteraction("PLAY");
    });

    $("pauseBtn").addEventListener("click", async () => {
        state.audioEl.pause();
        await sendInteraction("PAUSE");
    });

    $("skipBtn").addEventListener("click", async () => {
        state.audioEl.pause();
        state.audioEl.currentTime = 0;
        await sendInteraction("SKIP");
        await loadRecommendations();
    });

    $("replayBtn").addEventListener("click", async () => {
        if (!state.audioEl.src) {
            setStatus("Сначала выбери трек");
            return;
        }
        state.audioEl.currentTime = 0;
        await state.audioEl.play();
        await sendInteraction("PLAY");
    });

    $("logoutBtn").addEventListener("click", logout);

    document.querySelectorAll("[data-rating]").forEach(btn => {
        btn.addEventListener("click", () => sendRating(btn.dataset.rating));
    });

    state.audioEl.addEventListener("ended", async () => {
        await sendInteraction("FINISH");
        await loadRecommendations();
    });
}

async function init() {
    state.audioEl = $("audioPlayer");
    bindEvents();

    try {
        await ensureSession();
        await loadTracks();
        await loadRecommendations();
    } catch (e) {
        setStatus(`Ошибка инициализации: ${e.message}`);
    }
}

document.addEventListener("DOMContentLoaded", init);