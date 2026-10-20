/*
 * بخش آموزش پنل مدیریت: دوره‌ها، محتوا، آزمون‌ها، نتایج و آمار.
 * پیش از app.js بارگذاری می‌شود تا PAGES بتواند به توابع این فایل اشاره کند؛
 * ابزارهای مشترک (h، api، dialog، ...) در زمان اجرا از app.js خوانده می‌شوند.
 */
"use strict";

// ------------------------------------------------------------------ آیکون‌ها (Material Icons، Apache 2.0)

const ICONS = {
  school: "M5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82zM12 3 1 9l11 6 9-4.91V17h2V9L12 3z",
  quiz: "M19 3h-4.18C14.4 1.84 13.3 1 12 1s-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm2 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z",
  people: "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
  dashboard: "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z",
  video: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z",
  image: "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
  audio: "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z",
  pdf: "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z",
  pptx: "M10 8v8l5-4-5-4zm9-5H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z",
  file: "M6 2c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6H6zm7 7V3.5L18.5 9H13z",
  link: "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z",
  text: "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z",
  upload: "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z",
  bell: "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
  units: "M12 7V3H2v18h20V7H12zM6 19H4v-2h2v2zm0-4H4v-2h2v2zm0-4H4V9h2v2zm0-4H4V5h2v2zm4 12H8v-2h2v2zm0-4H8v-2h2v2zm0-4H8V9h2v2zm0-4H8V5h2v2zm10 12h-8v-2h2v-2h-2v-2h2v-2h-2V9h8v10zm-2-8h-2v2h2v-2zm0 4h-2v2h2v-2z",
  help: "M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z",
  chat: "M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 12h-2v-2h2v2zm0-4h-2V6h2v4z",
  history: "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z",
  logout: "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z",
  add: "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
  download: "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
  edit: "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
  delete: "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
  up: "M7 14l5-5 5 5z",
  down: "M7 10l5 5 5-5z",
  back: "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z",
  eye: "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z",
  lock: "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
  clock: "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
  check: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
  user: "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
};

// مثل replaceChildren ولی null/false را رد می‌کند (وگرنه متن «null» در صفحه می‌آید).
function fill(el, ...kids) {
  el.replaceChildren(...kids.filter((k) => k != null && k !== false));
}

function icon(name, cls) {
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute("class", "icon" + (cls ? " " + cls : ""));
  const path = document.createElementNS(ns, "path");
  path.setAttribute("d", ICONS[name] || ICONS.file);
  svg.append(path);
  return svg;
}

// ------------------------------------------------------------------ برچسب‌ها

const TL = {
  kind: {
    text: "متن", image: "عکس", video: "فیلم", audio: "صوت", pdf: "PDF", pptx: "پاورپوینت", file: "فایل", link: "لینک",
  },
  courseStatus: { draft: "پیش‌نویس", published: "منتشرشده", archived: "بایگانی" },
  examState: { draft: "پیش‌نویس", upcoming: "زمان‌بندی‌شده", open: "باز", closed: "بسته" },
  manual: { auto: "طبق زمان‌بندی", open: "باز (دستی)", closed: "بسته (دستی)" },
  qkind: { single: "تک‌گزینه‌ای", multiple: "چندگزینه‌ای", true_false: "درست / غلط" },
  showAnswers: { never: "هرگز", after_submit: "بلافاصله پس از ثبت", after_close: "پس از بسته شدن آزمون" },
};

// پسوندهای مجاز هر نوع (همان فهرست سرور)
const KIND_ACCEPT = {
  image: ".jpg,.jpeg,.png,.webp,.gif",
  video: ".mp4,.m4v,.webm,.mov",
  audio: ".mp3,.m4a,.aac,.ogg,.wav",
  pdf: ".pdf",
  pptx: ".ppt,.pptx,.pps,.ppsx",
  file: ".doc,.docx,.xls,.xlsx,.txt,.zip,.pdf,.ppt,.pptx",
};

const fmtSize = (n) =>
  n >= 1048576 ? fa((n / 1048576).toFixed(1)) + " مگابایت" : fa(Math.max(1, Math.round(n / 1024))) + " کیلوبایت";
const fmtMinutes = (sec) => (sec < 60 ? fa(sec) + " ثانیه" : fa(Math.round(sec / 60)) + " دقیقه");
const pct = (a, b) => (b ? Math.round((100 * a) / b) : 0);

// ------------------------------------------------------------------ اجزای مشترک

function pageHead(title, subtitle, ...actions) {
  return h("div", { class: "page-head" },
    h("div", { class: "grow" }, h("h1", {}, title), subtitle ? h("p", { class: "muted" }, subtitle) : null),
    h("div", { class: "actions" }, actions));
}

function iconBtn(name, label, onclick, cls = "secondary") {
  return h("button", { class: "btn " + cls, type: "button", onclick, title: label }, icon(name), h("span", {}, label));
}

function kpi(iconName, label, value, hint, tone) {
  return h("div", { class: "kpi" + (tone ? " " + tone : "") },
    h("div", { class: "kpi-icon" }, icon(iconName)),
    h("div", {}, h("div", { class: "kpi-value" }, value), h("div", { class: "kpi-label" }, label),
      hint ? h("div", { class: "kpi-hint" }, hint) : null));
}

function badge(text, tone) {
  return h("span", { class: "badge " + (tone || "") }, text);
}

function stateBadge(state) {
  const tone = { draft: "gray", upcoming: "amber", open: "green", closed: "red", published: "green", archived: "gray" }[state];
  return badge(TL.examState[state] || TL.courseStatus[state] || state, tone);
}

function progressBar(value, max, tone) {
  const fill = h("div", { class: "pfill" + (tone ? " " + tone : "") });
  fill.style.width = pct(value, max) + "%";
  return h("div", { class: "pbar" }, fill);
}

function barList(rows, { valueLabel = (v) => fa(v), max } = {}) {
  if (!rows.length) return h("p", { class: "muted" }, "داده‌ای نیست");
  const top = max || Math.max(1, ...rows.map((r) => r.value));
  return h("div", { class: "barlist" }, rows.map((r) => {
    const fill = h("div");
    fill.style.width = (100 * r.value) / top + "%";
    return h("div", { class: "bar-row" }, h("span", { class: "bar-label", title: r.label }, r.label),
      h("div", { class: "bar" }, fill), h("span", {}, valueLabel(r.value, r)));
  }));
}

function tabs(items, current, onChange) {
  return h("div", { class: "tabs", role: "tablist" }, items.map(([id, label, ic]) =>
    h("button", { class: "tab" + (id === current ? " active" : ""), type: "button", role: "tab", onclick: () => onChange(id) },
      ic ? icon(ic) : null, label)));
}

function emptyState(iconName, title, text, action) {
  return h("div", { class: "empty" }, icon(iconName, "big"), h("h3", {}, title), h("p", { class: "muted" }, text), action || null);
}

