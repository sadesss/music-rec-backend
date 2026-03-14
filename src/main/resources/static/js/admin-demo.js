// Admin snippets.
// If admin token enabled: add header X-Admin-Token (see application.properties).

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
  return r.json();
}

async function analyzeTrack(trackId) {
  const r = await fetch(`/api/admin/v1/tracks/${trackId}/analyze`, {
    method: "POST",
    headers: adminHeaders()
  });
  return r.json();
}

async function trainModel(notes = "") {
  const r = await fetch("/api/admin/v1/train", {
    method: "POST",
    headers: { ...adminHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ notes })
  });
  return r.json();
}

async function getMetrics() {
  const r = await fetch("/api/admin/v1/metrics", {
    method: "GET",
    headers: adminHeaders()
  });
  return r.json();
}
