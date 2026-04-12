const SESSION_KEY = "musicrec_session";

function setStatus(text) {
  document.getElementById("status").textContent = text;
}

function saveSession(data) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(data));
}

function getSession() {
  const raw = localStorage.getItem(SESSION_KEY);
  return raw ? JSON.parse(raw) : null;
}

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  if (!res.ok) {
    let text = await res.text();
    throw new Error(text);
  }

  if (res.status === 204) return null;
  return res.json();
}

function activateTab(mode) {
  document.getElementById("tabLogin").classList.toggle("active", mode === "login");
  document.getElementById("tabRegister").classList.toggle("active", mode === "register");
  document.getElementById("loginForm").classList.toggle("active", mode === "login");
  document.getElementById("registerForm").classList.toggle("active", mode === "register");
}

document.getElementById("tabLogin").addEventListener("click", () => activateTab("login"));
document.getElementById("tabRegister").addEventListener("click", () => activateTab("register"));

document.getElementById("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    setStatus("Выполняется вход...");

    const data = await api("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({
        email: document.getElementById("loginEmail").value.trim(),
        password: document.getElementById("loginPassword").value
      })
    });

    saveSession(data);
    setStatus("Успешный вход");
    window.location.href = "/user.html";
  } catch (err) {
    setStatus("Ошибка входа:\n" + err.message);
  }
});

document.getElementById("registerForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    setStatus("Создание аккаунта...");

    const data = await api("/api/v1/auth/register", {
      method: "POST",
      body: JSON.stringify({
        displayName: document.getElementById("registerDisplayName").value.trim(),
        email: document.getElementById("registerEmail").value.trim(),
        password: document.getElementById("registerPassword").value
      })
    });

    saveSession(data);
    setStatus("Аккаунт создан");
    window.location.href = "/user.html";
  } catch (err) {
    setStatus("Ошибка регистрации:\n" + err.message);
  }
});

(async function autoCheckSession() {
  const session = getSession();
  if (!session?.sessionToken) return;

  try {
    await api("/api/v1/auth/me", {
      headers: { "X-Session-Token": session.sessionToken }
    });
    window.location.href = "/user.html";
  } catch {
    localStorage.removeItem(SESSION_KEY);
  }
})();