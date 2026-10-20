/*
 * پنل مدیریت وب سامانه بیمارستان ولیعصر.
 * بدون کتابخانه و بدون مرحله build؛ فقط از API همین سرور استفاده می‌کند.
 * همه مجوزها در سرور بررسی می‌شوند؛ این صفحه فقط منوهای بی‌فایده را پنهان می‌کند.
 * متن‌ها همیشه با textContent درج می‌شوند (نه innerHTML) تا داده کاربران اجرا نشود.
 */
"use strict";

const API = "/api/v1";

// ------------------------------------------------------------------ برچسب‌ها

const L = {
  incidentStatus: {
    submitted: "ثبت‌شده", under_review: "در حال بررسی", assigned: "ارجاع‌شده", in_progress: "در حال اقدام",
    action_taken: "اقدام انجام شد", closed: "بسته‌شده", rejected: "رد شده",
  },
  incidentKind: { error: "خطا", adverse_event: "حادثه", near_miss: "شبه‌خطا", equipment: "تجهیزات" },
  unitType: { ward: "بخش بستری", clinic: "درمانگاه", support: "پشتیبانی", admin: "اداری" },
  category: { news: "خبر", circular: "بخشنامه", guideline: "دستورالعمل", notice: "اطلاعیه" },
  audience: { staff: "کارکنان", public: "عمومی" },
  feedbackKind: { suggestion: "پیشنهاد", criticism: "انتقاد", complaint: "شکایت", praise: "قدردانی" },
  feedbackStatus: { received: "دریافت شد", reviewing: "در حال بررسی", answered: "پاسخ داده شد" },
};

// render(main, sub): sub بخش‌های بعدی نشانی است، مثلاً #/courses/<id>/stats → ["<id>", "stats"]
const PAGES = [
  { id: "dashboard", title: "داشبورد", icon: "dashboard", perm: "dashboard.read", render: renderDashboard },
  { id: "courses", title: "دوره‌های آموزشی", icon: "school", perm: "training.manage", render: renderCourses },
  { id: "exams", title: "آزمون‌ها", icon: "quiz", perm: "training.manage", render: renderExams },
  { id: "requests", title: "درخواست‌های پشتیبانی", icon: "build", perm: ["request.handle", "request.read"], render: renderRequests },
  { id: "request-types", title: "انواع درخواست", icon: "category", perm: "request.type.manage", render: renderRequestTypes },
  { id: "schedules", title: "برنامه شیفت", icon: "calendar", perm: ["schedule.write", "schedule.approve", "schedule.read.all"], render: renderSchedules },
  { id: "on-duty", title: "کشیک", icon: "clock", perm: ["schedule.read.all", "schedule.approve"], render: renderOnDuty },
  { id: "shift-codes", title: "تنظیمات شیفت", icon: "tune", perm: "schedule.config", render: renderShiftCodes },
  { id: "users", title: "کاربران", icon: "people", perm: "user.manage", render: renderUsers },
  { id: "units", title: "واحدها", icon: "units", perm: "unit.manage", render: renderUnits },
  { id: "announcements", title: "اطلاعیه‌ها", icon: "bell", perm: "announcement.manage", render: renderAnnouncements },
  { id: "faqs", title: "پرسش‌های متداول", icon: "help", perm: "faq.manage", render: renderFaqs },
  { id: "feedback", title: "پیشنهاد و شکایت", icon: "chat", perm: "feedback.manage", render: renderFeedback },
  { id: "audit", title: "لاگ حسابرسی", icon: "history", perm: "audit.read", render: renderAudit },
];

// ------------------------------------------------------------------ ابزارها

/** ساخت عنصر: h("div", {class: "x", onclick: fn}, "متن", child) */
function h(tag, props, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === "class") el.className = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (k === "value") el.value = v;
    else if (k === "checked" || k === "disabled" || k === "selected" || k === "required") el[k] = !!v;
    else el.setAttribute(k, v === true ? "" : String(v));
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

const fa = (n) => (n === null || n === undefined ? "—" : Number(n).toLocaleString("fa-IR"));
const faDigits = (s) => String(s).replace(/\d/g, (d) => "۰۱۲۳۴۵۶۷۸۹"[d]);

function fmtDate(iso, withTime = false) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const opts = withTime ? { dateStyle: "medium", timeStyle: "short" } : { dateStyle: "medium" };
  return new Intl.DateTimeFormat("fa-IR", opts).format(d);
}

function toast(message, isError = false) {
  const t = document.getElementById("toast");
  t.textContent = message;
  t.className = "show" + (isError ? " error" : "");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.className = ""), isError ? 6000 : 3000);
}

/** رمز موقت تصادفی: ۱۰ نویسه، حتماً با حرف و عدد. */
function randomPassword() {
  const letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
  const digits = "23456789";
  const all = letters + digits;
  const buf = new Uint32Array(10);
  crypto.getRandomValues(buf);
  const chars = Array.from(buf, (n) => all[n % all.length]);
  chars[0] = letters[buf[0] % letters.length];
  chars[9] = digits[buf[9] % digits.length];
  return chars.join("");
}