/** دانلود فایل از API (برای CSV) با همان توکن ورود. */
async function downloadFile(path, fallbackName) {
  const go = () => fetch(API + path, { headers: { Authorization: "Bearer " + store.get("access") } });
  let res = await go();
  if (res.status === 401 && (await refreshTokens())) res = await go();
  if (!res.ok) throw new ApiError(res.status, "download", "دریافت فایل ممکن نشد");
  const blob = await res.blob();
  const m = /filename\*=UTF-8''([^;]+)/.exec(res.headers.get("content-disposition") || "");
  const a = h("a", { href: URL.createObjectURL(blob), download: m ? decodeURIComponent(m[1]) : fallbackName });
  document.body.append(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}

/** بارگذاری فایل با نوار پیشرفت. بدنه درخواست خود فایل است. */
function uploadFile(file, onProgress, path = "/admin/training/media") {
  const send = () => new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", API + path + "?filename=" + encodeURIComponent(file.name));
    xhr.setRequestHeader("Authorization", "Bearer " + store.get("access"));
    xhr.setRequestHeader("Content-Type", "application/octet-stream");
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(e.loaded / e.total);
    xhr.onload = () => {
      let data = null;
      try { data = JSON.parse(xhr.responseText); } catch { /* بدون بدنه */ }
      if (xhr.status === 201) return resolve(data);
      const d = data && data.detail;
      reject(new ApiError(xhr.status, d && d.code, (d && d.message) || "بارگذاری ناموفق بود (" + xhr.status + ")"));
    };
    xhr.onerror = () => reject(new ApiError(0, "network", "ارتباط با سرور قطع شد"));
    xhr.send(file);
  });
  return send().catch(async (e) => {
    if (e.status === 401 && (await refreshTokens())) return send();
    throw e;
  });
}

/** جعبه انتخاب فایل با کشیدن و رها کردن و نوار پیشرفت؛ value() شناسه فایل بارگذاری‌شده است. */
function dropZone(accept, initialMedia) {
  let media = initialMedia || null;
  const input = h("input", { type: "file", accept, hidden: true });
  const info = h("div", { class: "dz-info" });
  const bar = h("div", { class: "pfill" });
  const progress = h("div", { class: "pbar", hidden: true }, bar);
  const zone = h("div", { class: "dropzone", tabindex: "0", role: "button" },
    icon("upload", "big"), h("div", {}, "فایل را اینجا رها کنید یا کلیک کنید"),
    h("div", { class: "muted" }, "پسوندهای مجاز: " + accept.replaceAll(",", "  ")), info, progress, input);
  let busy = null;
  function show() {
    fill(info, media ? h("span", { class: "badge green" }, icon("check"), media.original_name + " · " + fmtSize(media.size)) : "");
  }
  async function take(file) {
    if (!file) return;
    progress.hidden = false;
    bar.style.width = "0%";
    fill(info, h("span", { class: "muted" }, "در حال بارگذاری " + file.name + "…"));
    busy = uploadFile(file, (p) => (bar.style.width = Math.round(p * 100) + "%"))
      .then((m) => { media = m; show(); return m; })
      .catch((e) => { fill(info, h("span", { class: "error" }, e.message)); throw e; })
      .finally(() => { progress.hidden = true; busy = null; });
    return busy.catch(() => {});
  }
  zone.addEventListener("click", () => input.click());
  zone.addEventListener("keydown", (e) => (e.key === "Enter" || e.key === " ") && input.click());
  input.addEventListener("change", () => take(input.files[0]));
  zone.addEventListener("dragover", (e) => { e.preventDefault(); zone.classList.add("over"); });
  zone.addEventListener("dragleave", () => zone.classList.remove("over"));
  zone.addEventListener("drop", (e) => { e.preventDefault(); zone.classList.remove("over"); take(e.dataTransfer.files[0]); });
  show();
  return {
    el: zone,
    async value() {
      if (busy) await busy;
      return media;
    },
  };
}

let peopleCache = null;
async function people() {
  if (!peopleCache) peopleCache = await api("/admin/training/people");
  return peopleCache;
}

/** ویرایشگر مخاطب: واحد، نقش، سمت و افراد. */
async function audienceEditor(t) {
  await catalog();
  const p = await people();
  const checks = (items, selected) => items.map(([value, label]) => {
    const input = h("input", { type: "checkbox", checked: selected.includes(value) });
    return { value, input, el: h("label", { class: "chip-check" }, input, h("span", {}, label)) };
  });
  const unitC = checks(state.units.map((u) => [u.id, u.name]), t.target_unit_ids || []);
  const roleC = checks(state.roles.map((r) => [r.code, r.name]), t.target_role_codes || []);
  const titles = [...new Set([...p.job_titles, ...(t.target_job_titles || [])])].sort();
  const titleC = checks(titles.map((x) => [x, x]), t.target_job_titles || []);
  const picked = new Map((t.target_user_ids || []).map((id) => [id, p.users.find((u) => u.user_id === id)]));
  const chips = h("div", { class: "chips" });
  function drawChips() {
    fill(chips, ...[...picked].map(([id, u]) => h("span", { class: "chip" },
      icon("user"), u ? u.full_name + " (" + faDigits(u.personnel_code) + ")" : "کاربر حذف‌شده",
      h("button", { type: "button", class: "chip-x", title: "حذف", onclick: () => { picked.delete(id); drawChips(); } }, "×"))));
  }
  const search = h("input", { type: "search", placeholder: "جستجوی نام یا کد پرسنلی برای افزودن فرد" });
  const results = h("div", { class: "search-results" });
  search.addEventListener("input", () => {
    const q = search.value.trim();
    results.replaceChildren();
    if (q.length < 2) return;
    p.users.filter((u) => !picked.has(u.user_id) && (u.full_name.includes(q) || u.personnel_code.includes(q))).slice(0, 8)
      .forEach((u) => results.append(h("button", { type: "button", class: "result", onclick: () => {
        picked.set(u.user_id, u); drawChips(); search.value = ""; results.replaceChildren();
      } }, u.full_name, h("span", { class: "muted" }, " · " + [u.unit_name, u.job_title].filter(Boolean).join(" · ")))));
  });
  drawChips();
  const group = (title, hint, list) => h("div", { class: "aud-group" },
    h("div", { class: "aud-title" }, title, h("span", { class: "muted" }, hint)),
    list.length ? h("div", { class: "chip-checks" }, list.map((c) => c.el)) : h("p", { class: "muted" }, "موردی تعریف نشده است"));
  return {
    el: h("div", { class: "audience" },
      h("p", { class: "muted" }, "اگر هیچ گزینه‌ای انتخاب نشود، همه کارکنان مخاطب‌اند. اگر چند دسته انتخاب شود، فرد باید در همه بگنجد (مثلاً «اورژانس» و «پرستار» یعنی پرستاران اورژانس). افرادی که نام‌شان افزوده شود همیشه مخاطب‌اند."),
      group("واحدها", "", unitC),
      group("سمت‌ها", " (از پروفایل کاربران)", titleC),
      group("نقش‌ها", "", roleC),
      h("div", { class: "aud-group" }, h("div", { class: "aud-title" }, "افراد مشخص"), search, results, chips)),
    value: () => ({
      target_unit_ids: unitC.filter((c) => c.input.checked).map((c) => c.value),
      target_role_codes: roleC.filter((c) => c.input.checked).map((c) => c.value),
      target_job_titles: titleC.filter((c) => c.input.checked).map((c) => c.value),
      target_user_ids: [...picked.keys()],
    }),
  };
}

