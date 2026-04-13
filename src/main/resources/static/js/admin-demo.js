const SESSION_KEY = "musicrec_session";
let session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");

function clearSessionAndRedirect() {
  localStorage.removeItem(SESSION_KEY);
  window.location.href = "/auth";
}

async function authFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (session?.sessionToken) {
    headers["X-Session-Token"] = session.sessionToken;
  }

  const response = await fetch(path, { ...options, headers });

  if (response.status === 401 || response.status === 403) {
    clearSessionAndRedirect();
    throw new Error("Сессия недействительна или доступ запрещён");
  }

  if (!response.ok) {
    throw new Error(await response.text());
  }

  if (response.status === 204) return null;
  return response.json();
}

async function ensureAdmin() {
  if (!session?.sessionToken) {
    clearSessionAndRedirect();
    return;
  }

  const me = await authFetch("/api/v1/auth/me", { method: "GET" });

  if (me.role !== "ADMIN") {
    clearSessionAndRedirect();
    return;
  }
}

document.addEventListener("DOMContentLoaded", ensureAdmin);