const store = {
  get(k) { try { return sessionStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { v === null ? sessionStorage.removeItem(k) : sessionStorage.setItem(k, v); } catch { /* حالت خصوصی */ } },
};

// ------------------------------------------------------------------ API

class ApiError extends Error {
  constructor(status, code, message, fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields || null;
  }
}

let refreshing = null;

async function refreshTokens() {
  const rt = store.get("refresh");
  if (!rt) return false;
  if (!refreshing) {
    refreshing = fetch(API + "/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: rt }),
    })
      .then(async (r) => {
        if (!r.ok) return false;
        const t = await r.json();
        store.set("access", t.access_token);
        store.set("refresh", t.refresh_token);
        return true;
      })
      .catch(() => false)
      .finally(() => setTimeout(() => (refreshing = null), 0));
  }
  return refreshing;
}

async function api(path, { method = "GET", body, retry = true } = {}) {
  const headers = {};
  const token = store.get("access");
  if (token) headers.Authorization = "Bearer " + token;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  let res;
  try {
    res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, "network", "ارتباط با سرور برقرار نشد");
  }
  if (res.status === 401 && retry && path !== "/auth/login") {
    if (await refreshTokens()) return api(path, { method, body, retry: false });
    sessionExpired();
    throw new ApiError(401, "session_expired", "نشست منقضی شده است؛ دوباره وارد شوید");
  }
  if (res.status === 204) return null;
  let data = null;
  try { data = await res.json(); } catch { /* پاسخ بدون بدنه */ }
  if (!res.ok) {
    const d = data && data.detail;
    if (d && d.code === "password_change_required") {
      renderChangePassword(true);
    }
    const message = (d && (d.message || (typeof d === "string" ? d : null))) || "خطای سرور (" + res.status + ")";
    throw new ApiError(res.status, d && d.code, message, d && d.fields);
  }
  return data;
}

function showError(e) {
  let msg = e && e.message ? e.message : "خطای ناشناخته";
  if (e && e.fields) msg += ": " + Object.values(e.fields).join("، ");
  toast(msg, true);
}

// ------------------------------------------------------------------ وضعیت و مسیریابی

const state = { me: null, roles: null, units: null, jobTitles: new Set() };
const can = (perm) =>
  !!state.me && (Array.isArray(perm) ? perm.some((p) => state.me.permissions.includes(p)) : state.me.permissions.includes(perm));
const root = () => document.getElementById("app");

async function catalog() {
  if (!state.roles) state.roles = await api("/admin/roles");
  if (!state.units) state.units = await api("/admin/units");
  return { roles: state.roles, units: state.units };
}
const roleName = (code) => (state.roles || []).find((r) => r.code === code)?.name || code;
const unitName = (id) => (id ? (state.units || []).find((u) => u.id === id)?.name || "—" : "—");

function sessionExpired() {
  store.set("access", null);
  store.set("refresh", null);
  state.me = null;
  renderLogin("نشست شما تمام شده است؛ دوباره وارد شوید.");
}

async function logout() {
  const rt = store.get("refresh");
  if (rt) await api("/auth/logout", { method: "POST", body: { refresh_token: rt }, retry: false }).catch(() => {});
  store.set("access", null);
  store.set("refresh", null);
  state.me = state.roles = state.units = null;
  peopleCache = null;
  location.hash = "";
  renderLogin();
}

async function start() {
  if (!store.get("access") && !store.get("refresh")) return renderLogin();
  try {
    state.me = await api("/auth/me");
  } catch {
    return renderLogin();
  }
  if (state.me.must_change_password) return renderChangePassword(true);
  route();
}

window.addEventListener("hashchange", () => state.me && !state.me.must_change_password && route());

