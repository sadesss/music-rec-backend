// Minimal frontend interaction snippets (fetch-based).
// You can copy these into your real frontend later.

async function createUser(displayName) {
  const r = await fetch("/api/v1/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName })
  });
  return r.json();
}

async function listTracks() {
  const r = await fetch("/api/v1/tracks");
  return r.json();
}

function audioUrl(trackId) {
  return `/api/v1/tracks/${trackId}/stream`;
}

async function sendInteraction({ userId, trackId, type, positionMs }) {
  const r = await fetch("/api/v1/interactions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId, trackId, type, positionMs })
  });
  return r.json();
}

async function rate({ userId, trackId, value }) {
  const r = await fetch("/api/v1/ratings", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId, trackId, value })
  });
  return r.json();
}

async function getRecommendations(userId, limit = 20) {
  const r = await fetch(`/api/v1/recommendations?userId=${encodeURIComponent(userId)}&limit=${limit}`);
  return r.json();
}

// Example usage in console:
// 1) const u = await createUser("Alice")
// 2) const tracks = await listTracks()
// 3) const a = new Audio(audioUrl(tracks[0].id)); a.play(); await sendInteraction({userId:u.id, trackId:tracks[0].id, type:"PLAY", positionMs:0})
