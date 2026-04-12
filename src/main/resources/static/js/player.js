const playerState = {
    userId: null,
    tracks: [],
    filteredTracks: [],
    currentTrack: null,
    audioEl: null,
};

const $ = (id) => document.getElementById(id);

function setStatus(text) {
    $("playerStatus").textContent = text;
}

function showTrackDetails(track) {
    const details = $("trackDetails");
    if (!track) {
        details.innerHTML = "Выбери трек, чтобы увидеть его свойства и признаки.";
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
        throw new Error(`GET ${url} failed: ${r.status}`);
    }
    return r.json();
}

async function apiPost(url, body) {
    const r = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });

    if (!r.ok) {
        const text = await r.text();
        throw new Error(`POST ${url} failed: ${r.status} ${text}`);
    }
    return r.json();
}

async function createUser() {
    const name = $("createUserName").value.trim();
    if (!name) {
        setStatus("Введи имя пользователя");
        return;
    }

    const user = await apiPost("/api/v1/users", { displayName: name });
    playerState.userId = user.id;
    $("currentUserId").value = user.id;
    setStatus(`Пользователь ${user.displayName} создан`);
}

async function loadTracks() {
    const tracks = await apiGet("/api/v1/tracks");
    playerState.tracks = tracks;
    applyTrackFilter();
}

function applyTrackFilter() {
    const query = $("trackSearch").value.trim().toLowerCase();
    playerState.filteredTracks = playerState.tracks.filter(track => {
        const haystack = `${track.title || ""} ${track.artist || ""} ${track.album || ""}`.toLowerCase();
        return haystack.includes(query);
    });
    renderTracksList();
}

function renderTracksList() {
    const container = $("tracksList");
    if (!playerState.filteredTracks.length) {
        container.innerHTML = `<div class="empty-state-small">Треки не найдены.</div>`;
        return;
    }

    container.innerHTML = playerState.filteredTracks.map(track => `
        <button class="track-card" data-track-id="${track.id}">
            <div class="track-card-title">${escapeHtml(track.title || "Без названия")}</div>
            <div class="track-card-meta">${escapeHtml(track.artist || "Неизвестный исполнитель")}</div>
        </button>
    `).join("");

    container.querySelectorAll(".track-card").forEach(btn => {
        btn.addEventListener("click", () => selectTrack(btn.dataset.trackId));
    });
}

async function selectTrack(trackId) {
    const track = await apiGet(`/api/v1/tracks/${trackId}`);
    playerState.currentTrack = track;

    $("currentTrackTitle").textContent = track.title || "Без названия";
    $("currentTrackMeta").textContent = `${track.artist || "Неизвестный исполнитель"} • ${track.album || "Без альбома"}`;

    const streamUrl = `/api/v1/tracks/${track.id}/stream`;
    playerState.audioEl.src = streamUrl;
    showTrackDetails(track);
    setStatus(`Выбран трек: ${track.title || track.id}`);
}

async function sendInteraction(type) {
    const userId = $("currentUserId").value.trim();
    if (!userId) {
        setStatus("Сначала создай пользователя или вставь userId");
        return;
    }
    if (!playerState.currentTrack) {
        setStatus("Сначала выбери трек");
        return;
    }

    try {
        await apiPost("/api/v1/interactions", {
            userId,
            trackId: playerState.currentTrack.id,
            type,
            positionMs: Math.floor((playerState.audioEl.currentTime || 0) * 1000)
        });
        setStatus(`Событие ${type} отправлено`);
    } catch (e) {
        setStatus(`Ошибка interaction: ${e.message}`);
    }
}

async function sendRating(value) {
    const userId = $("currentUserId").value.trim();
    if (!userId) {
        setStatus("Сначала создай пользователя или вставь userId");
        return;
    }
    if (!playerState.currentTrack) {
        setStatus("Сначала выбери трек");
        return;
    }

    try {
        await apiPost("/api/v1/ratings", {
            userId,
            trackId: playerState.currentTrack.id,
            value: Number(value)
        });
        setStatus(`Оценка ${value} сохранена`);
    } catch (e) {
        setStatus(`Ошибка rating: ${e.message}`);
    }
}

async function loadRecommendations() {
    const userId = $("currentUserId").value.trim();
    if (!userId) {
        setStatus("Сначала создай пользователя или вставь userId");
        return;
    }

    try {
        const response = await apiGet(`/api/v1/recommendations?userId=${encodeURIComponent(userId)}&limit=20`);
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
                <div class="recommendation-title">${escapeHtml(item.trackId)}</div>
                <div class="recommendation-meta">score: ${item.score ?? "—"} • model: ${escapeHtml(item.modelVersion || "—")}</div>
            </div>
            <span class="recommendation-rank">#${item.rank ?? "?"}</span>
        </button>
    `).join("");

    container.querySelectorAll(".recommendation-item").forEach(btn => {
        btn.addEventListener("click", () => selectTrack(btn.dataset.trackId));
    });
}

function bindEvents() {
    $("createUserBtn").addEventListener("click", async () => {
        try {
            await createUser();
            await loadRecommendations();
        } catch (e) {
            setStatus(`Ошибка создания пользователя: ${e.message}`);
        }
    });

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
        if (!playerState.audioEl.src) return setStatus("Сначала выбери трек");
        await playerState.audioEl.play();
        await sendInteraction("PLAY");
    });

    $("pauseBtn").addEventListener("click", async () => {
        playerState.audioEl.pause();
        await sendInteraction("PAUSE");
    });

    $("skipBtn").addEventListener("click", async () => {
        playerState.audioEl.pause();
        playerState.audioEl.currentTime = 0;
        await sendInteraction("SKIP");
    });

    $("replayBtn").addEventListener("click", async () => {
        playerState.audioEl.currentTime = 0;
        await playerState.audioEl.play();
        await sendInteraction("REPLAY");
    });

    document.querySelectorAll("[data-rating]").forEach(btn => {
        btn.addEventListener("click", () => sendRating(btn.dataset.rating));
    });
}

async function init() {
    playerState.audioEl = $("audioPlayer");
    bindEvents();
    try {
        await loadTracks();
    } catch (e) {
        setStatus(`Ошибка инициализации: ${e.message}`);
    }
}

document.addEventListener("DOMContentLoaded", init);