function route() {
  const pages = PAGES.filter((p) => can(p.perm));
  if (!pages.length) return renderNoAccess();
  const [id, ...sub] = location.hash.replace(/^#\/?/, "").split("/").filter(Boolean);
  let page = pages.find((p) => p.id === id);
  if (!page) {
    page = pages[0];
    sub.length = 0;
    history.replaceState(null, "", "#/" + page.id);
  }
  const main = h("main", { class: "main" });
  root().replaceChildren(layout(pages, page));
  root().firstChild.append(main);
  document.title = page.title + " | پنل مدیریت ولیعصر";
  main.append(h("div", { class: "loading" }, h("span", { class: "spinner" }), "در حال بارگذاری…"));
  page.render(main, sub).catch((e) => {
    main.replaceChildren(h("div", { class: "card error" }, e.message || "خطا در بارگذاری"));
  });
}

function layout(pages, current) {
  return h(
    "div",
    { class: "layout" },
    h(
      "aside",
      { class: "sidebar" },
      h("div", { class: "brand" }, h("div", { class: "logo" }, icon("school")),
        h("div", {}, h("div", {}, "بیمارستان ولیعصر"), h("div", { class: "brand-sub" }, "پنل مدیریت"))),
      h("div", { class: "who" }, h("div", { class: "avatar" }, state.me.full_name.trim().charAt(0)),
        h("div", { class: "grow" }, h("div", {}, state.me.full_name),
          h("div", { class: "brand-sub" }, [state.me.job_title, faDigits(state.me.personnel_code)].filter(Boolean).join(" · "))),
        bellButton()),
      h("nav", {}, pages.map((p) => h("a", { href: "#/" + p.id, class: p.id === current.id ? "active" : null }, icon(p.icon), h("span", {}, p.title)))),
      h("div", { class: "spacer" }),
      h("div", { class: "logout" }, h("button", { class: "btn secondary", onclick: logout }, icon("logout"), h("span", {}, "خروج"))),
    ),
  );
}

function renderNoAccess() {
  root().replaceChildren(
    h("div", { class: "login-page" },
      h("div", { class: "card login-box" },
        h("h2", {}, "دسترسی ندارید"),
        h("p", {}, "حساب شما به هیچ بخشی از پنل مدیریت دسترسی ندارد. کارهای روزانه کارکنان از اپ موبایل انجام می‌شود."),
        h("button", { class: "btn", onclick: logout }, "خروج"))),
  );
}

// ------------------------------------------------------------------ ورود و تغییر رمز

function renderLogin(notice) {
  const code = h("input", { type: "text", autocomplete: "username", required: true, dir: "ltr" });
  const pass = h("input", { type: "password", autocomplete: "current-password", required: true, dir: "ltr" });
  const err = h("p", { class: "error" }, notice && !notice.startsWith("نشست") ? notice : "");
  const btn = h("button", { class: "btn", type: "submit" }, "ورود");
  const form = h("form", { class: "form", onsubmit: async (ev) => {
    ev.preventDefault();
    btn.disabled = true;
    err.textContent = "";
    try {
      const t = await api("/auth/login", {
        method: "POST",
        body: { personnel_code: code.value.trim(), password: pass.value, device: "پنل مدیریت وب" },
        retry: false,
      });
      store.set("access", t.access_token);
      store.set("refresh", t.refresh_token);
      state.me = await api("/auth/me");
      if (state.me.must_change_password) renderChangePassword(true);
      else route();
    } catch (e) {
      err.textContent = e.message;
    } finally {
      btn.disabled = false;
    }
  } },
    h("label", {}, h("span", {}, "کد پرسنلی"), code),
    h("label", {}, h("span", {}, "رمز عبور"), pass),
    err,
    btn,
  );
  root().replaceChildren(
    h("div", { class: "login-page" },
      h("div", { class: "card login-box" },
        h("h1", {}, "پنل مدیریت بیمارستان ولیعصر"),
        notice && notice.startsWith("نشست") ? h("p", { class: "muted" }, notice) : null,
        form)),
  );
  code.focus();
}

function renderChangePassword(forced) {
  const cur = h("input", { type: "password", autocomplete: "current-password", required: true, dir: "ltr" });
  const n1 = h("input", { type: "password", autocomplete: "new-password", required: true, dir: "ltr" });
  const n2 = h("input", { type: "password", autocomplete: "new-password", required: true, dir: "ltr" });
  const err = h("p", { class: "error" });
  const form = h("form", { class: "form", onsubmit: async (ev) => {
    ev.preventDefault();
    err.textContent = "";
    if (n1.value.length < 8) return (err.textContent = "رمز جدید باید حداقل ۸ نویسه باشد");
    if (n1.value !== n2.value) return (err.textContent = "تکرار رمز جدید یکسان نیست");
    try {
      await api("/auth/change-password", { method: "POST", body: { current_password: cur.value, new_password: n1.value } });
      state.me = await api("/auth/me");
      toast("رمز عبور تغییر کرد");
      route();
    } catch (e) {
      err.textContent = e.message;
    }
  } },
    h("p", {}, forced ? "برای ادامه، رمز موقت را به یک رمز شخصی تغییر دهید. رمز جدید حداقل ۸ نویسه و ترکیبی از حرف و عدد باشد." : ""),
    h("label", {}, h("span", {}, "رمز فعلی"), cur),
    h("label", {}, h("span", {}, "رمز جدید"), n1),
    h("label", {}, h("span", {}, "تکرار رمز جدید"), n2),
    err,
    h("button", { class: "btn", type: "submit" }, "ذخیره"),
    h("button", { class: "btn secondary", type: "button", onclick: logout }, "خروج"),
  );
  root().replaceChildren(h("div", { class: "login-page" }, h("div", { class: "card login-box" }, h("h2", {}, "تغییر رمز عبور"), form)));
}

// ------------------------------------------------------------------ پنجره و جدول

/** پنجره فرم. onSave باید true برگرداند تا پنجره بسته شود. */
function dialog(title, body, { saveLabel = "ذخیره", onSave, extraButtons = [], wide = false } = {}) {
  const dlg = h("dialog", { class: wide ? "wide" : null });
  const err = h("p", { class: "error" });
  const saveBtn = onSave ? h("button", { class: "btn", type: "submit" }, saveLabel) : null;
  const form = h("form", { method: "dialog", onsubmit: async (ev) => {
    ev.preventDefault();
    if (!onSave) return dlg.close();
    err.textContent = "";
    saveBtn.disabled = true;
    try {
      if ((await onSave()) !== false) dlg.close();
    } catch (e) {
      err.textContent = e.message + (e.fields ? ": " + Object.values(e.fields).join("، ") : "");
    } finally {
      saveBtn.disabled = false;
    }
  } },
    h("div", { class: "dlg-head" }, title),
    h("div", { class: "dlg-body" }, body, err),
    h("div", { class: "dlg-foot" },
      saveBtn,
      extraButtons,
      h("button", { class: "btn secondary", type: "button", onclick: () => dlg.close() }, onSave ? "انصراف" : "بستن")),
  );
  dlg.append(form);
  dlg.addEventListener("close", () => dlg.remove());
  document.body.append(dlg);
  dlg.showModal();
  return dlg;
}

function table(headers, rows, onRowClick) {
  return h("div", { class: "table-wrap" },
    h("table", {},
      h("thead", {}, h("tr", {}, headers.map((t) => h("th", {}, t)))),
      h("tbody", {},
        rows.length
          ? rows.map((r) => h("tr", { class: onRowClick ? "clickable" : null, onclick: onRowClick ? () => onRowClick(r.item) : null },
              r.cells.map((c) => h("td", {}, c))))
          : h("tr", {}, h("td", { colspan: headers.length, class: "muted" }, "موردی نیست")))));
}

const field = (label, input) => h("label", {}, h("span", {}, label), input);
const select = (options, value) =>
  h("select", {}, options.map(([v, t]) => h("option", { value: v, selected: v === value }, t)));
const checkbox = (label, checked) => {
  const input = h("input", { type: "checkbox", checked });
  return { input, el: h("label", { class: "check" }, input, label) };
};

// ------------------------------------------------------------------ داشبورد

async function renderDashboard(main) {
  const days = select([["30", "۳۰ روز اخیر"], ["90", "۹۰ روز اخیر"], ["365", "یک سال اخیر"], ["3650", "همه"]], "365");
  const box = h("div");
  async function load() {
    box.replaceChildren(h("p", { class: "muted" }, "در حال بارگذاری…"));
    const s = await api("/dashboard/incidents/summary?days=" + days.value);
    const bars = (title, entries) => {
      const max = Math.max(1, ...entries.map((e) => e[1]));
      return h("div", { class: "card" }, h("h3", {}, title),
        entries.length
          ? entries.map(([label, n]) => {
              const fill = h("div");
              fill.style.width = (100 * n) / max + "%";
              return h("div", { class: "bar-row" }, h("span", {}, label), h("div", { class: "bar" }, fill), h("span", {}, fa(n)));
            })
          : h("p", { class: "muted" }, "داده‌ای نیست"));
    };
    box.replaceChildren(
      h("div", { class: "stats" },
        h("div", { class: "stat" }, h("div", { class: "muted" }, "کل گزارش‌ها"), h("div", { class: "value" }, fa(s.total))),
        h("div", { class: "stat" }, h("div", { class: "muted" }, "باز (بسته‌نشده)"),
          h("div", { class: "value" }, fa(s.total - (s.by_status.closed || 0) - (s.by_status.rejected || 0)))),
        h("div", { class: "stat" }, h("div", { class: "muted" }, "میانگین زمان تا بستن"),
          h("div", { class: "value" }, s.avg_hours_to_close === null ? "—" : fa(s.avg_hours_to_close) + " ساعت"))),
      h("div", { class: "grid2" },
        bars("بر اساس وضعیت", Object.entries(s.by_status).map(([k, n]) => [L.incidentStatus[k] || k, n])),
        bars("بر اساس نوع", Object.entries(s.by_kind).map(([k, n]) => [L.incidentKind[k] || k, n])),
        bars("بر اساس واحد", s.by_unit.map((u) => [u.unit_name, u.count])),
        bars("بر اساس ماه (میلادی)", Object.entries(s.by_month).map(([m, n]) => [faDigits(m), n]))),
      h("p", { class: "muted" }, "فقط آمار تجمیعی نمایش داده می‌شود؛ جزئیات و هویت افراد در این صفحه نیست."),
    );
  }
  days.addEventListener("change", () => load().catch(showError));
  main.replaceChildren(h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "داشبورد گزارش‌های خطا"), days), box);
  await load();
}