function audienceSummary(t) {
  const parts = [];
  if (t.target_unit_ids?.length) parts.push(t.target_unit_ids.map(unitName).join("، "));
  if (t.target_job_titles?.length) parts.push(t.target_job_titles.join("، "));
  if (t.target_role_codes?.length) parts.push(t.target_role_codes.map(roleName).join("، "));
  if (t.target_user_ids?.length) parts.push(fa(t.target_user_ids.length) + " نفر مشخص");
  return parts.length ? parts.join(" | ") : "همه کارکنان";
}

function personCells(p) {
  return [
    h("div", { class: "person" }, h("b", {}, p.full_name), h("span", { class: "muted" }, faDigits(p.personnel_code))),
    p.unit_name || "—",
    p.job_title ? badge(p.job_title, "blue") : h("span", { class: "muted" }, "—"),
  ];
}

/** تبدیل ISO به مقدار input[type=datetime-local] به وقت محلی و برعکس. */
function toLocalInput(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, "0");
  return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + "T" + pad(d.getHours()) + ":" + pad(d.getMinutes());
}
const fromLocalInput = (v) => (v ? new Date(v).toISOString() : null);

function dateWithPreview(type, value) {
  const input = h("input", { type, value });
  const preview = h("span", { class: "muted date-preview" });
  const sync = () => (preview.textContent = input.value ? fmtDate(type === "date" ? input.value + "T12:00" : input.value, type !== "date") : "");
  input.addEventListener("input", sync);
  sync();
  return { input, el: h("div", { class: "date-field" }, input, preview) };
}

// ------------------------------------------------------------------ دوره‌ها

async function renderCourses(main, sub) {
  if (sub[0]) return renderCourse(main, sub[0], sub[1] || "content");
  await catalog();
  const courses = await api("/admin/training/courses");
  const published = courses.filter((c) => c.status === "published");
  const grid = h("div", { class: "course-grid" });
  const filter = select([["active", "فعال (منتشرشده و پیش‌نویس)"], ["published", "منتشرشده"], ["draft", "پیش‌نویس"], ["archived", "بایگانی"], ["all", "همه"]], "active");
  function draw() {
    const list = courses.filter((c) => filter.value === "all" || (filter.value === "active" ? c.status !== "archived" : c.status === filter.value));
    if (!list.length) {
      fill(grid, emptyState("school", "دوره‌ای نیست", "با «دوره جدید» اولین دوره آموزشی را بسازید."));
      return;
    }
    fill(grid, ...list.map(courseCard));
  }
  filter.addEventListener("change", draw);
  fill(main, 
    pageHead("دوره‌های آموزشی", "محتوای آموزشی همکاران: فیلم، عکس، صوت، PDF، پاورپوینت و متن",
      filter, iconBtn("add", "دوره جدید", () => newCourseDialog(), "primary")),
    h("div", { class: "kpis" },
      kpi("school", "دوره منتشرشده", fa(published.length)),
      kpi("lock", "دوره اجباری", fa(published.filter((c) => c.is_mandatory).length), null, "amber"),
      kpi("pptx", "محتوای آموزشی", fa(published.reduce((s, c) => s + c.item_count, 0)), null, "blue"),
      kpi("edit", "پیش‌نویس", fa(courses.filter((c) => c.status === "draft").length), null, "gray")),
    grid,
  );
  draw();
}

function courseCard(c) {
  const cover = h("div", { class: "cover" });
  if (c.cover_url) cover.append(h("img", { src: c.cover_url, alt: "" }));
  else cover.append(icon("school", "cover-icon"));
  return h("a", { class: "course-card", href: "#/courses/" + c.id },
    cover,
    h("div", { class: "cc-body" },
      h("div", { class: "cc-badges" }, stateBadge(c.status), c.is_mandatory ? badge("اجباری", "amber") : null,
        c.due_date ? badge("مهلت " + fmtDate(c.due_date + "T12:00"), "gray") : null),
      h("h3", {}, c.title),
      c.description ? h("p", { class: "muted clamp" }, c.description) : null,
      h("div", { class: "cc-meta" },
        h("span", {}, icon("pptx"), fa(c.item_count) + " محتوا"),
        h("span", { title: audienceSummary(c) }, icon("people"), fa(c.audience_size) + " مخاطب"))));
}

function newCourseDialog() {
  const title = h("input", { type: "text", required: true, placeholder: "مثلاً: احیای قلبی ریوی پایه" });
  const desc = h("textarea", { placeholder: "هدف دوره و نکات مهم برای همکاران" });
  dialog("دوره جدید", h("div", { class: "form" }, field("عنوان دوره", title), field("توضیح", desc),
    h("p", { class: "muted" }, "دوره به صورت پیش‌نویس ساخته می‌شود. پس از افزودن محتوا و تعیین مخاطب، آن را منتشر کنید.")), {
    saveLabel: "ساخت دوره",
    onSave: async () => {
      const c = await api("/admin/training/courses", { method: "POST", body: { title: title.value.trim(), description: desc.value.trim() || null } });
      location.hash = "#/courses/" + c.id;
    },
  });
}

async function renderCourse(main, id, tab) {
  await catalog();
  const c = await api("/admin/training/courses/" + id);
  const go = (t) => (location.hash = "#/courses/" + id + "/" + t);
  const statusActions = {
    draft: [iconBtn("check", "انتشار", () => setStatus("published"), "primary")],
    published: [iconBtn("lock", "بایگانی", () => setStatus("archived"))],
    archived: [iconBtn("check", "انتشار دوباره", () => setStatus("published"))],
  }[c.status];
  async function setStatus(s) {
    if (s === "published" && !c.items.length && !confirm("این دوره هنوز محتوایی ندارد. منتشر شود؟")) return;
    try {
      await api("/admin/training/courses/" + id, { method: "PATCH", body: { status: s } });
      toast(s === "published" ? "دوره منتشر شد و برای مخاطبان نمایش داده می‌شود" : "دوره بایگانی شد");
      route();
    } catch (e) { showError(e); }
  }
  const body = h("div");
  fill(main, 
    h("a", { class: "back", href: "#/courses" }, icon("back"), "همه دوره‌ها"),
    pageHead(c.title, null, ...statusActions),
    h("div", { class: "subhead" }, stateBadge(c.status), c.is_mandatory ? badge("اجباری", "amber") : null,
      h("span", { class: "muted" }, icon("people"), " " + fa(c.audience_size) + " مخاطب: " + audienceSummary(c))),
    tabs([["content", "محتوا", "pptx"], ["settings", "تنظیمات و مخاطبان", "edit"], ["stats", "آمار بازدید", "dashboard"]], tab, go),
    body,
  );
  if (tab === "settings") return courseSettings(body, c);
  if (tab === "stats") return courseStats(body, c);
  return courseContent(body, c);
}

