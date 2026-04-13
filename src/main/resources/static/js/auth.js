const SESSION_KEY = "musicrec_session";

let session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");

function setStatus(text) {
    document.getElementById("status").textContent = text;
}

function saveSession(data) {
    session = data;
    localStorage.setItem(SESSION_KEY, JSON.stringify(data));
}

function clearSession() {
    session = null;
    localStorage.removeItem(SESSION_KEY);
}

function getRedirectByRole(role) {
    if (role === "ADMIN") {
        return "/admin";
    }
    return "/player";
}

async function api(path, options = {}) {
    const headers = {
        "Content-Type": "application/json",
        ...(options.headers || {})
    };

    const res = await fetch(path, {
        ...options,
        headers
    });

    if (!res.ok) {
        let text = await res.text();
        throw new Error(text || `Request failed: ${res.status}`);
    }

    if (res.status === 204) {
        return null;
    }

    return res.json();
}

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
        window.location.href = getRedirectByRole(data.role);
    } catch (err) {
        setStatus("Ошибка входа: " + err.message);
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
        window.location.href = getRedirectByRole(data.role);
    } catch (err) {
        setStatus("Ошибка регистрации: " + err.message);
    }
});

(async function autoCheckSession() {
    if (!session?.sessionToken) return;

    try {
        const me = await api("/api/v1/auth/me", {
            method: "GET",
            headers: {
                "X-Session-Token": session.sessionToken
            }
        });

        window.location.href = getRedirectByRole(me.role);
    } catch {
        clearSession();
    }
})();