// ------------------------------------------------------------------ کاربران

async function renderUsers(main) {
  await catalog();
  const q = h("input", { type: "search", placeholder: "جستجوی نام یا کد پرسنلی" });
  const list = h("div");
  async function load() {
    const users = await api("/admin/users" + (q.value.trim() ? "?q=" + encodeURIComponent(q.value.trim()) : ""));
    users.forEach((u) => u.job_title && state.jobTitles.add(u.job_title));
    list.replaceChildren(table(
      ["کد پرسنلی", "نام", "واحد", "سمت", "نقش‌ها", "وضعیت", ""],
      users.map((u) => ({
        item: u,
        cells: [
          faDigits(u.personnel_code),
          u.full_name,
          unitName(u.unit_id),
          u.job_title || h("span", { class: "muted" }, "—"),
          u.roles.map((r) => h("span", { class: "tag" }, roleName(r.role) + (r.unit_id ? " (" + unitName(r.unit_id) + ")" : ""))),
          [u.is_active ? h("span", { class: "tag" }, "فعال") : h("span", { class: "tag danger" }, "غیرفعال"),
            u.must_change_password ? h("span", { class: "tag gray" }, "رمز موقت") : null],
          h("div", { class: "actions" },
            h("button", { class: "btn small secondary", onclick: (e) => { e.stopPropagation(); userDialog(u, load); } }, "ویرایش"),
            h("button", { class: "btn small secondary", onclick: (e) => { e.stopPropagation(); resetPasswordDialog(u); } }, "رمز موقت")),
        ],
      })),
    ));
  }
  let timer;
  q.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => load().catch(showError), 300); });
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "کاربران"), q,
      h("button", { class: "btn", onclick: () => userDialog(null, load) }, "کاربر جدید")),
    list,
  );
  await load();
}

