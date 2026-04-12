const ADMIN_TOKEN = "change_me";

function adminHeaders() {
  return { "X-Admin-Token": ADMIN_TOKEN };
}

async function uploadTrack(file, metadata) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));

  const r = await fetch("/api/admin/v1/tracks/upload", {
    method: "POST",
    headers: adminHeaders(),
    body: fd
  });

  if (!r.ok) {
    throw new Error(await r.text());
  }

  return r.json();
}

async function listTracks() {
  const r = await fetch("/api/v1/tracks");
  if (!r.ok) {
    throw new Error(await r.text());
  }
  return r.json();
}

function setStatus(text) {
  document.getElementById("status").textContent = text;
}

function formatTrack(track) {
  return `
    <div class="track-card">
      <div class="track-title">${track.title ?? "Без названия"}</div>
      <div><strong>Исполнитель:</strong> ${track.artist ?? "-"}</div>
      <div><strong>Альбом:</strong> ${track.album ?? "-"}</div>
      <div><strong>Жанр:</strong> ${track.originalGenre ?? "-"}</div>
      <div><strong>ID:</strong> ${track.id}</div>
      <audio controls preload="none" src="${track.audioUrl}"></audio>
    </div>
  `;
}

async function refreshTracks() {
  const container = document.getElementById("tracks");
  container.innerHTML = "Загрузка...";
  try {
    const tracks = await listTracks();
    container.innerHTML = tracks.length
      ? tracks.map(formatTrack).join("")
      : "Треков пока нет";
  } catch (e) {
    container.innerHTML = `Ошибка загрузки списка: ${e.message}`;
  }
}

document.getElementById("uploadForm").addEventListener("submit", async (e) => {
  e.preventDefault();

  const fileInput = document.getElementById("file");
  const file = fileInput.files[0];
  if (!file) {
    setStatus("Выбери mp3 файл");
    return;
  }

  const metadata = {
    title: document.getElementById("title").value.trim(),
    artist: document.getElementById("artist").value.trim(),
    album: document.getElementById("album").value.trim(),
    genre: document.getElementById("genre").value.trim(),
    durationSeconds: document.getElementById("durationSeconds").value
      ? Number(document.getElementById("durationSeconds").value)
      : null,
    metadataText: document.getElementById("metadataText").value.trim()
  };

  try {
    setStatus("Загрузка...");
    const result = await uploadTrack(file, metadata);
    setStatus(`Успешно загружено.\ntrackId=${result.trackId}\naudioKey=${result.audioKey}`);
    e.target.reset();
    await refreshTracks();
  } catch (err) {
    setStatus(`Ошибка загрузки:\n${err.message}`);
  }
});

document.getElementById("refreshBtn").addEventListener("click", refreshTracks);

refreshTracks();