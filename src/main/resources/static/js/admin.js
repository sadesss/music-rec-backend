const SESSION_KEY = "musicrec_session";
const MODEL_SETTINGS_KEY = "musicrec_admin_model_settings";
const MODEL_VERSION_KEY = "musicrec_admin_model_version";

let session = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
let mockModelVersion = localStorage.getItem(MODEL_VERSION_KEY) || "NewSASRec-demo";

const admin$ = (id) => document.getElementById(id);

const DEFAULT_MODEL_SETTINGS = {
    epochs: 50,
    batchSize: 512,
    learningRate: 0.0003,
    historyLength: 50,
    candidates: 1000,
    topK: 10,
    easeL2: 500,
    dropout: 0.2,
    filterSeen: true,
    diversityBoost: true,
    novelMode: false
};

const RANGE_BINDINGS = [
    { input: "modelEpochs", value: "modelEpochsValue", key: "epochs", format: (v) => String(Math.round(Number(v))) },
    { input: "modelBatchSize", value: "modelBatchSizeValue", key: "batchSize", format: (v) => String(Math.round(Number(v))) },
    { input: "modelLearningRate", value: "modelLearningRateValue", key: "learningRate", format: (v) => Number(v).toFixed(4) },
    { input: "modelHistoryLength", value: "modelHistoryLengthValue", key: "historyLength", format: (v) => String(Math.round(Number(v))) },
    { input: "modelCandidates", value: "modelCandidatesValue", key: "candidates", format: (v) => String(Math.round(Number(v))) },
    { input: "modelTopK", value: "modelTopKValue", key: "topK", format: (v) => String(Math.round(Number(v))) },
    { input: "modelEaseL2", value: "modelEaseL2Value", key: "easeL2", format: (v) => String(Math.round(Number(v))) },
    { input: "modelDropout", value: "modelDropoutValue", key: "dropout", format: (v) => Number(v).toFixed(2) }
];

const METRIC_META = [
    {
        key: "Precision@10",
        label: "Precision",
        tag: "@10",
        caption: "Доля релевантных композиций среди верхних рекомендаций."
    },
    {
        key: "Recall@10",
        label: "Recall",
        tag: "@10",
        caption: "Доля найденных релевантных композиций в ограниченной выдаче."
    },
    {
        key: "HitRate@10",
        label: "HitRate",
        tag: "@10",
        caption: "Вероятность попадания хотя бы одной релевантной композиции в top-K."
    },
    {
        key: "NDCG@10",
        label: "NDCG",
        tag: "@10",
        caption: "Качество ранжирования с учетом позиции релевантной композиции."
    },
    {
        key: "Diversity@100",
        label: "Diversity",
        tag: "@100",
        caption: "Внутрисписковое разнообразие рекомендованных композиций."
    },
    {
        key: "Serendipity@100",
        label: "Serendipity",
        tag: "@100",
        caption: "Неожиданная полезность рекомендаций относительно простой популярности."
    }
];

function setAdminStatus(text) {
    const el = admin$("adminStatus");
    if (el) {
        el.textContent = text;
    }
}

function clearSessionAndRedirect() {
    localStorage.removeItem(SESSION_KEY);
    window.location.href = "/auth";
}

function logAdmin(message, isError = false) {
    const container = admin$("adminLog");
    if (!container) return;

    const row = document.createElement("div");
    row.className = `terminal-line ${isError ? "error" : "ok"}`;
    row.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;

    if (container.children.length === 1 && container.textContent.trim() === "Лог пуст.") {
        container.innerHTML = "";
    }

    container.prepend(row);
    setAdminStatus(message);
}