function roleRows(initial) {
  const box = h("div", { class: "form" });
  const rows = [];
  const unitOpts = [["", "همه واحدها"], ...state.units.map((u) => [u.id, u.name])];
  const roleOpts = state.roles.map((r) => [r.code, r.name]);
  function add(a) {
    const role = select(roleOpts, a ? a.role : roleOpts[0][0]);
    const unit = select(unitOpts, a && a.unit_id ? a.unit_id : "");
    const row = h("div", { class: "row" }, role,
      h("div", { class: "toolbar" }, unit,
        h("button", { class: "btn small danger", type: "button", onclick: () => { rows.splice(rows.indexOf(entry), 1); row.remove(); } }, "حذف")));
    const entry = { role, unit };
    rows.push(entry);
    box.append(row);
  }
  (initial || []).forEach(add);
  return {
    el: h("div", {}, h("span", { class: "muted" }, "نقش‌ها (دامنه «همه واحدها» یعنی مجوز برای کل بیمارستان)"), box,
      h("button", { class: "btn small secondary", type: "button", onclick: () => add(null) }, "افزودن نقش")),
    value: () => rows.map((r) => ({ role: r.role.value, unit_id: r.unit.value || null })),
  };
}

function userDialog(u, reload) {
  const isNew = !u;
  const code = h("input", { type: "text", required: true, dir: "ltr", value: u ? u.personnel_code : "", disabled: !isNew });
  const name = h("input", { type: "text", required: true, value: u ? u.full_name : "" });
  const mobile = h("input", { type: "text", dir: "ltr", placeholder: "09xxxxxxxxx", value: u && u.mobile ? u.mobile : "" });
  const unit = select([["", "— بدون واحد —"], ...state.units.map((x) => [x.id, x.name])], u && u.unit_id ? u.unit_id : "");
  const titleList = h("datalist", { id: "job-titles" }, [...state.jobTitles].sort().map((t) => h("option", { value: t })));
  const jobTitle = h("input", { type: "text", list: "job-titles", maxlength: "100", placeholder: "مثلاً پرستار، پزشک، بهیار", value: u && u.job_title ? u.job_title : "" });
  const pass = h("input", { type: "text", dir: "ltr", value: isNew ? randomPassword() : "" });
  const active = checkbox("حساب فعال باشد", u ? u.is_active : true);
  const roles = roleRows(u ? u.roles : [{ role: "staff", unit_id: null }]);
  const body = h("div", { class: "form" },
    h("div", { class: "row" }, field("کد پرسنلی", code), field("نام و نام خانوادگی", name)),
    h("div", { class: "row" }, field("موبایل (اختیاری)", mobile), field("واحد محل کار", unit)),
    field("سمت (برای آمار و هدف‌گذاری آموزش)", h("div", {}, jobTitle, titleList)),
    isNew ? field("رمز موقت (کاربر در اولین ورود باید عوضش کند)", pass) : active.el,
    roles.el,
  );
  dialog(isNew ? "کاربر جدید" : "ویرایش کاربر", body, {
    onSave: async () => {
      const payload = {
        full_name: name.value.trim(),
        mobile: mobile.value.trim() || null,
        unit_id: unit.value || null,
        job_title: jobTitle.value.trim() || null,
        roles: roles.value(),
      };
      if (isNew) {
        await api("/admin/users", { method: "POST", body: { ...payload, personnel_code: code.value.trim(), temporary_password: pass.value } });
        dialog("کاربر ساخته شد", h("div", { class: "form" },
          h("p", {}, "این اطلاعات را به کاربر بدهید. رمز موقت دیگر نمایش داده نمی‌شود."),
          h("p", {}, "کد پرسنلی: ", h("b", { class: "mono" }, code.value.trim())),
          h("p", {}, "رمز موقت: ", h("b", { class: "mono" }, pass.value))));
      } else {
        await api("/admin/users/" + u.id, { method: "PATCH", body: { ...payload, is_active: active.input.checked } });
        toast("ذخیره شد");
      }
      await reload();
    },
  });
}