function itemPreview(it) {
  if (it.kind === "image" && it.media) return h("img", { class: "thumb", src: it.media.url, alt: "" });
  return h("div", { class: "thumb kind-" + it.kind }, icon(it.kind));
}

function courseContent(box, c) {
  const list = h("div", { class: "items" });
  async function move(i, dir) {
    const ids = c.items.map((x) => x.id);
    [ids[i], ids[i + dir]] = [ids[i + dir], ids[i]];
    try {
      await api("/admin/training/courses/" + c.id + "/order", { method: "PUT", body: { ids } });
      route();
    } catch (e) { showError(e); }
  }
  fill(list, ...c.items.map((it, i) => h("div", { class: "item-row" },
    h("div", { class: "order" },
      h("button", { class: "icon-btn", type: "button", title: "بالا", disabled: i === 0, onclick: () => move(i, -1) }, icon("up")),
      h("button", { class: "icon-btn", type: "button", title: "پایین", disabled: i === c.items.length - 1, onclick: () => move(i, 1) }, icon("down"))),
    itemPreview(it),
    h("div", { class: "grow" },
      h("div", { class: "item-title" }, h("span", { class: "num" }, fa(i + 1)), it.title),
      h("div", { class: "muted" }, TL.kind[it.kind],
        it.media ? " · " + it.media.original_name + " · " + fmtSize(it.media.size) : "",
        it.kind === "link" ? " · " + it.body : "")),
    h("div", { class: "actions" },
      h("button", { class: "icon-btn", type: "button", title: "پیش‌نمایش", onclick: () => previewItem(it) }, icon("eye")),
      h("button", { class: "icon-btn", type: "button", title: "ویرایش", onclick: () => itemDialog(c, it) }, icon("edit")),
      h("button", { class: "icon-btn danger", type: "button", title: "حذف", onclick: async () => {
        if (!confirm("«" + it.title + "» حذف شود؟ آمار بازدید همین محتوا هم پاک می‌شود.")) return;
        try { await api("/admin/training/items/" + it.id, { method: "DELETE" }); toast("حذف شد"); route(); } catch (e) { showError(e); }
      } }, icon("delete"))))));
  fill(box, 
    h("div", { class: "card" },
      h("div", { class: "card-head" }, h("h2", {}, "محتوای دوره"), iconBtn("add", "افزودن محتوا", () => itemDialog(c, null), "primary")),
      c.items.length ? list : emptyState("upload", "هنوز محتوایی اضافه نشده", "فیلم، عکس، صوت، PDF، پاورپوینت، فایل، متن یا لینک اضافه کنید.")),
    c.exams.length ? h("div", { class: "card" }, h("h2", {}, "آزمون‌های این دوره"),
      c.exams.map((e) => h("a", { class: "item-row link-row", href: "#/exams/" + e.id }, h("div", { class: "thumb kind-quiz" }, icon("quiz")),
        h("div", { class: "grow" }, h("div", { class: "item-title" }, e.title), h("div", { class: "muted" }, fa(e.question_count) + " سؤال")),
        stateBadge(e.state)))) : null,
  );
}

function previewItem(it) {
  let content;
  if (it.kind === "text") content = h("div", { class: "pre reading" }, it.body);
  else if (it.kind === "link") content = h("a", { href: it.body, target: "_blank", rel: "noopener noreferrer" }, it.body);
  else if (it.kind === "image") content = h("img", { class: "preview-media", src: it.media.url, alt: it.title });
  else if (it.kind === "video") content = h("video", { class: "preview-media", src: it.media.url, controls: true, preload: "metadata" });
  else if (it.kind === "audio") content = h("audio", { src: it.media.url, controls: true, preload: "metadata" });
  else content = h("div", { class: "form" }, h("p", {}, it.media.original_name + " · " + fmtSize(it.media.size)),
    h("a", { class: "btn", href: it.media.url, target: "_blank", rel: "noopener" }, icon("download"), " دریافت / باز کردن"));
  dialog(it.title, content);
}

function itemDialog(c, it) {
  const isNew = !it;
  const kinds = Object.keys(TL.kind);
  let kind = it ? it.kind : "video";
  const title = h("input", { type: "text", required: true, value: it ? it.title : "" });
  const holder = h("div");
  let zone = null;
  let body = null;
  const seg = h("div", { class: "segmented" });
  function drawKind() {
    fill(seg, ...kinds.map((k) => h("button", { type: "button", class: k === kind ? "active" : null, disabled: !isNew && k !== kind,
      onclick: () => { kind = k; drawKind(); } }, icon(k), TL.kind[k])));
    zone = null; body = null;
    if (kind === "text") {
      body = h("textarea", { class: "tall", placeholder: "متن آموزشی…" }, it && it.kind === "text" ? it.body : "");
      fill(holder, field("متن", body));
    } else if (kind === "link") {
      body = h("input", { type: "text", dir: "ltr", placeholder: "https://", value: it && it.kind === "link" ? it.body : "" });
      fill(holder, field("نشانی لینک", body));
    } else {
      zone = dropZone(KIND_ACCEPT[kind], it ? it.media : null);
      body = h("textarea", { placeholder: "توضیح کوتاه (اختیاری)" }, it && it.body ? it.body : "");
      fill(holder, zone.el, field("توضیح", body),
        kind === "pptx" ? h("p", { class: "muted" }, "پاورپوینت در گوشی با برنامه PowerPoint یا WPS باز می‌شود. برای نمایش داخل اپ، نسخه PDF را بارگذاری کنید.") : null,
        kind === "video" ? h("p", { class: "muted" }, "برای مصرف کمتر اینترنت همکاران، فیلم با کیفیت 720p و فرمت MP4 پیشنهاد می‌شود.") : null);
    }
  }
  drawKind();
  dialog(isNew ? "افزودن محتوا" : "ویرایش محتوا", h("div", { class: "form" }, seg, field("عنوان", title), holder), {
    onSave: async () => {
      const payload = { title: title.value.trim(), body: body ? body.value.trim() || null : null };
      if (zone) {
        const m = await zone.value();
        if (!m) throw new Error("ابتدا فایل را بارگذاری کنید");
        payload.media_id = m.id;
      }
      if (isNew) await api("/admin/training/courses/" + c.id + "/items", { method: "POST", body: { kind, ...payload } });
      else await api("/admin/training/items/" + it.id, { method: "PATCH", body: payload });
      toast("ذخیره شد");
      route();
    },
  });
}

