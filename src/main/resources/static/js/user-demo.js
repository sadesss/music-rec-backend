let currentUserId = localStorage.getItem("musicrec_user_id") || null;

function setStatus(text) {
  document.getElementById("status").textContent = text;
}

async function createUser(displayName) {
  const r = await fetch("/api/v1/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName })
  });
  if (!r.ok) throw new Error(await r.text());
  return r.json();
}

async function listTracks() {
  const r = await fetch("/api/v1/tracks");
  if (!r.ok) throw new Error(await r.text());
  return r.json();
}

async function sendInteraction({ userId, trackId, type, positionMs = 0, metadataText = null }) {
  const r = await fetch("/api/v1/interactions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId, trackId, type, positionMs, metadataText })
  });
  if (!r.ok) throw new Error(await r.text());
  return r.json();
}

async function rate({ userId, trackId, value }) {
  const r = await fetch("/api/v1/ratings", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId, trackId, value })
  });
  if (!r.ok) throw new Error(await r.text());
  return r.json();
}

async function getRecommendations(userId, limit = 20) {
  const r = await fetch(`/api/v1/recommendations?userId=${encodeURIComponent(userId)}&limit=${limit}`);
  if (!r.ok) throw new Error(await r.text());
  return r.json();
}

function ensureUser() {
  if (!currentUserId) throw new Error("Сначала создай пользователя");
  return currentUserId;
}

function trackCard(track) {
  return `
    <div class="track">
      <div class="title">${track.title ?? "Без названия"}</div>
      <div><strong>Исполнитель:</strong> ${track.artist ?? "-"}</div>
      <div><strong>Альбом:</strong> ${track.album ?? "-"}</div>
      <div><strong>Жанр:</strong> ${track.originalGenre ?? "-"}</div>
      <audio controls preload="none" src="${track.audioUrl}"></audio>
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
  return `
    <div class="track">
      <div class="title">#${item.rank} ${item.title ?? item.trackId}</div>
      <div><strong>Исполнитель:</strong> ${item.artist ?? "-"}</div>
      <div><strong>Альбом:</strong> ${item.album ?? "-"}</div>
      <div><strong>Жанр:</strong> ${item.originalGenre ?? "-"}</div>
      <div><strong>Score:</strong> ${item.score?.toFixed?.(4) ?? item.score}</div>
      <div><strong>Причина:</strong> ${item.reason ?? "-"}</div>
      <audio controls preload="none" src="${item.audioUrl}"></audio>
      <div>
        <button onclick="onPlay('${item.trackId}')">PLAY</button>
        <button onclick="onFinish('${item.trackId}')">FINISH</button>
        <button onclick="onLike('${item.trackId}')">LIKE</button>
        <button onclick="onDislike('${item.trackId}')">DISLIKE</button>
      </div>
    </div>
  `;
}

async function loadCatalog() {
  try {
    const tracks = await listTracks();
    document.getElementById("catalog").innerHTML = tracks.map(trackCard).join("") || "Нет треков";
  } catch (e) {
    setStatus("Ошибка загрузки каталога:\n" + e.message);
  }
}

async function loadRecommendations() {
  try {
    const userId = ensureUser();
    const reco = await getRecommendations(userId, 20);
    document.getElementById("recommendations").innerHTML =
      (reco.items || []).map(recoCard).join("") || "Рекомендаций нет";
  } catch (e) {
    setStatus("Ошибка загрузки рекомендаций:\n" + e.message);
  }
}

async function onPlay(trackId) {
  try {
    const userId = ensureUser();
    await sendInteraction({ userId, trackId, type: "PLAY" });
    setStatus("Сохранено: PLAY " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

async function onFinish(trackId) {
  try {
    const userId = ensureUser();
    await sendInteraction({ userId, trackId, type: "FINISH" });
    setStatus("Сохранено: FINISH " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

async function onLike(trackId) {
  try {
    const userId = ensureUser();
    await rate({ userId, trackId, value: 1 });
    await sendInteraction({ userId, trackId, type: "LIKE" });
    setStatus("Сохранено: LIKE " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

async function onDislike(trackId) {
  try {
    const userId = ensureUser();
    await rate({ userId, trackId, value: -1 });
    await sendInteraction({ userId, trackId, type: "DISLIKE" });
    setStatus("Сохранено: DISLIKE " + trackId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
}

document.getElementById("createUserBtn").addEventListener("click", async () => {
  try {
    const u = await createUser("Demo User " + new Date().toLocaleTimeString());
    currentUserId = u.id;
    localStorage.setItem("musicrec_user_id", currentUserId);
    setStatus("Создан пользователь:\n" + currentUserId);
    await loadRecommendations();
  } catch (e) {
    setStatus(e.message);
  }
});

document.getElementById("loadTracksBtn").addEventListener("click", loadCatalog);
document.getElementById("loadRecoBtn").addEventListener("click", loadRecommendations);

loadCatalog();
if (currentUserId) {
  setStatus("Текущий пользователь:\n" + currentUserId);
  loadRecommendations();
}