function resetPasswordDialog(u) {
  const pass = h("input", { type: "text", dir: "ltr", value: randomPassword() });
  dialog("رمز موقت جدید برای " + u.full_name, h("div", { class: "form" },
    h("p", { class: "muted" }, "همه تلاش‌های ناموفق و قفل حساب هم پاک می‌شود. کاربر در ورود بعدی باید رمز را عوض کند."),
    field("رمز موقت", pass)), {
    saveLabel: "ثبت رمز موقت",
    onSave: async () => {
      await api("/admin/users/" + u.id + "/reset-password", { method: "POST", body: { temporary_password: pass.value } });
      dialog("رمز موقت ثبت شد", h("p", {}, "رمز موقت: ", h("b", { class: "mono" }, pass.value)));
    },
  });
}

// ------------------------------------------------------------------ واحدها

async function renderUnits(main) {
  async function load() {
    state.units = await api("/admin/units");
    return state.units;
  }
  const list = h("div");
  async function draw() {
    const units = await load();
    list.replaceChildren(table(
      ["نام", "نوع", "در راهنمای عمومی", "مکان", "تلفن", ""],
      units.map((u) => ({
        item: u,
        cells: [u.name, L.unitType[u.type] || u.type, u.is_public ? "بله" : "خیر", u.location || "—",
          u.phone ? faDigits(u.phone) : "—",
          h("button", { class: "btn small secondary", onclick: (e) => { e.stopPropagation(); unitDialog(u, draw); } }, "ویرایش")],
      })),
    ));
  }
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "واحدها"),
      h("button", { class: "btn", onclick: () => unitDialog(null, draw) }, "واحد جدید")),
    list,
  );
  await draw();
}

function unitDialog(u, reload) {
  const name = h("input", { type: "text", required: true, value: u ? u.name : "" });
  const type = select(Object.entries(L.unitType), u ? u.type : "ward");
  const pub = checkbox("در راهنمای بخش‌ها برای مراجعان نمایش داده شود", u ? u.is_public : false);
  const location_ = h("input", { type: "text", value: u && u.location ? u.location : "" });
  const phone = h("input", { type: "text", dir: "ltr", value: u && u.phone ? u.phone : "" });
  const hours = h("input", { type: "text", value: u && u.hours ? u.hours : "" });
  const desc = h("textarea", {}, u && u.description ? u.description : "");
  dialog(u ? "ویرایش واحد" : "واحد جدید", h("div", { class: "form" },
    h("div", { class: "row" }, field("نام واحد", name), field("نوع", type)),
    pub.el,
    h("div", { class: "row" }, field("مکان (طبقه/ساختمان)", location_), field("تلفن داخلی", phone)),
    field("ساعات کار", hours),
    field("توضیح", desc)), {
    onSave: async () => {
      const body = {
        name: name.value.trim(), type: type.value, is_public: pub.input.checked,
        location: location_.value.trim() || null, phone: phone.value.trim() || null,
        hours: hours.value.trim() || null, description: desc.value.trim() || null,
        parent_id: u ? u.parent_id : null,
      };
      if (u) await api("/admin/units/" + u.id, { method: "PUT", body });
      else await api("/admin/units", { method: "POST", body });
      toast("ذخیره شد");
      await reload();
    },
  });
}

// ------------------------------------------------------------------ اطلاعیه‌ها

async function renderAnnouncements(main) {
  await catalog();
  const list = h("div");
  async function load() {
    const items = await api("/admin/announcements");
    const now = Date.now();
    list.replaceChildren(table(
      ["عنوان", "نوع", "مخاطب", "انتشار", "وضعیت", ""],
      items.map((a) => {
        const expired = a.expires_at && new Date(a.expires_at).getTime() <= now;
        const scheduled = new Date(a.published_at).getTime() > now;
        return {
          item: a,
          cells: [
            [a.title, a.is_important ? h("span", { class: "tag danger" }, "مهم") : null,
              a.requires_ack ? h("span", { class: "tag" }, "نیاز به تأیید مطالعه") : null],
            L.category[a.category] || a.category,
            L.audience[a.audience] || a.audience,
            fmtDate(a.published_at),
            expired ? h("span", { class: "tag gray" }, "منقضی") : scheduled ? h("span", { class: "tag gray" }, "زمان‌بندی‌شده")
              : h("span", { class: "tag" }, a.expires_at ? "فعال تا " + fmtDate(a.expires_at) : "فعال"),
            h("div", { class: "actions" },
              h("button", { class: "btn small secondary", onclick: (e) => { e.stopPropagation(); showAnnouncement(a); } }, "متن"),
              a.audience === "staff"
                ? h("button", { class: "btn small secondary", onclick: (e) => { e.stopPropagation(); readStats(a); } }, "آمار مطالعه")
                : null,
              expired ? null : h("button", { class: "btn small danger", onclick: async (e) => {
                e.stopPropagation();
                if (!confirm("این اطلاعیه منقضی شود و دیگر نمایش داده نشود؟")) return;
                try {
                  await api("/admin/announcements/" + a.id, { method: "DELETE" });
                  toast("منقضی شد");
                  await load();
                } catch (err) { showError(err); }
              } }, "پایان انتشار")),
          ],
        };
      }),
    ));
  }
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "اطلاعیه‌ها و بخشنامه‌ها"),
      h("button", { class: "btn", onclick: () => announcementDialog(load) }, "اطلاعیه جدید")),
    list,
  );
  await load();
}