async function courseSettings(box, c) {
  const title = h("input", { type: "text", required: true, value: c.title });
  const desc = h("textarea", {}, c.description || "");
  const mandatory = checkbox("دوره اجباری است (در اپ همکاران بالاتر و با نشان «اجباری» نمایش داده می‌شود)", c.is_mandatory);
  const due = dateWithPreview("date", c.due_date || "");
  const cover = dropZone(KIND_ACCEPT.image, null);
  const aud = await audienceEditor(c);
  const save = h("button", { class: "btn primary", type: "button" }, "ذخیره تغییرات");
  save.addEventListener("click", async () => {
    save.disabled = true;
    try {
      const m = await cover.value();
      await api("/admin/training/courses/" + c.id, { method: "PATCH", body: {
        title: title.value.trim(), description: desc.value.trim() || null, is_mandatory: mandatory.input.checked,
        due_date: due.input.value || null, ...(m ? { cover_media_id: m.id } : {}), ...aud.value(),
      } });
      toast("ذخیره شد");
      route();
    } catch (e) { showError(e); } finally { save.disabled = false; }
  });
  const del = c.status === "draft" ? h("button", { class: "btn danger", type: "button", onclick: async () => {
    if (!confirm("دوره «" + c.title + "» و همه محتوای آن حذف شود؟")) return;
    try { await api("/admin/training/courses/" + c.id, { method: "DELETE" }); toast("حذف شد"); location.hash = "#/courses"; } catch (e) { showError(e); }
  } }, "حذف دوره") : null;
  fill(box, 
    h("div", { class: "grid2" },
      h("div", { class: "card form" }, h("h2", {}, "مشخصات"), field("عنوان", title), field("توضیح", desc), mandatory.el,
        field("مهلت تکمیل (اختیاری)", due.el), h("div", {}, h("span", { class: "muted" }, "تصویر جلد (اختیاری)"), cover.el)),
      h("div", { class: "card" }, h("h2", {}, "مخاطبان دوره"), aud.el)),
    h("div", { class: "sticky-actions" }, save, del),
  );
}

async function courseStats(box, c) {
  const r = await api("/admin/training/courses/" + c.id + "/report");
  const learners = table(["همکار", "واحد", "سمت", "بازدید", "پیشرفت", "مدت", "آخرین بازدید", ""],
    r.learners.map((p) => ({ item: p, cells: [
      ...personCells(p), fa(p.views),
      h("div", { class: "prog" }, progressBar(p.items_completed, r.item_count, p.completed ? "green" : null),
        h("span", { class: "muted" }, fa(p.items_completed) + " از " + fa(r.item_count))),
      fmtMinutes(p.total_seconds), fmtDate(p.last_view_at, true),
      p.completed ? badge("تکمیل", "green") : p.in_audience ? badge("در حال یادگیری", "amber") : badge("خارج از مخاطبان", "gray"),
    ] })));
  const notStarted = table(["همکار", "واحد", "سمت"], r.not_started.map((p) => ({ item: p, cells: personCells(p) })));
  const items = table(["محتوا", "بیننده", "دفعات بازدید", "کامل دیده‌اند", "مجموع زمان"],
    r.items.map((s) => ({ item: s, cells: [
      h("span", { class: "with-icon" }, icon(s.kind), s.title), fa(s.viewers), fa(s.views),
      h("div", { class: "prog" }, progressBar(s.completed, r.audience_size || s.viewers), h("span", { class: "muted" }, fa(s.completed))),
      fmtMinutes(s.total_seconds),
    ] })), (s) => itemViewersDialog(s));
  const breakdown = (rows) => barList(rows.map((b) => ({ label: b.label, value: pct(b.completed, b.audience || b.participated), b })), {
    max: 100, valueLabel: (v, row) => fa(v) + "٪ (" + fa(row.b.completed) + "/" + fa(row.b.audience) + ")" });
  fill(box, 
    h("div", { class: "kpis" },
      kpi("people", "مخاطبان", fa(r.audience_size)),
      kpi("eye", "شرکت کرده‌اند", fa(r.participants), fa(pct(r.participants, r.audience_size)) + "٪ مخاطبان", "blue"),
      kpi("check", "دوره را کامل کرده‌اند", fa(r.completed), fa(pct(r.completed, r.audience_size)) + "٪ مخاطبان", "green"),
      kpi("video", "کل دفعات بازدید", fa(r.total_views), null, "gray")),
    h("div", { class: "card" }, h("div", { class: "card-head" }, h("h2", {}, "آمار هر محتوا"),
      iconBtn("download", "خروجی Excel", () => downloadFile("/admin/training/courses/" + c.id + "/report.csv", "course.csv").catch(showError))),
      h("p", { class: "muted" }, "برای دیدن بینندگان هر محتوا روی ردیف آن بزنید."), items),
    h("div", { class: "grid3" },
      h("div", { class: "card" }, h("h3", {}, "درصد تکمیل بر اساس سمت"), breakdown(r.by_job_title)),
      h("div", { class: "card" }, h("h3", {}, "درصد تکمیل بر اساس واحد"), breakdown(r.by_unit)),
      h("div", { class: "card" }, h("h3", {}, "درصد تکمیل بر اساس نقش"), breakdown(r.by_role))),
    h("div", { class: "card" }, h("h2", {}, "همکارانی که دوره را دیده‌اند"), learners),
    h("div", { class: "card" }, h("h2", {}, "هنوز شروع نکرده‌اند (" + fa(r.not_started.length) + " نفر)"), notStarted),
  );
}

async function itemViewersDialog(s) {
  const rows = await api("/admin/training/items/" + s.item_id + "/viewers").catch(showError);
  if (!rows) return;
  dialog("بینندگان «" + s.title + "»", table(["همکار", "واحد", "سمت", "دفعات", "مدت", "تماشا", "اولین", "آخرین"],
    rows.map((p) => ({ item: p, cells: [...personCells(p), fa(p.views), fmtMinutes(p.total_seconds),
      ["video", "audio"].includes(s.kind) ? fa(p.max_progress) + "٪" : p.completed ? "دیده شد" : "—",
      fmtDate(p.first_view_at, true), fmtDate(p.last_view_at, true)] }))), { wide: true });
}

// ------------------------------------------------------------------ آزمون‌ها

function examWindow(e) {
  if (e.manual_state !== "auto") return TL.manual[e.manual_state];
  if (!e.opens_at && !e.closes_at) return "بدون محدودیت زمانی";
  return (e.opens_at ? "از " + fmtDate(e.opens_at, true) : "") + (e.closes_at ? " تا " + fmtDate(e.closes_at, true) : "");
}

