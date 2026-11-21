// Lab 5 auth page. The token lives ONLY in this variable: not in localStorage,
// not in the URL, not on the page. Log out = drop the variable.
let accessToken = null;
let expiresAt = null;
let expiryTimer = null;

const $ = (id) => document.getElementById(id);

function newCorrelationId() {
    return "web-" + crypto.randomUUID().slice(0, 8);
}

/** One place for every API call: JSON in, JSON out, X-Correlation-Id always, Bearer when signed in. */
async function call(method, path, body) {
    const correlationId = newCorrelationId();
    const headers = { "X-Correlation-Id": correlationId, "Accept": "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (accessToken) headers["Authorization"] = "Bearer " + accessToken;

    const response = await fetch(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    let data = null;
    try { data = await response.json(); } catch { /* empty body */ }
    return { method, path, status: response.status, data, correlationId };
}

function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    Object.entries(attrs).forEach(([k, v]) => node.setAttribute(k, v));
    children.forEach((c) => node.append(c));
    return node;
}

function httpBadge(status) {
    const cls = status === 401 || status === 403 ? "s" + status : "s" + String(status)[0];
    return el("span", { class: "http " + cls }, String(status));
}

/** Shows status plus either the item's status token or code / message / correlationId. */
function showResult(result, note) {
    const box = $("result");
    box.className = "result";
    box.replaceChildren();
    box.append(el("div", { class: "line" }, httpBadge(result.status), el("code", {}, result.method + " " + result.path)));

    const d = result.data || {};
    if (d.code) {
        box.append(el("div", { class: "line" }, "code", el("code", {}, d.code)));
        box.append(el("div", { class: "line" }, "message", el("code", {}, d.message || "")));
        box.append(el("div", { class: "line" }, "correlationId", el("code", {}, d.correlationId || "")));
    } else if (d.status) {
        box.append(el("div", { class: "line" }, "status", el("code", {}, d.status), "business key", el("code", {}, d.businessKey)));
    } else if (d.username) {
        box.append(el("div", { class: "line" }, "username", el("code", {}, d.username)));
    }
    if (note) box.append(el("div", { class: "note" }, note));
}

function showRequest(item) {
    $("request-card").hidden = false;
    $("rq-key").textContent = item.businessKey;
    $("rq-title").textContent = item.title;
    $("rq-status").textContent = item.status;
    $("request-id").value = item.id;
}

function roleOf(authorities) {
    return (authorities || []).map((a) => a.replace(/^ROLE_/, "")).join(", ") || "none";
}

function signedIn(me) {
    $("login-card").hidden = true;
    $("me-card").hidden = false;
    $("me-name").textContent = me.username;
    $("me-role").replaceChildren(...roleOf(me.authorities).split(", ").map(
        (r) => el("span", { class: "chip " + r.toLowerCase() }, r)));
    tickExpiry();
    expiryTimer = setInterval(tickExpiry, 1000);
}

function tickExpiry() {
    const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
    const m = Math.floor(left / 60), s = String(left % 60).padStart(2, "0");
    $("me-expires").textContent = `in ${m}:${s} (15-minute token)`;
    if (left === 0 && accessToken) logout("Token expired. Sign in again.");
}

function logout(note) {
    accessToken = null;          // the only copy is gone
    expiresAt = null;
    clearInterval(expiryTimer);
    $("me-card").hidden = true;
    $("login-card").hidden = false;
    $("password").value = "";
    const box = $("result");
    box.className = "result empty";
    box.textContent = note || "Signed out. The next API call carries no token and will be 401.";
}

// --- login ---------------------------------------------------------------

$("login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("login-error").hidden = true;
    const result = await call("POST", "/api/v1/auth/login", {
        username: $("username").value,
        password: $("password").value,
    });
    if (result.status !== 200) {
        accessToken = null; // a failed login stores nothing
        const d = result.data || {};
        $("login-error").hidden = false;
        $("login-error").textContent = `${result.status} ${d.code || ""}: ${d.message || "login failed"} (correlationId ${d.correlationId || result.correlationId})`;
        showResult(result);
        return;
    }
    accessToken = result.data.accessToken;
    expiresAt = Date.now() + result.data.expiresIn * 1000;
    $("password").value = "";

    const me = await call("GET", "/api/me"); // the role shown comes from the token, via the server
    if (me.status !== 200) { showResult(me); return; }
    signedIn(me.data);
    showResult(me, `Signed in. expiresIn = ${result.data.expiresIn} seconds.`);
});

document.querySelectorAll(".devuser").forEach((b) => b.addEventListener("click", () => {
    $("username").value = b.dataset.user;
    $("password").value = b.dataset.pass;
}));

$("logout").addEventListener("click", () => logout());

// --- the allowed calls: create and read ------------------------------------

$("business-key").value = "PR-" + Math.floor(100 + Math.random() * 900);

$("create-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const result = await call("POST", "/api/v1/purchase-requests", {
        businessKey: $("business-key").value,
        title: $("title").value,
    });
    if (result.status === 201) {
        showRequest(result.data);
        $("business-key").value = "PR-" + Math.floor(100 + Math.random() * 900);
    }
    showResult(result);
});

$("read-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const id = $("request-id").value.trim();
    const result = await call("GET", "/api/v1/purchase-requests/" + encodeURIComponent(id));
    if (result.status === 200) showRequest(result.data);
    showResult(result);
});

// --- the locked action ----------------------------------------------------

$("approve").addEventListener("click", async () => {
    const id = $("request-id").value.trim();
    if (!id) { $("request-id").focus(); return; }
    const result = await call("POST", "/api/v1/purchase-requests/" + encodeURIComponent(id) + "/approve");
    if (result.status === 200) {
        showRequest(result.data);
        showResult(result, `Approved. New status: ${result.data.status}.`);
        return;
    }
    if (result.status === 403) {
        // prove the row did not change: read it again (REQUESTER may read)
        const again = await call("GET", "/api/v1/purchase-requests/" + encodeURIComponent(id));
        if (again.status === 200) showRequest(again.data);
        const still = again.status === 200 ? `Row unchanged: status is still ${again.data.status}.` : "";
        showResult(result, still);
        return;
    }
    showResult(result);
});