function showAnnouncement(a) {
  dialog(a.title, h("div", {}, h("p", { class: "muted" }, fmtDate(a.published_at, true)), h("div", { class: "pre" }, a.body)));
}

async function readStats(a) {
  try {
    const s = await api("/admin/announcements/" + a.id + "/reads");
    dialog("آمار مطالعه: " + a.title, h("div", {},
      h("div", { class: "stats" },
        h("div", { class: "stat" }, h("div", { class: "muted" }, "مخاطبان"), h("div", { class: "value" }, fa(s.targeted))),
        h("div", { class: "stat" }, h("div", { class: "muted" }, "خوانده‌اند"), h("div", { class: "value" }, fa(s.read))),
        h("div", { class: "stat" }, h("div", { class: "muted" }, "تأیید کرده‌اند"), h("div", { class: "value" }, fa(s.acknowledged)))),
      a.requires_ack
        ? [h("h3", {}, "هنوز تأیید نکرده‌اند"),
          s.not_acknowledged.length ? h("ul", {}, s.not_acknowledged.map((n) => h("li", {}, n))) : h("p", { class: "ok" }, "همه تأیید کرده‌اند")]
        : null));
  } catch (e) { showError(e); }
}

function announcementDialog(reload) {
  const title = h("input", { type: "text", required: true });
  const body = h("textarea", { required: true });
  const category = select(Object.entries(L.category), "news");
  const audience = select(Object.entries(L.audience), "staff");
  const important = checkbox("مهم (بالای فهرست نمایش داده شود)", false);
  const ack = checkbox("کارکنان باید مطالعه را تأیید کنند", false);
  const days = h("input", { type: "number", min: "1", max: "3650", placeholder: "بدون انقضا" });
  const roleChecks = state.roles.map((r) => ({ code: r.code, ...checkbox(r.name, false) }));
  const unitChecks = state.units.map((u) => ({ id: u.id, ...checkbox(u.name, false) }));
  const staffOnly = h("div", { class: "form" },
    h("div", {}, h("span", { class: "muted" }, "فقط برای این نقش‌ها (هیچ‌کدام = همه کارکنان)"),
      h("div", { class: "checklist" }, roleChecks.map((c) => c.el))),
    h("div", {}, h("span", { class: "muted" }, "فقط برای این واحدها (هیچ‌کدام = همه واحدها)"),
      h("div", { class: "checklist" }, unitChecks.map((c) => c.el))),
    ack.el);
  const syncAudience = () => { staffOnly.hidden = audience.value !== "staff"; };
  audience.addEventListener("change", syncAudience);
  syncAudience();
  dialog("اطلاعیه جدید", h("div", { class: "form" },
    field("عنوان", title),
    field("متن", body),
    h("div", { class: "row" }, field("نوع", category), field("مخاطب", audience)),
    important.el,
    staffOnly,
    field("مدت نمایش (روز)", days)), {
    saveLabel: "انتشار",
    onSave: async () => {
      const staff = audience.value === "staff";
      const d = parseInt(days.value, 10);
      await api("/admin/announcements", { method: "POST", body: {
        title: title.value.trim(),
        body: body.value.trim(),
        category: category.value,
        audience: audience.value,
        is_important: important.input.checked,
        requires_ack: staff && ack.input.checked,
        target_role_codes: staff ? roleChecks.filter((c) => c.input.checked).map((c) => c.code) : [],
        target_unit_ids: staff ? unitChecks.filter((c) => c.input.checked).map((c) => c.id) : [],
        expires_at: d > 0 ? new Date(Date.now() + d * 86400000).toISOString() : null,
      } });
      toast("منتشر شد");
      await reload();
    },
  });
}

// ------------------------------------------------------------------ پرسش‌های متداول

async function renderFaqs(main) {
  const list = h("div");
  async function load() {
    const faqs = await api("/public/faqs");
    list.replaceChildren(table(
      ["پرسش", "پاسخ", "دسته", "ترتیب", ""],
      faqs.map((f) => ({
        item: f,
        cells: [f.question, h("div", { class: "pre" }, f.answer), f.category || "—", fa(f.sort_order),
          h("button", { class: "btn small danger", onclick: async () => {
            if (!confirm("این پرسش حذف شود؟")) return;
            try {
              await api("/admin/faqs/" + f.id, { method: "DELETE" });
              toast("حذف شد");
              await load();
            } catch (e) { showError(e); }
          } }, "حذف")],
      })),
    ));
  }
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "پرسش‌های متداول مراجعان"),
      h("button", { class: "btn", onclick: () => faqDialog(load) }, "پرسش جدید")),
    list,
  );
  await load();
}

function faqDialog(reload) {
  const q = h("input", { type: "text", required: true });
  const a = h("textarea", { required: true });
  const cat = h("input", { type: "text", placeholder: "مثلاً ملاقات، پذیرش" });
  const order = h("input", { type: "number", value: "0" });
  dialog("پرسش جدید", h("div", { class: "form" }, field("پرسش", q), field("پاسخ", a),
    h("div", { class: "row" }, field("دسته (اختیاری)", cat), field("ترتیب نمایش", order))), {
    onSave: async () => {
      await api("/admin/faqs", { method: "POST", body: {
        question: q.value.trim(), answer: a.value.trim(), category: cat.value.trim() || null,
        sort_order: parseInt(order.value, 10) || 0,
      } });
      toast("ذخیره شد");
      await reload();
    },
  });
}