async function renderExams(main, sub) {
  if (sub[0]) return renderExam(main, sub[0], sub[1] || "questions");
  await catalog();
  const exams = await api("/admin/training/exams");
  const rows = table(["آزمون", "وضعیت", "زمان", "سؤال", "شرکت‌کننده", ""],
    exams.map((e) => ({ item: e, cells: [
      h("div", { class: "person" }, h("b", {}, e.title), h("span", { class: "muted" }, audienceSummary(e))),
      stateBadge(e.state),
      h("span", { class: "muted" }, examWindow(e)),
      fa(e.question_count),
      h("div", { class: "prog" }, progressBar(e.attempt_count, e.audience_size, "green"),
        h("span", { class: "muted" }, fa(e.attempt_count) + " از " + fa(e.audience_size))),
      quickToggle(e),
    ] })), (e) => (location.hash = "#/exams/" + e.id));
  fill(main, 
    pageHead("آزمون‌ها", "ساخت آزمون، باز و بسته کردن برای گروه‌های مشخص و دیدن نتایج",
      iconBtn("add", "آزمون جدید", () => newExamDialog(), "primary")),
    h("div", { class: "kpis" },
      kpi("quiz", "آزمون باز", fa(exams.filter((e) => e.state === "open").length), null, "green"),
      kpi("clock", "زمان‌بندی‌شده", fa(exams.filter((e) => e.state === "upcoming").length), null, "amber"),
      kpi("lock", "بسته", fa(exams.filter((e) => e.state === "closed").length), null, "gray"),
      kpi("edit", "پیش‌نویس", fa(exams.filter((e) => e.state === "draft").length), null, "blue")),
    exams.length ? h("div", { class: "card" }, rows) : emptyState("quiz", "آزمونی نیست", "با «آزمون جدید» اولین آزمون را بسازید."),
  );
}

function quickToggle(e) {
  if (e.status !== "published") return h("span", { class: "muted" }, "منتشر نشده");
  const next = e.state === "open" ? "closed" : "open";
  return h("button", { class: "btn small " + (next === "open" ? "primary" : "danger"), type: "button", onclick: async (ev) => {
    ev.stopPropagation();
    try {
      await api("/admin/training/exams/" + e.id, { method: "PATCH", body: { manual_state: next } });
      toast(next === "open" ? "آزمون باز شد" : "آزمون بسته شد");
      route();
    } catch (err) { showError(err); }
  } }, next === "open" ? "باز کن" : "ببند");
}

async function newExamDialog(courseId) {
  const courses = await api("/admin/training/courses");
  const title = h("input", { type: "text", required: true, placeholder: "مثلاً: آزمون پایان دوره احیا" });
  const course = select([["", "— مستقل از دوره —"], ...courses.filter((c) => c.status !== "archived").map((c) => [c.id, c.title])], courseId || "");
  dialog("آزمون جدید", h("div", { class: "form" }, field("عنوان آزمون", title), field("دوره مرتبط (اختیاری)", course),
    h("p", { class: "muted" }, "آزمون به صورت پیش‌نویس ساخته می‌شود؛ سؤال‌ها، زمان‌بندی و مخاطبان را در صفحه بعد تنظیم کنید.")), {
    saveLabel: "ساخت آزمون",
    onSave: async () => {
      const e = await api("/admin/training/exams", { method: "POST", body: { title: title.value.trim(), course_id: course.value || null } });
      location.hash = "#/exams/" + e.id;
    },
  });
}

async function renderExam(main, id, tab) {
  await catalog();
  const e = await api("/admin/training/exams/" + id);
  const go = (t) => (location.hash = "#/exams/" + id + "/" + t);
  async function patch(body, msg) {
    try { await api("/admin/training/exams/" + id, { method: "PATCH", body }); toast(msg); route(); } catch (err) { showError(err); }
  }
  const actions = [];
  if (e.status === "draft") {
    actions.push(iconBtn("check", "انتشار آزمون", () => {
      if (!e.question_count) return toast("ابتدا سؤال‌ها را اضافه کنید", true);
      patch({ status: "published" }, "آزمون منتشر شد");
    }, "primary"));
  } else {
    if (e.state !== "open") actions.push(iconBtn("check", "باز کردن الان", () => patch({ manual_state: "open" }, "آزمون باز شد"), "primary"));
    if (e.state === "open") actions.push(iconBtn("lock", "بستن الان", () => patch({ manual_state: "closed" }, "آزمون بسته شد"), "danger"));
    if (e.manual_state !== "auto") actions.push(iconBtn("clock", "بازگشت به زمان‌بندی", () => patch({ manual_state: "auto" }, "آزمون طبق زمان‌بندی باز و بسته می‌شود")));
  }
  const body = h("div");
  fill(main, 
    h("a", { class: "back", href: "#/exams" }, icon("back"), "همه آزمون‌ها"),
    pageHead(e.title, null, ...actions),
    h("div", { class: "subhead" }, stateBadge(e.state), h("span", { class: "muted" }, icon("clock"), " " + examWindow(e)),
      h("span", { class: "muted" }, icon("people"), " " + fa(e.audience_size) + " مخاطب: " + audienceSummary(e))),
    tabs([["questions", "سؤال‌ها (" + fa(e.question_count) + ")", "quiz"], ["settings", "زمان‌بندی، قوانین و مخاطبان", "clock"],
      ["results", "نتایج (" + fa(e.attempt_count) + ")", "dashboard"]], tab, go),
    body,
  );
  if (tab === "settings") return examSettings(body, e);
  if (tab === "results") return examResults(body, e);
  return examQuestions(body, e);
}

