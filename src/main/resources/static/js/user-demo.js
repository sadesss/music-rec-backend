const SESSION_KEY = "musicrec_session";

let session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
let currentUser = null;

function setStatus(text) {
  const el = document.getElementById("status");
  if (el) {
    el.textContent = text;
  }
}

function clearSessionAndRedirect() {
  localStorage.removeItem(SESSION_KEY);
  window.location.href = "/auth.html";
}

async function authFetch(path, options = {}) {
  const headers = {
    ...(options.headers || {})
  };

  if (session?.sessionToken) {
    headers["X-Session-Token"] = session.sessionToken;
  }

  const response = await fetch(path, {
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

async function ensureSession() {
  if (!session?.sessionToken) {
    clearSessionAndRedirect();
    throw new Error("Нет активной сессии");
  }

  const me = await authFetch("/api/v1/auth/me", {
    method: "GET"
  });

  if (me.role !== "USER" && me.role !== "ADMIN") {
    throw new Error("Недостаточно прав для пользовательской страницы");
  }

  currentUser = me;
  return me;
}

async function listTracks() {
  return authFetch("/api/v1/tracks", {
    method: "GET"
  });
}

async function sendInteraction({ trackId, type, positionMs = 0, metadataText = null }) {
  return authFetch("/api/v1/interactions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      trackId,
      type,
      positionMs,
      metadataText
    })
  });
}

async function rate({ trackId, value }) {
  return authFetch("/api/v1/ratings", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      trackId,
      value
    })
  });
}