// ------------------------------------------------------------------ پیشنهاد و شکایت

async function renderFeedback(main) {
  await catalog();
  const status = select([["", "همه"], ...Object.entries(L.feedbackStatus)], "received");
  const list = h("div");
  async function load() {
    const items = await api("/admin/feedback" + (status.value ? "?status=" + status.value : ""));
    list.replaceChildren(table(
      ["کد پیگیری", "نوع", "تاریخ", "بخش", "وضعیت", "متن"],
      items.map((f) => ({
        item: f,
        cells: [h("span", { class: "mono" }, f.tracking_code), L.feedbackKind[f.kind] || f.kind, fmtDate(f.created_at),
          unitName(f.unit_id), L.feedbackStatus[f.status] || f.status,
          f.body.length > 80 ? f.body.slice(0, 80) + "…" : f.body],
      })),
      (f) => feedbackDialog(f, load),
    ));
  }
  status.addEventListener("change", () => load().catch(showError));
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "پیشنهادها و شکایات مراجعان"), h("span", { class: "muted" }, "وضعیت:"), status),
    h("p", { class: "muted" }, "برای دیدن متن کامل و پاسخ‌دادن روی ردیف بزنید. پاسخ شما با کد پیگیری به مراجع نمایش داده می‌شود."),
    list,
  );
  await load();
}

function feedbackDialog(f, reload) {
  const st = select([["reviewing", "در حال بررسی"], ["answered", "پاسخ داده شد"]], f.status === "answered" ? "answered" : "reviewing");
  const resp = h("textarea", {}, f.response || "");
  dialog((L.feedbackKind[f.kind] || f.kind) + " — " + f.tracking_code, h("div", { class: "form" },
    h("p", { class: "muted" }, fmtDate(f.created_at, true), " · بخش: ", unitName(f.unit_id)),
    f.name || f.mobile ? h("p", {}, "فرستنده: ", f.name || "—", " · ", f.mobile ? faDigits(f.mobile) : "بدون شماره") : h("p", { class: "muted" }, "فرستنده نام و شماره نداده است."),
    h("div", { class: "card pre" }, f.body),
    field("وضعیت", st),
    field("پاسخ به مراجع", resp)), {
    onSave: async () => {
      await api("/admin/feedback/" + f.id + "/respond", { method: "POST", body: { status: st.value, response: resp.value.trim() || null } });
      toast("ذخیره شد");
      await reload();
    },
  });
}

// ------------------------------------------------------------------ لاگ حسابرسی

async function renderAudit(main) {
  const action = h("input", { type: "text", dir: "ltr", placeholder: "مثلاً auth.login_failed" });
  const entity = h("input", { type: "text", dir: "ltr", placeholder: "شناسه موجودیت" });
  const list = h("div");
  const pager = h("div", { class: "pager" });
  let page = 1;
  const size = 50;
  async function load() {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (action.value.trim()) params.set("action", action.value.trim());
    if (entity.value.trim()) params.set("entity_id", entity.value.trim());
    const res = await api("/admin/audit?" + params);
    list.replaceChildren(table(
      ["زمان", "رویداد", "کاربر", "موجودیت", "IP", "جزئیات"],
      res.items.map((r) => ({
        item: r,
        cells: [fmtDate(r.at, true), h("span", { class: "mono" }, r.action),
          r.actor_id ? h("span", { class: "mono", title: r.actor_id }, r.actor_id.slice(0, 8)) : "—",
          r.entity_type ? [r.entity_type, " ", h("span", { class: "mono", title: r.entity_id || "" }, (r.entity_id || "").slice(0, 8))] : "—",
          h("span", { class: "mono" }, r.ip || "—"),
          r.details && Object.keys(r.details).length ? h("span", { class: "mono" }, JSON.stringify(r.details)) : "—"],
      })),
    ));
    const pages = Math.max(1, Math.ceil(res.total / size));
    pager.replaceChildren(
      h("button", { class: "btn small secondary", disabled: page <= 1, onclick: () => { page--; load().catch(showError); } }, "قبلی"),
      h("span", { class: "muted" }, "صفحه " + fa(page) + " از " + fa(pages) + " (" + fa(res.total) + " رویداد)"),
      h("button", { class: "btn small secondary", disabled: page >= pages, onclick: () => { page++; load().catch(showError); } }, "بعدی"),
    );
  }
  const search = h("form", { class: "toolbar", onsubmit: (e) => { e.preventDefault(); page = 1; load().catch(showError); } },
    action, entity, h("button", { class: "btn", type: "submit" }, "جستجو"));
  main.replaceChildren(h("h1", {}, "لاگ حسابرسی"), search, list, pager);
  await load();
}

// ------------------------------------------------------------------

document.addEventListener("DOMContentLoaded", start);