function examQuestions(box, e) {
  const locked = e.attempt_count > 0;
  const qs = e.questions.map((q) => ({ kind: q.kind, text: q.text, points: q.points, explanation: q.explanation || "",
    options: q.options.map((o) => ({ text: o.text, correct: !!o.correct })) }));
  const list = h("div", { class: "questions" });
  let dirty = false;
  const saveBtn = h("button", { class: "btn primary", type: "button", disabled: locked }, "ذخیره سؤال‌ها");
  const totalEl = h("span", { class: "muted" });
  const empty = emptyState("quiz", "هنوز سؤالی ندارد", "یک سؤال تک‌گزینه‌ای، چندگزینه‌ای یا درست/غلط اضافه کنید.");
  const markDirty = () => {
    dirty = true; saveBtn.textContent = "ذخیره سؤال‌ها *";
    totalEl.textContent = "مجموع امتیاز: " + fa(qs.reduce((s, q) => s + q.points, 0));
  };

  function blank(kind) {
    if (kind === "true_false") return { kind, text: "", points: 1, explanation: "", options: [{ text: "درست", correct: true }, { text: "غلط", correct: false }] };
    return { kind, text: "", points: 1, explanation: "", options: [1, 2, 3, 4].map(() => ({ text: "", correct: false })) };
  }

  function draw() {
    empty.hidden = qs.length > 0;
    totalEl.textContent = "مجموع امتیاز: " + fa(qs.reduce((s, q) => s + q.points, 0));
    fill(list, ...qs.map((q, i) => {
      const text = h("textarea", { placeholder: "متن سؤال", disabled: locked }, q.text);
      text.addEventListener("input", () => { q.text = text.value; markDirty(); });
      const kindSel = select(Object.entries(TL.qkind), q.kind);
      kindSel.disabled = locked;
      kindSel.addEventListener("change", () => {
        const fresh = blank(kindSel.value);
        q.kind = kindSel.value;
        if (q.kind === "true_false" || q.options.length < 2) q.options = fresh.options;
        if (q.kind === "single") { let seen = false; q.options.forEach((o) => { if (o.correct && seen) o.correct = false; seen ||= o.correct; }); }
        markDirty(); draw();
      });
      const points = h("input", { type: "number", min: "1", max: "100", value: q.points, disabled: locked, class: "narrow" });
      points.addEventListener("input", () => { q.points = Math.max(1, parseInt(points.value, 10) || 1); markDirty(); });
      const name = "q" + i + "-" + Math.random().toString(36).slice(2);
      const opts = q.options.map((o, k) => {
        const mark = h("input", { type: q.kind === "multiple" ? "checkbox" : "radio", name, checked: o.correct, disabled: locked, title: "پاسخ درست" });
        mark.addEventListener("change", () => {
          if (q.kind !== "multiple") q.options.forEach((x) => (x.correct = false));
          o.correct = mark.checked; markDirty();
          if (q.kind !== "multiple") draw();
        });
        const t = h("input", { type: "text", value: o.text, placeholder: "گزینه " + fa(k + 1), disabled: locked || q.kind === "true_false" });
        t.addEventListener("input", () => { o.text = t.value; markDirty(); });
        return h("div", { class: "opt" + (o.correct ? " correct" : "") }, mark, t,
          q.kind !== "true_false" && q.options.length > 2 && !locked
            ? h("button", { class: "icon-btn", type: "button", title: "حذف گزینه", onclick: () => { q.options.splice(k, 1); markDirty(); draw(); } }, icon("delete"))
            : null);
      });
      const explanation = h("input", { type: "text", value: q.explanation, placeholder: "توضیح پاسخ (اختیاری؛ پس از نمایش پاسخ‌ها به همکار نشان داده می‌شود)", disabled: locked });
      explanation.addEventListener("input", () => { q.explanation = explanation.value; markDirty(); });
      return h("div", { class: "q-card" },
        h("div", { class: "q-head" }, h("span", { class: "q-num" }, fa(i + 1)), kindSel,
          h("label", { class: "inline" }, "امتیاز", points), h("div", { class: "grow" }),
          locked ? null : h("div", { class: "actions" },
            h("button", { class: "icon-btn", type: "button", title: "بالا", disabled: i === 0, onclick: () => { [qs[i - 1], qs[i]] = [qs[i], qs[i - 1]]; markDirty(); draw(); } }, icon("up")),
            h("button", { class: "icon-btn", type: "button", title: "پایین", disabled: i === qs.length - 1, onclick: () => { [qs[i + 1], qs[i]] = [qs[i], qs[i + 1]]; markDirty(); draw(); } }, icon("down")),
            h("button", { class: "icon-btn danger", type: "button", title: "حذف سؤال", onclick: () => { if (confirm("سؤال " + fa(i + 1) + " حذف شود؟")) { qs.splice(i, 1); markDirty(); draw(); } } }, icon("delete")))),
        text,
        h("div", { class: "opts" }, opts),
        q.kind !== "true_false" && q.options.length < 10 && !locked
          ? h("button", { class: "btn small secondary", type: "button", onclick: () => { q.options.push({ text: "", correct: false }); markDirty(); draw(); } }, icon("add"), " گزینه")
          : null,
        explanation);
    }));
  }

  saveBtn.addEventListener("click", async () => {
    for (const [i, q] of qs.entries()) {
      if (!q.text.trim()) return toast("متن سؤال " + fa(i + 1) + " خالی است", true);
      if (q.options.some((o) => !o.text.trim())) return toast("گزینه خالی در سؤال " + fa(i + 1), true);
      if (!q.options.some((o) => o.correct)) return toast("پاسخ درست سؤال " + fa(i + 1) + " را مشخص کنید", true);
    }
    saveBtn.disabled = true;
    try {
      await api("/admin/training/exams/" + e.id + "/questions", { method: "PUT", body: qs.map((q) => ({
        kind: q.kind, text: q.text.trim(), points: q.points, explanation: q.explanation.trim() || null,
        options: q.options.map((o) => ({ text: o.text.trim(), correct: o.correct })) })) });
      dirty = false;
      toast("سؤال‌ها ذخیره شد");
      route();
    } catch (err) { showError(err); } finally { saveBtn.disabled = locked; }
  });
  window.onbeforeunload = () => (dirty ? true : undefined);
  window.addEventListener("hashchange", () => (window.onbeforeunload = null), { once: true });

  draw();
  fill(box,
    locked ? h("div", { class: "notice" }, icon("lock"), "همکاران در این آزمون شرکت کرده‌اند؛ سؤال‌ها قفل است تا نتایج همه با یک معیار سنجیده شود.") : null,
    list,
    empty,
    locked ? null : h("div", { class: "sticky-actions" },
      iconBtn("add", "تک‌گزینه‌ای", () => { qs.push(blank("single")); markDirty(); draw(); }),
      iconBtn("add", "چندگزینه‌ای", () => { qs.push(blank("multiple")); markDirty(); draw(); }),
      iconBtn("add", "درست / غلط", () => { qs.push(blank("true_false")); markDirty(); draw(); }),
      h("div", { class: "grow" }),
      totalEl,
      saveBtn),
  );
}

