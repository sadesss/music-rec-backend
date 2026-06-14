const SESSION_KEY = "musicrec_session";

const state = {
    session: JSON.parse(localStorage.getItem(SESSION_KEY) || "null"),
    me: null,
    tracks: [],
    filteredTracks: [],
    recommendations: [],
    currentRecommendationIndex: -1,
    currentTrack: null,
    audioEl: null,
    suppressPauseInteraction: false
};

const $ = (id) => document.getElementById(id);

const INTERACTION_LABELS = {
    PLAY: "Воспроизведение",
    PAUSE: "Пауза",
    SKIP: "Пропуск",
    FINISH: "Завершение",
    LIKE: "Мне нравится",
    DISLIKE: "Мне не нравится"
};

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

function formatDuration(seconds) {
    const value = Number(seconds);
    if (!Number.isFinite(value) || value <= 0) {
        return "—";
    }

    const rounded = Math.round(value);
    const minutes = Math.floor(rounded / 60);
    const restSeconds = String(rounded % 60).padStart(2, "0");
    return `${minutes}:${restSeconds}`;
}

function getTrackGenre(track) {
    return track.originalGenre || track.genre || "—";
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
        details.innerHTML = `<div class="empty-state-small">Выбери трек, чтобы увидеть основные данные.</div>`;
        return;
    }

    details.innerHTML = `
        <div class="detail-item"><div class="detail-key">Название</div><div class="detail-value">${escapeHtml(track.title || "—")}</div></div>
        <div class="detail-item"><div class="detail-key">Исполнитель</div><div class="detail-value">${escapeHtml(track.artist || "—")}</div></div>
        <div class="detail-item"><div class="detail-key">Альбом</div><div class="detail-value">${escapeHtml(track.album || "—")}</div></div>
        <div class="detail-item"><div class="detail-key">Жанр</div><div class="detail-value">${escapeHtml(getTrackGenre(track))}</div></div>
        <div class="detail-item"><div class="detail-key">Длительность</div><div class="detail-value">${formatDuration(track.durationSeconds)}</div></div>
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
        btn.addEventListener("click", () => {
            state.currentRecommendationIndex = -1;
            selectTrack(btn.dataset.trackId);
        });
    });
}

async function selectTrack(trackId, options = {}) {
    const track = await apiGet(`/api/v1/tracks/${trackId}`);
    state.currentTrack = track;

    if (typeof options.recommendationIndex === "number") {
        state.currentRecommendationIndex = options.recommendationIndex;
    }

    $("currentTrackTitle").textContent = track.title || "Без названия";
    $("currentTrackMeta").textContent = `${track.artist || "Неизвестный исполнитель"} • ${track.album || "Без альбома"}`;

    state.audioEl.src = `/api/v1/tracks/${track.id}/stream`;
    showTrackDetails(track);
    setStatus(`Выбран трек: ${track.title || track.id}`);
}

async function selectAndPlayTrack(trackId, options = {}) {
    await selectTrack(trackId, options);
    await state.audioEl.play();
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

        setStatus(`Событие «${INTERACTION_LABELS[type] || type}» отправлено`);
    } catch (e) {
        setStatus(`Ошибка отправки события: ${e.message}`);
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
    } catch (e) {
        setStatus(`Ошибка сохранения оценки: ${e.message}`);
    }
}

async function loadRecommendations() {
    try {
        const response = await authFetch("/api/v1/recommendations/me?limit=20", {
            method: "GET"
        });

        state.recommendations = response.items || [];
        renderRecommendations(state.recommendations);
        setStatus(`Рекомендации загружены: ${state.recommendations.length}`);
    } catch (e) {
        setStatus(`Ошибка загрузки рекомендаций: ${e.message}`);
    }
}

function renderRecommendations(items) {
    const container = $("recommendationsList");

    if (!items.length) {
        container.innerHTML = `<div class="empty-state-small">Рекомендаций пока нет.</div>`;
        return;
    }

    container.innerHTML = items.map((item, index) => `
        <button class="recommendation-item" data-track-id="${item.trackId}" data-index="${index}">
            <div>
                <div class="recommendation-title">${escapeHtml(item.title || item.trackId)}</div>
                <div class="recommendation-meta">
                    ${escapeHtml(item.artist || "Неизвестный исполнитель")}
                    ${item.album ? ` • ${escapeHtml(item.album)}` : ""}
                </div>
            </div>
        </button>
    `).join("");

    container.querySelectorAll(".recommendation-item").forEach(btn => {
        btn.addEventListener("click", () => {
            selectTrack(btn.dataset.trackId, {
                recommendationIndex: Number(btn.dataset.index)
            });
        });
    });
}

function findCurrentRecommendationIndex() {
    if (state.currentRecommendationIndex >= 0) {
        return state.currentRecommendationIndex;
    }

    if (!state.currentTrack) {
        return -1;
    }

    return state.recommendations.findIndex(item => item.trackId === state.currentTrack.id);
}

async function playNextRecommendation() {
    if (!state.recommendations.length) {
        await loadRecommendations();
    }

    if (!state.recommendations.length) {
        setStatus("Нет следующего трека в потоке рекомендаций");
        return;
    }

    const currentIndex = findCurrentRecommendationIndex();
    let nextIndex = currentIndex + 1;

    if (nextIndex < 0) {
        nextIndex = 0;
    }

    if (nextIndex >= state.recommendations.length) {
        await loadRecommendations();

        if (!state.recommendations.length) {
            setStatus("Нет следующего трека в потоке рекомендаций");
            return;
        }

        nextIndex = 0;

        if (state.currentTrack && state.recommendations.length > 1 && state.recommendations[0].trackId === state.currentTrack.id) {
            nextIndex = 1;
        }
    }

    const nextItem = state.recommendations[nextIndex];

    if (!nextItem?.trackId) {
        setStatus("Следующий трек в потоке не найден");
        return;
    }

    state.currentRecommendationIndex = nextIndex;
    await selectAndPlayTrack(nextItem.trackId, {
        recommendationIndex: nextIndex
    });

    setStatus(`Включен следующий трек из потока: ${nextItem.title || nextItem.trackId}`);
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

    $("skipBtn").addEventListener("click", async () => {
        if (!state.currentTrack) {
            await playNextRecommendation();
            return;
        }

        state.suppressPauseInteraction = true;
        state.audioEl.pause();
        state.audioEl.currentTime = 0;
        setTimeout(() => {
            state.suppressPauseInteraction = false;
        }, 0);

        await sendInteraction("SKIP");
        await playNextRecommendation();
    });

    $("replayBtn").addEventListener("click", async () => {
        if (!state.audioEl.src) {
            setStatus("Сначала выбери трек");
            return;
        }

        const wasPaused = state.audioEl.paused;
        state.audioEl.currentTime = 0;
        await state.audioEl.play();

        if (!wasPaused) {
            await sendInteraction("PLAY");
        }
    });

    $("likeBtn").addEventListener("click", async () => {
        await sendRating(10);
        await sendInteraction("LIKE");
        await loadRecommendations();
    });

    $("dislikeBtn").addEventListener("click", async () => {
        await sendRating(1);
        await sendInteraction("DISLIKE");
        await loadRecommendations();
    });

    document.querySelectorAll("[data-rating]").forEach(btn => {
        btn.addEventListener("click", async () => {
            await sendRating(Number(btn.dataset.rating));
            await loadRecommendations();
        });
    });

    $("logoutBtn").addEventListener("click", logout);

    state.audioEl.addEventListener("play", async () => {
        await sendInteraction("PLAY");
    });

    state.audioEl.addEventListener("pause", async () => {
        if (state.suppressPauseInteraction || state.audioEl.ended) {
            return;
        }

        await sendInteraction("PAUSE");
    });

    state.audioEl.addEventListener("ended", async () => {
        await sendInteraction("FINISH");
        await playNextRecommendation();
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