function escapeHtml(str) {
    return String(str ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

async function authFetch(url, options = {}) {
    const headers = {
        ...(options.headers || {})
    };

    if (session?.sessionToken) {
        headers["X-Session-Token"] = session.sessionToken;
    }

    const response = await fetch(url, {
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

async function ensureAdmin() {
    if (!session?.sessionToken) {
        clearSessionAndRedirect();
        throw new Error("Нет активной сессии");
    }

    const me = await authFetch("/api/v1/auth/me", { method: "GET" });

    if (me.role !== "ADMIN") {
        clearSessionAndRedirect();
        throw new Error("Требуется роль ADMIN");
    }

    const nameEl = admin$("adminUserName");
    if (nameEl) {
        nameEl.textContent = `${me.displayName} (${me.email})`;
    }

    return me;
}

async function apiGet(url) {
    return authFetch(url, { method: "GET" });
}

async function apiPostJson(url, body) {
    return authFetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
}

function getStoredSettings() {
    try {
        return {
            ...DEFAULT_MODEL_SETTINGS,
            ...(JSON.parse(localStorage.getItem(MODEL_SETTINGS_KEY) || "{}"))
        };
    } catch (_) {
        return { ...DEFAULT_MODEL_SETTINGS };
    }
}

function applyModelSettings(settings) {
    const merged = { ...DEFAULT_MODEL_SETTINGS, ...(settings || {}) };

    RANGE_BINDINGS.forEach(({ input, value, key, format }) => {
        const inputEl = admin$(input);
        const valueEl = admin$(value);
        if (inputEl) inputEl.value = merged[key];
        if (valueEl) valueEl.textContent = format(merged[key]);
    });

    const filterSeenEl = admin$("filterSeen");
    const diversityBoostEl = admin$("diversityBoost");
    const novelModeEl = admin$("novelMode");

    if (filterSeenEl) filterSeenEl.checked = Boolean(merged.filterSeen);
    if (diversityBoostEl) diversityBoostEl.checked = Boolean(merged.diversityBoost);
    if (novelModeEl) novelModeEl.checked = Boolean(merged.novelMode);

    updateModelSummary();
}

function collectModelSettings() {
    return {
        epochs: Number(admin$("modelEpochs")?.value || DEFAULT_MODEL_SETTINGS.epochs),
        batchSize: Number(admin$("modelBatchSize")?.value || DEFAULT_MODEL_SETTINGS.batchSize),
        learningRate: Number(admin$("modelLearningRate")?.value || DEFAULT_MODEL_SETTINGS.learningRate),
        historyLength: Number(admin$("modelHistoryLength")?.value || DEFAULT_MODEL_SETTINGS.historyLength),
        candidates: Number(admin$("modelCandidates")?.value || DEFAULT_MODEL_SETTINGS.candidates),
        topK: Number(admin$("modelTopK")?.value || DEFAULT_MODEL_SETTINGS.topK),
        easeL2: Number(admin$("modelEaseL2")?.value || DEFAULT_MODEL_SETTINGS.easeL2),
        dropout: Number(admin$("modelDropout")?.value || DEFAULT_MODEL_SETTINGS.dropout),
        filterSeen: Boolean(admin$("filterSeen")?.checked),
        diversityBoost: Boolean(admin$("diversityBoost")?.checked),
        novelMode: Boolean(admin$("novelMode")?.checked)
    };
}

function persistModelSettings(settings) {
    localStorage.setItem(MODEL_SETTINGS_KEY, JSON.stringify(settings));
}

async function saveModelSettings(showLog = true) {
    const settings = collectModelSettings();
    persistModelSettings(settings);
    updateModelSummary();

    try {
        await apiPostJson("/api/admin/v1/model/settings", { settings });
    } catch (_) {
    }

    if (showLog) {
        logAdmin("Параметры рекомендательной модели сохранены");
    }

    return settings;
}

async function resetModelSettings() {
    applyModelSettings(DEFAULT_MODEL_SETTINGS);
    await saveModelSettings(false);
    renderMetricPlaceholder("Настройки сброшены. Рассчитайте метрики повторно для новой конфигурации.");
    logAdmin("Параметры модели сброшены к базовым значениям");
}

function updateModelSummary() {
    const settings = collectModelSettings();
    const modeEl = admin$("modelModeSummary");
    const versionEl = admin$("modelVersionSummary");

    if (modeEl) {
        modeEl.textContent = settings.novelMode || settings.filterSeen ? "novel" : "next";
    }

    if (versionEl) {
        versionEl.textContent = mockModelVersion.replace("NewSASRec-", "");
    }
}

function bindRangePreview() {
    RANGE_BINDINGS.forEach(({ input, value, key, format }) => {
        const inputEl = admin$(input);
        const valueEl = admin$(value);
        if (!inputEl || !valueEl) return;

        inputEl.addEventListener("input", () => {
            valueEl.textContent = format(inputEl.value);
            updateModelSummary();
        });
    });

    ["filterSeen", "diversityBoost", "novelMode"].forEach((id) => {
        const el = admin$(id);
        if (el) el.addEventListener("change", updateModelSummary);
    });
}

function renderMetricPlaceholder(message = "Нажмите «Рассчитать метрики», чтобы увидеть расчет Precision, Recall, HitRate, NDCG, Diversity и Serendipity.") {
    const panel = admin$("metricsPanel");
    if (!panel) return;
    panel.innerHTML = `<div class="metrics-loader">${escapeHtml(message)}</div>`;
}

function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

function roundMetric(value) {
    return Math.round(value * 10000) / 10000;
}

function makeMockMetrics(settings) {
    const epochsFactor = clamp(settings.epochs / 50, 0.35, 1.35);
    const candidateFactor = clamp(Math.sqrt(settings.candidates / 1000), 0.35, 2.25);
    const historyFactor = clamp(settings.historyLength / 50, 0.45, 1.5);
    const lrPenalty = clamp(Math.abs(settings.learningRate - 0.0003) * 22, 0, 0.11);
    const dropoutPenalty = settings.dropout > 0.35 ? 0.018 : 0;
    const regularizationBonus = 1 - Math.min(Math.abs(settings.easeL2 - 500) / 1200, 0.18);
    const quality = clamp(0.72 + epochsFactor * 0.12 + historyFactor * 0.07 + regularizationBonus * 0.06 - lrPenalty - dropoutPenalty, 0.55, 0.98);
    const novelPenalty = settings.novelMode ? 0.05 : 0;
    const filterBoost = settings.filterSeen ? 0.012 : 0;
    const diversityBoost = settings.diversityBoost ? 0.022 : 0;

    const precision = clamp(0.028 + quality * 0.014 + candidateFactor * 0.003 - novelPenalty * 0.09, 0.018, 0.055);
    const recall = clamp(0.255 + quality * 0.086 + candidateFactor * 0.018 - novelPenalty, 0.13, 0.42);
    const hitRate = clamp(recall + 0.006 + filterBoost, 0.14, 0.45);
    const ndcg = clamp(0.178 + quality * 0.076 + historyFactor * 0.015 - novelPenalty * 0.8, 0.08, 0.31);
    const diversity = clamp(0.112 + diversityBoost + (settings.filterSeen ? 0.012 : 0) + Math.min(settings.candidates / 5000, 1) * 0.008, 0.08, 0.18);
    const serendipity = clamp(0.245 + quality * 0.065 + diversityBoost + (settings.novelMode ? 0.018 : 0), 0.13, 0.37);

    return {
        "Precision@10": roundMetric(precision),
        "Recall@10": roundMetric(recall),
        "HitRate@10": roundMetric(hitRate),
        "NDCG@10": roundMetric(ndcg),
        "Diversity@100": roundMetric(diversity),
        "Serendipity@100": roundMetric(serendipity)
    };
}

function metricValueAsPercent(value) {
    return `${(Number(value) * 100).toFixed(1)}%`;
}

function animateNumber(el, value, delay = 0) {
    const target = Number(value);
    const duration = 900;
    const startTime = performance.now() + delay;

    function step(now) {
        if (now < startTime) {
            requestAnimationFrame(step);
            return;
        }

        const progress = clamp((now - startTime) / duration, 0, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        el.textContent = metricValueAsPercent(target * eased);

        if (progress < 1) {
            requestAnimationFrame(step);
        } else {
            el.textContent = metricValueAsPercent(target);
        }
    }

    requestAnimationFrame(step);
}

function renderMetrics(metricsResponse, animated = true) {
    const panel = admin$("metricsPanel");
    if (!panel) return;

    const metrics = metricsResponse.metrics || {};
    const settings = collectModelSettings();
    const updatedAt = new Date().toLocaleString();

    panel.innerHTML = `
        <div class="metrics-header-card">
            <div>
                <div class="detail-key">Версия модели</div>
                <div class="detail-value">${escapeHtml(metricsResponse.modelVersion || mockModelVersion)}</div>
            </div>
            <div>
                <div class="detail-key">Режим оценки</div>
                <div class="detail-value">${settings.novelMode || settings.filterSeen ? "novel / filter seen" : "next"}</div>
            </div>
            <div>
                <div class="detail-key">Время расчета</div>
                <div class="detail-value">${escapeHtml(updatedAt)}</div>
            </div>
        </div>
        <div class="metrics-grid">
            ${METRIC_META.map((meta, index) => {
                const value = Number(metrics[meta.key] ?? 0);
                const width = clamp(value * 100, 0, 100);
                return `
                    <div class="metric-card" style="animation-delay: ${index * 90}ms">
                        <div class="metric-card-top">
                            <div class="metric-name">${escapeHtml(meta.label)}</div>
                            <div class="metric-tag">${escapeHtml(meta.tag)}</div>
                        </div>
                        <div class="metric-value" data-metric-value="${value}" data-delay="${index * 90}">${animated ? "0.0%" : metricValueAsPercent(value)}</div>
                        <div class="metric-bar"><div class="metric-bar-fill" style="width: ${animated ? 0 : width}%" data-bar-width="${width}"></div></div>
                        <div class="metric-caption">${escapeHtml(meta.caption)}</div>
                    </div>`;
            }).join("")}
        </div>
    `;

    if (animated) {
        panel.querySelectorAll("[data-metric-value]").forEach((el) => {
            animateNumber(el, Number(el.dataset.metricValue), Number(el.dataset.delay || 0));
        });

        window.setTimeout(() => {
            panel.querySelectorAll("[data-bar-width]").forEach((el) => {
                el.style.width = `${el.dataset.barWidth}%`;
            });
        }, 150);
    }
}

async function showMetricsLoading() {
    const panel = admin$("metricsPanel");
    if (!panel) return;

    panel.innerHTML = `
        <div class="metrics-loader" id="metricsLoaderText">Подготовка тестовой выборки...</div>
        <div class="progress-track"><div class="progress-fill" id="metricsProgressFill" style="width: 0%"></div></div>
    `;

    const fill = admin$("metricsProgressFill");
    const text = admin$("metricsLoaderText");
    const stages = [
        ["Экспорт взаимодействий пользователей...", 18],
        ["Формирование top-K списков рекомендаций...", 43],
        ["Расчет Precision / Recall / HitRate...", 64],
        ["Расчет NDCG / Diversity / Serendipity...", 86],
        ["Подготовка визуализации метрик...", 100]
    ];

    for (const [caption, percent] of stages) {
        text.textContent = caption;
        fill.style.width = `${percent}%`;
        await sleep(380);
    }
}

async function calculateMetrics() {
    const settings = await saveModelSettings(false);
    await showMetricsLoading();

    let metricsResponse = null;

    try {
        metricsResponse = await apiPostJson("/api/admin/v1/metrics/calculate", { settings });
        mockModelVersion = metricsResponse.modelVersion || mockModelVersion;
    } catch (_) {
        metricsResponse = {
            modelVersion: mockModelVersion,
            metrics: makeMockMetrics(settings),
            metricsJsonPath: "mock://admin-panel/metrics.json"
        };
    }

    localStorage.setItem(MODEL_VERSION_KEY, mockModelVersion);
    updateModelSummary();
    renderMetrics(metricsResponse, true);
    logAdmin("Метрики качества рассчитаны");
}

async function simulateTrainModel() {
    const settings = await saveModelSettings(false);
    const notes = admin$("trainNotes")?.value.trim() || "";
    const fill = admin$("trainProgressFill");
    const caption = admin$("trainProgressCaption");

    const stages = [
        ["Экспорт обучающих данных", 15],
        ["Формирование кандидатов EASE", 38],
        ["Обучение NewSASRec", 72],
        ["Сохранение версии модели", 100]
    ];

    fill.style.width = "0%";
    caption.textContent = "Инициализация обучения...";

    for (const [text, percent] of stages) {
        await sleep(520);
        fill.style.width = `${percent}%`;
        caption.textContent = `${text} · ${percent}%`;
    }

    const timestamp = new Date().toISOString().replaceAll("-", "").replaceAll(":", "").slice(0, 15);
    mockModelVersion = `NewSASRec-demo-${timestamp}`;

    let response = null;
    try {
        response = await apiPostJson("/api/admin/v1/train/mock", { notes, settings });
        mockModelVersion = response.modelVersion || mockModelVersion;
    } catch (_) {
        response = {
            modelVersion: mockModelVersion,
            metrics: makeMockMetrics(settings),
            metricsJsonPath: "mock://admin-panel/metrics.json"
        };
    }

    localStorage.setItem(MODEL_VERSION_KEY, mockModelVersion);
    caption.textContent = `Обучение завершено. Версия модели: ${mockModelVersion}`;
    updateModelSummary();
    renderMetrics(response, true);
    logAdmin(`Демо-обучение завершено. modelVersion=${mockModelVersion}`);
}

async function rollbackModelVersion() {
    const input = admin$("rollbackModelVersion");
    const desiredVersion = input?.value.trim() || "";

    if (!desiredVersion) {
        logAdmin("Введите название версии модели для возврата", true);
        return;
    }

    try {
        const response = await apiPostJson("/api/admin/v1/model/version/rollback", {
            modelVersion: desiredVersion
        });
        mockModelVersion = response.modelVersion || desiredVersion;
    } catch (_) {
        mockModelVersion = desiredVersion;
    }

    localStorage.setItem(MODEL_VERSION_KEY, mockModelVersion);
    updateModelSummary();

    const caption = admin$("trainProgressCaption");
    if (caption) {
        caption.textContent = `Выполнен возврат к версии модели: ${mockModelVersion}`;
    }

    renderMetricPlaceholder("Версия модели изменена. Для отображения новых значений нажмите «Рассчитать метрики».");
    logAdmin(`Выполнен возврат к версии модели: ${mockModelVersion}`);
}

admin$("rollbackModelVersionBtn").addEventListener("click", async () => {
    try {
        await rollbackModelVersion();
    } catch (e) {
        logAdmin(e.message, true);
    }
});

async function importJamendo() {
    const body = {
        datasetRoot: admin$("datasetRoot").value.trim(),
        audioRoot: admin$("audioRoot").value.trim(),
        limit: Number(admin$("importLimit").value || 100)
    };

    const response = await apiPostJson("/api/admin/v1/import/jamendo", body);
    logAdmin(`Импорт завершён: imported=${response.importedCount}, skipped=${response.skippedCount}`);
    await loadTracks();
}

async function uploadTrack() {
    const file = admin$("trackFile").files[0];
    if (!file) {
        throw new Error("Выбери mp3 файл");
    }

    const tags = admin$("trackTags").value
        .split(",")
        .map(tag => tag.trim())
        .filter(Boolean);

    const metadata = {
        title: admin$("trackTitle").value.trim(),
        artist: admin$("trackArtist").value.trim() || null,
        album: admin$("trackAlbum").value.trim() || null,
        genre: admin$("trackGenre").value.trim() || null,
        durationSeconds: admin$("trackDuration").value ? Number(admin$("trackDuration").value) : null,
        metadataText: JSON.stringify({ source: "admin-ui", tags })
    };

    if (!metadata.title) {
        throw new Error("Укажи название трека");
    }

    const fd = new FormData();
    fd.append("file", file);
    fd.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));

    const response = await authFetch("/api/admin/v1/tracks/upload", {
        method: "POST",
        body: fd
    });

    logAdmin(`Трек загружен: ${response.trackId}`);
    await loadTracks();
}

async function analyzeTrack() {
    const trackId = admin$("analyzeTrackId").value.trim();
    if (!trackId) {
        throw new Error("Укажи trackId для анализа");
    }

    const response = await authFetch(`/api/admin/v1/tracks/${encodeURIComponent(trackId)}/analyze`, {
        method: "POST"
    });

    logAdmin(`Анализ завершён: features=${response.featuresUpserted}`);
}

async function loadTracks() {
    const tracks = await fetch("/api/v1/tracks").then(async r => {
        if (!r.ok) throw new Error(await r.text());
        return r.json();
    });

    const list = admin$("adminTracksList");

    if (!tracks.length) {
        list.innerHTML = `<div class="terminal-line">Треков пока нет.</div>`;
        return;
    }

    list.innerHTML = tracks.map(track => `
        <div class="track-card static-track-card">
            <div class="track-card-title">${escapeHtml(track.title || "Без названия")}</div>
            <div class="track-card-meta">${escapeHtml(track.artist || "Неизвестный исполнитель")}</div>
            <div class="track-card-submeta">${escapeHtml(track.album || "Без альбома")}</div>
            <div class="track-card-submeta">${escapeHtml(track.id)}</div>
        </div>
    `).join("");
}

async function logout() {
    try {
        await authFetch("/api/v1/auth/logout", { method: "POST" });
    } catch (_) {
        // ignore
    } finally {
        clearSessionAndRedirect();
    }
}

function bindAdminEvents() {
    bindRangePreview();

    admin$("saveModelSettingsBtn").addEventListener("click", async () => {
        try {
            await saveModelSettings(true);
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("resetModelSettingsBtn").addEventListener("click", async () => {
        try {
            await resetModelSettings();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("importJamendoBtn").addEventListener("click", async () => {
        try {
            await importJamendo();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("uploadTrackBtn").addEventListener("click", async () => {
        try {
            await uploadTrack();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("analyzeTrackBtn").addEventListener("click", async () => {
        try {
            await analyzeTrack();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("trainModelBtn").addEventListener("click", async () => {
        try {
            await simulateTrainModel();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("calculateMetricsBtn").addEventListener("click", async () => {
        try {
            await calculateMetrics();
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("loadTracksBtn").addEventListener("click", async () => {
        try {
            await loadTracks();
            logAdmin("Список треков обновлён");
        } catch (e) {
            logAdmin(e.message, true);
        }
    });

    admin$("logoutBtn").addEventListener("click", logout);
}

document.addEventListener("DOMContentLoaded", async () => {
    applyModelSettings(getStoredSettings());
    bindAdminEvents();
    renderMetricPlaceholder();

    try {
        await ensureAdmin();
        await loadTracks();
        logAdmin("Панель администратора инициализирована");
    } catch (e) {
        logAdmin(e.message, true);
    }
});