async function examSettings(box, e) {
  const courses = await api("/admin/training/courses");
  const title = h("input", { type: "text", required: true, value: e.title });
  const desc = h("textarea", { placeholder: "توضیح برای شرکت‌کنندگان: منابع، قوانین، ..." }, e.description || "");
  const course = select([["", "— مستقل از دوره —"], ...courses.map((c) => [c.id, c.title])], e.course_id || "");
  const opens = dateWithPreview("datetime-local", toLocalInput(e.opens_at));
  const closes = dateWithPreview("datetime-local", toLocalInput(e.closes_at));
  const limit = h("input", { type: "number", min: "1", max: "600", value: e.time_limit_minutes || "", placeholder: "بدون محدودیت" });
  const attempts = h("input", { type: "number", min: "1", max: "20", value: e.max_attempts });
  const pass = h("input", { type: "number", min: "0", max: "100", value: e.pass_score });
  const shuffleQ = checkbox("ترتیب سؤال‌ها برای هر نفر تصادفی باشد", e.shuffle_questions);
  const shuffleO = checkbox("ترتیب گزینه‌ها برای هر نفر تصادفی باشد", e.shuffle_options);
  const show = select(Object.entries(TL.showAnswers), e.show_answers);
  const aud = await audienceEditor(e);
  const save = h("button", { class: "btn primary", type: "button" }, "ذخیره تغییرات");
  save.addEventListener("click", async () => {
    save.disabled = true;
    try {
      await api("/admin/training/exams/" + e.id, { method: "PATCH", body: {
        title: title.value.trim(), description: desc.value.trim() || null, course_id: course.value || null,
        opens_at: fromLocalInput(opens.input.value), closes_at: fromLocalInput(closes.input.value),
        time_limit_minutes: parseInt(limit.value, 10) || null, max_attempts: parseInt(attempts.value, 10) || 1,
        pass_score: Math.min(100, Math.max(0, parseInt(pass.value, 10) || 0)),
        shuffle_questions: shuffleQ.input.checked, shuffle_options: shuffleO.input.checked, show_answers: show.value,
        ...aud.value(),
      } });
      toast("ذخیره شد");
      route();
    } catch (err) { showError(err); } finally { save.disabled = false; }
  });
  const del = e.attempt_count === 0 ? h("button", { class: "btn danger", type: "button", onclick: async () => {
    if (!confirm("آزمون «" + e.title + "» حذف شود؟")) return;
    try { await api("/admin/training/exams/" + e.id, { method: "DELETE" }); toast("حذف شد"); location.hash = "#/exams"; } catch (err) { showError(err); }
  } }, "حذف آزمون") : null;
  fill(box, 
    h("div", { class: "grid2" },
      h("div", { class: "card form" }, h("h2", {}, "مشخصات"), field("عنوان", title), field("توضیح", desc), field("دوره مرتبط", course)),
      h("div", { class: "card form" }, h("h2", {}, "زمان‌بندی"),
        h("p", { class: "muted" }, "آزمون در این بازه خودکار باز است. دکمه‌های «باز کردن الان» و «بستن الان» بالای صفحه زمان‌بندی را نادیده می‌گیرند."),
        h("div", { class: "row" }, field("زمان باز شدن", opens.el), field("زمان بسته شدن", closes.el)),
        h("div", { class: "row" }, field("مدت پاسخگویی (دقیقه)", limit), field("دفعات مجاز شرکت", attempts)),
        field("حداقل نمره قبولی (درصد)", pass), shuffleQ.el, shuffleO.el, field("نمایش پاسخ درست به شرکت‌کننده", show))),
    h("div", { class: "card" }, h("h2", {}, "چه کسانی در آزمون شرکت کنند؟"), aud.el),
    h("div", { class: "sticky-actions" }, save, del),
  );
}

async function examResults(box, e) {
  const r = await api("/admin/training/exams/" + e.id + "/report");
  const hist = barList(r.histogram.map((n, i) => ({ label: fa(i * 10) + "–" + fa(i * 10 + 10), value: n })));
  const qstats = h("div", { class: "qstats" }, r.questions.map((q, i) => {
    const src = e.questions.find((x) => x.id === q.question_id);
    const tone = q.correct_percent === null ? null : q.correct_percent < 40 ? "red" : q.correct_percent < 70 ? "amber" : "green";
    return h("div", { class: "qstat" },
      h("div", { class: "q-head" }, h("span", { class: "q-num" }, fa(i + 1)), h("div", { class: "grow" }, q.text),
        badge(q.correct_percent === null ? "—" : fa(q.correct_percent) + "٪ درست", tone)),
      src ? h("div", { class: "opts" }, src.options.map((o) => {
        const n = q.option_counts[o.id] || 0;
        return h("div", { class: "opt-stat" + (o.correct ? " correct" : "") },
          o.correct ? icon("check") : h("span", { class: "dot" }), h("span", { class: "grow" }, o.text),
          progressBar(n, r.participants, o.correct ? "green" : "gray"), h("span", { class: "muted" }, fa(n) + " نفر"));
      })) : null);
  }));
  const takers = table(["همکار", "واحد", "سمت", "دفعات", "بهترین نمره", "نتیجه", "زمان ثبت"],
    r.takers.map((t) => ({ item: t, cells: [...personCells(t), fa(t.attempts),
      t.best_score === null ? "—" : h("b", {}, fa(t.best_score)),
      t.best_score === null ? badge("در حال پاسخ", "amber") : t.passed ? badge("قبول", "green") : badge("مردود", "red"),
      fmtDate(t.last_submitted_at, true)] })));
  const missing = table(["همکار", "واحد", "سمت"], r.not_attempted.map((p) => ({ item: p, cells: personCells(p) })));
  const breakdown = (rows) => barList(rows.map((b) => ({ label: b.label, value: pct(b.participated, b.audience), b })), {
    max: 100, valueLabel: (v, row) => fa(v) + "٪ شرکت · " + fa(row.b.completed) + " قبول" });
  fill(box, 
    h("div", { class: "kpis" },
      kpi("people", "مخاطبان", fa(r.audience_size)),
      kpi("quiz", "شرکت کرده‌اند", fa(r.participants), fa(pct(r.participants, r.audience_size)) + "٪ مخاطبان", "blue"),
      kpi("check", "قبول شده‌اند", fa(r.passed), "نمره قبولی " + fa(e.pass_score) + "٪", "green"),
      kpi("dashboard", "میانگین نمره", r.average_score === null ? "—" : fa(r.average_score) + "٪", null, "amber")),
    h("div", { class: "grid2" },
      h("div", { class: "card" }, h("h3", {}, "توزیع نمره‌ها (بهترین نمره هر نفر)"), hist),
      h("div", { class: "card" }, h("h3", {}, "شرکت و قبولی بر اساس سمت"), breakdown(r.by_job_title),
        h("h3", { class: "mt" }, "بر اساس واحد"), breakdown(r.by_unit))),
    h("div", { class: "card" }, h("div", { class: "card-head" }, h("h2", {}, "نتایج همکاران"),
      iconBtn("download", "خروجی Excel", () => downloadFile("/admin/training/exams/" + e.id + "/report.csv", "exam.csv").catch(showError))), takers),
    h("div", { class: "card" }, h("h2", {}, "تحلیل سؤال‌ها"),
      h("p", { class: "muted" }, "سؤال‌هایی که درصد پاسخ درستشان پایین است ممکن است مبهم باشند یا نیاز به آموزش بیشتر داشته باشند."), qstats),
    h("div", { class: "card" }, h("h2", {}, "شرکت نکرده‌اند (" + fa(r.not_attempted.length) + " نفر)"), missing),
  );
}