async function getRecommendations(limit = 20) {
  return authFetch(`/api/v1/recommendations/me?limit=${limit}`, {
    method: "GET"
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function trackCard(track) {
  const title = escapeHtml(track.title ?? "Без названия");
  const artist = escapeHtml(track.artist ?? "-");
  const album = escapeHtml(track.album ?? "-");
  const genre = escapeHtml(track.originalGenre ?? "-");
  const audioUrl = track.audioUrl ?? "";

  return `
    <div class="track">
      <div class="title">${title}</div>
      <div><strong>Исполнитель:</strong> ${artist}</div>
      <div><strong>Альбом:</strong> ${album}</div>
      <div><strong>Жанр:</strong> ${genre}</div>
      <audio controls preload="none" src="${audioUrl}" data-track-id="${track.id}"></audio>
      <div>
        <button onclick="onPlay('${track.id}')">PLAY</button>
        <button onclick="onFinish('${track.id}')">FINISH</button>
        <button onclick="onLike('${track.id}')">LIKE</button>
        <button onclick="onDislike('${track.id}')">DISLIKE</button>
      </div>
    </div>
  `;
}

function recoCard(item) {
  const rank = item.rank ?? "-";
  const title = escapeHtml(item.title ?? item.trackId);
  const artist = escapeHtml(item.artist ?? "-");
  const album = escapeHtml(item.album ?? "-");
  const genre = escapeHtml(item.originalGenre ?? "-");
  const reason = escapeHtml(item.reason ?? "-");
  const audioUrl = item.audioUrl ?? "";
  const score =
    typeof item.score === "number"
      ? item.score.toFixed(4)
      : escapeHtml(item.score ?? "-");

  return `
    <div class="track">
      <div class="title">#${rank} ${title}</div>
      <div><strong>Исполнитель:</strong> ${artist}</div>
      <div><strong>Альбом:</strong> ${album}</div>
      <div><strong>Жанр:</strong> ${genre}</div>
      <div><strong>Score:</strong> ${score}</div>
      <div><strong>Причина:</strong> ${reason}</div>
      <audio controls preload="none" src="${audioUrl}" data-track-id="${item.trackId}"></audio>
      <div>
        <button onclick="onPlay('${item.trackId}')">PLAY</button>
        <button onclick="onFinish('${item.trackId}')">FINISH</button>
        <button onclick="onLike('${item.trackId}')">LIKE</button>
        <button onclick="onDislike('${item.trackId}')">DISLIKE</button>
      </div>
    </div>
  `;
}

function wireAudioEvents(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;

  const audios = container.querySelectorAll("audio[data-track-id]");
  audios.forEach((audio) => {
    const trackId = audio.getAttribute("data-track-id");
    if (!trackId) return;

    if (!audio.dataset.handlersBound) {
      audio.addEventListener("play", async () => {
        try {
          await sendInteraction({ trackId, type: "PLAY" });
          setStatus(`Сохранено: PLAY ${trackId}`);
        } catch (e) {
          setStatus(`Ошибка PLAY:\n${e.message}`);
        }
      });

      audio.addEventListener("pause", async () => {
        if (audio.ended) return;
        try {
          await sendInteraction({
            trackId,
            type: "PAUSE",
            positionMs: Math.floor((audio.currentTime || 0) * 1000)
          });
          setStatus(`Сохранено: PAUSE ${trackId}`);
        } catch (e) {
          setStatus(`Ошибка PAUSE:\n${e.message}`);
        }
      });

      audio.addEventListener("ended", async () => {
        try {
          await sendInteraction({
            trackId,
            type: "FINISH",
            positionMs: Math.floor((audio.duration || 0) * 1000)
          });
          setStatus(`Сохранено: FINISH ${trackId}`);
          await loadRecommendations();
        } catch (e) {
          setStatus(`Ошибка FINISH:\n${e.message}`);
        }
      });

      audio.dataset.handlersBound = "true";
    }
  });
}

async function loadCatalog() {
  const container = document.getElementById("catalog");
  if (!container) return;

  try {
    container.innerHTML = "Загрузка...";
    const tracks = await listTracks();

    container.innerHTML = tracks.length
      ? tracks.map(trackCard).join("")
      : "Нет треков";

    wireAudioEvents("catalog");
  } catch (e) {
    container.innerHTML = "Ошибка загрузки каталога";
    setStatus("Ошибка загрузки каталога:\n" + e.message);
  }
}

async function loadRecommendations() {
  const container = document.getElementById("recommendations");
  if (!container) return;

  try {
    container.innerHTML = "Загрузка...";
    const reco = await getRecommendations(20);

    const items = reco?.items || [];
    container.innerHTML = items.length
      ? items.map(recoCard).join("")
      : "Рекомендаций пока нет";

    wireAudioEvents("recommendations");
  } catch (e) {
    container.innerHTML = "Ошибка загрузки рекомендаций";
    setStatus("Ошибка загрузки рекомендаций:\n" + e.message);
  }
}

async function onPlay(trackId) {
  try {
    await sendInteraction({ trackId, type: "PLAY" });
    setStatus("Сохранено: PLAY " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

async function onFinish(trackId) {
  try {
    await sendInteraction({ trackId, type: "FINISH" });
    setStatus("Сохранено: FINISH " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

async function onLike(trackId) {
  try {
    await rate({ trackId, value: 1 });
    await sendInteraction({ trackId, type: "LIKE" });
    setStatus("Сохранено: LIKE " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

async function onDislike(trackId) {
  try {
    await rate({ trackId, value: -1 });
    await sendInteraction({ trackId, type: "DISLIKE" });
    setStatus("Сохранено: DISLIKE " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

async function logout() {
  try {
    if (session?.sessionToken) {
      await authFetch("/api/v1/auth/logout", {
        method: "POST"
      });
    }
  } catch (_) {
    // ignore
  } finally {
    clearSessionAndRedirect();
  }
}

window.onPlay = onPlay;
window.onFinish = onFinish;
window.onLike = onLike;
window.onDislike = onDislike;
window.logout = logout;

(async function init() {
  try {
    const me = await ensureSession();
    setStatus(`Вы вошли как ${me.displayName} (${me.email})`);

    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) {
      logoutBtn.addEventListener("click", logout);
    }

    const loadTracksBtn = document.getElementById("loadTracksBtn");
    if (loadTracksBtn) {
      loadTracksBtn.addEventListener("click", loadCatalog);
    }

    const loadRecoBtn = document.getElementById("loadRecoBtn");
    if (loadRecoBtn) {
      loadRecoBtn.addEventListener("click", loadRecommendations);
    }

    const welcome = document.getElementById("welcomeText");
    if (welcome) {
      welcome.textContent = `Вы вошли как ${me.displayName} (${me.email})`;
    }

    await loadCatalog();
    await loadRecommendations();
  } catch (e) {
    console.error(e);
    setStatus(`Ошибка инициализации:\n${e.message}`);
  }
})();