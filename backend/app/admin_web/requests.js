/*
 * بخش درخواست‌های پشتیبانی پنل: کارتابل واحد، جزئیات و رسیدگی، داشبورد و تعریف انواع درخواست.
 * پس از training.js و پیش از app.js بارگذاری می‌شود (از icon، fill، kpi و ... استفاده می‌کند).
 */
"use strict";

Object.assign(ICONS, {
  build: "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z",
  category: "M12 2l-5.5 9h11L12 2zm5.5 11c-2.49 0-4.5 2.01-4.5 4.5S15.01 22 17.5 22s4.5-2.01 4.5-4.5-2.01-4.5-4.5-4.5zM3 21.5h8v-8H3v8z",
  star: "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z",
  attach: "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5-2.46 5.5-5.5V6h-1.5z",
  transfer: "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z",
  warning: "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z",
  place: "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
  inbox: "M19 3H4.99c-1.11 0-1.98.89-1.98 2L3 19c0 1.1.88 2 1.99 2H19c1.1 0 2-.9 2-2V5c0-1.11-.9-2-2-2zm0 12h-4c0 1.66-1.35 3-3 3s-3-1.34-3-3H4.99V5H19v10z",
  play: "M8 5v14l11-7z",
  pause: "M6 19h4V5H6v14zm8-14v14h4V5h-4z",
  close: "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
});

// ------------------------------------------------------------------ برچسب‌ها

const RL = {
  category: { it: "فناوری اطلاعات", equipment: "تجهیزات پزشکی", facilities: "تأسیسات", other: "سایر" },
  priority: { critical: "بحرانی", urgent: "فوری", normal: "عادی" },
  status: {
    submitted: "ثبت‌شده", accepted: "پذیرفته‌شده", in_progress: "در حال انجام", on_hold: "در انتظار",
    done: "انجام شد", closed: "بسته", rejected: "رد شده",
  },
  fieldKind: { text: "متن", number: "عدد", select: "انتخابی" },
};
const PRIORITY_TONE = { critical: "red", urgent: "amber", normal: "gray" };
const STATUS_TONE = {
  submitted: "blue", accepted: "blue", in_progress: "amber", on_hold: "gray", done: "green", closed: "gray", rejected: "red",
};
const CATEGORY_TONE = { it: "blue", equipment: "red", facilities: "amber", other: "gray" };
const OPEN = ["submitted", "accepted", "in_progress", "on_hold"];

// دکمه هر تغییر وضعیت: [برچسب، آیکون، کلاس]
const TRANSITION_BTN = {
  accepted: ["پذیرش", "check", "primary"],
  in_progress: ["شروع کار", "play", "primary"],
  on_hold: ["در انتظار", "pause", "secondary"],
  done: ["انجام شد", "check", "primary"],
  rejected: ["رد درخواست", "close", "danger"],
};

const priorityBadge = (p) => badge(RL.priority[p] || p, PRIORITY_TONE[p]);
const statusBadge = (s) => badge(RL.status[s] || s, STATUS_TONE[s]);

/** «۳ ساعت پیش»، «۲ روز پیش» */
function ago(iso) {
  const min = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (min < 1) return "همین الان";
  if (min < 60) return fa(min) + " دقیقه پیش";
  if (min < 1440) return fa(Math.round(min / 60)) + " ساعت پیش";
  return fa(Math.round(min / 1440)) + " روز پیش";
}

function hoursLabel(hrs) {
  if (hrs === null || hrs === undefined) return "—";
  if (hrs < 1) return fa(Math.max(1, Math.round(hrs * 60))) + " دقیقه";
  return hrs < 24 ? fa(hrs) + " ساعت" : fa((hrs / 24).toFixed(1)) + " روز";
}

/** واحدهای پشتیبانی (برای فیلتر و ارجاع) از روی انواع درخواست فعال. */
async function supportCatalog() {
  const types = await api("/request-types").catch(() => []);
  const units = new Map(types.map((t) => [t.handler_unit_id, t.handler_unit_name]));
  return { types, units: [...units].map(([id, name]) => ({ id, name })) };
}

// ------------------------------------------------------------------ کارتابل

async function renderRequests(main, sub) {
  if (sub[0] === "dashboard") return renderRequestDashboard(main);
  if (sub[0]) return renderRequest(main, sub[0]);
  const handles = can("request.handle");
  const { types } = await supportCatalog();
  const openRows = (await api("/requests?status=open&size=200")).items;
  const status = select([["open", "باز"], ["", "همه وضعیت‌ها"], ...Object.entries(RL.status)], "open");
  const priority = select([["", "همه اولویت‌ها"], ...Object.entries(RL.priority)], "");
  const type = select([["", "همه انواع"], ...types.map((t) => [t.id, t.name])], "");
  const assigned = select([["", "همه"], ["me", "ارجاع‌شده به من"], ["none", "بدون کارشناس"]], "");
  const q = h("input", { type: "search", placeholder: "کد پیگیری یا متن درخواست" });
  const list = h("div");
  const pager = h("div", { class: "pager" });
  let page = 1;
  const size = 30;
  const query = () => {
    const p = new URLSearchParams();
    for (const [k, el] of [["status", status], ["priority", priority], ["type_id", type], ["assigned", assigned]]) {
      if (el.value) p.set(k, el.value);
    }
    if (q.value.trim()) p.set("q", q.value.trim());
    return p;
  };
  async function load() {
    const p = query();
    p.set("page", String(page));
    p.set("size", String(size));
    const res = await api("/requests?" + p);
    fill(list, res.items.length
      ? h("div", { class: "card flush" }, table(["درخواست", "درخواست‌کننده", "اولویت", "وضعیت", "کارشناس", "ثبت"],
        res.items.map((r) => ({ item: r, cells: [
          h("div", { class: "req-cell" },
            h("div", {}, h("span", { class: "mono code" }, r.tracking_code), " ", h("b", {}, r.type_name)),
            h("div", { class: "muted clamp1" }, r.description)),
          h("div", { class: "person" }, h("b", {}, r.requester.full_name), h("span", { class: "muted" }, r.requester_unit_name || "—")),
          priorityBadge(r.priority),
          [statusBadge(r.status), handles ? null : h("div", { class: "muted small" }, r.handler_unit_name)],
          r.assigned_to ? r.assigned_to.full_name : h("span", { class: "muted" }, "—"),
          h("span", { class: "muted nowrap", title: fmtDate(r.created_at, true) }, ago(r.created_at)),
        ] })), (r) => (location.hash = "#/requests/" + r.id)))
      : emptyState("inbox", "درخواستی نیست", status.value === "open" ? "همه درخواست‌های باز رسیدگی شده‌اند." : "با این فیلترها درخواستی پیدا نشد."));
    const pages = Math.max(1, Math.ceil(res.total / size));
    fill(pager, res.total > size ? [
      h("button", { class: "btn small secondary", disabled: page <= 1, onclick: () => { page--; load().catch(showError); } }, "قبلی"),
      h("span", { class: "muted" }, "صفحه " + fa(page) + " از " + fa(pages) + " (" + fa(res.total) + " درخواست)"),
      h("button", { class: "btn small secondary", disabled: page >= pages, onclick: () => { page++; load().catch(showError); } }, "بعدی"),
    ] : h("span", { class: "muted" }, fa(res.total) + " درخواست"));
  }
  const reload = () => { page = 1; load().catch(showError); };
  [status, priority, type, assigned].forEach((el) => el.addEventListener("change", reload));
  let timer;
  q.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(reload, 300); });

  const critical = openRows.filter((r) => r.priority === "critical").length;
  const unassigned = openRows.filter((r) => !r.assigned_to).length;
  const mine = openRows.filter((r) => r.assigned_to && r.assigned_to.id === state.me.id).length;
  fill(main,
    pageHead("درخواست‌های پشتیبانی",
      handles ? "درخواست‌های رسیده به واحد شما؛ بحرانی و فوری‌ها بالاتر و قدیمی‌ترها جلوترند" : "درخواست‌هایی که کارکنان واحد شما ثبت کرده‌اند",
      iconBtn("download", "خروجی اکسل (CSV)", () => downloadFile("/requests/export.csv?" + query(), "requests.csv").catch(showError))),
    tabs([["queue", "کارتابل", "inbox"], ["dashboard", "داشبورد و آمار", "dashboard"]], "queue",
      (t) => (location.hash = t === "dashboard" ? "#/requests/dashboard" : "#/requests")),
    h("div", { class: "kpis" },
      kpi("inbox", "درخواست باز", fa(openRows.length), null, "blue"),
      kpi("warning", "بحرانی باز", fa(critical), critical ? "اول به این‌ها رسیدگی کنید" : null, critical ? "red" : "gray"),
      kpi("user", "بدون کارشناس", fa(unassigned), null, unassigned ? "amber" : "gray"),
      handles ? kpi("build", "ارجاع‌شده به من", fa(mine), null, "green") : null),
    h("div", { class: "toolbar filters" }, q, status, priority, type, handles ? assigned : null),
    list,
    pager,
  );
  await load();
}

// ------------------------------------------------------------------ جزئیات و رسیدگی

async function renderRequest(main, id) {
  let d;
  try {
    d = await api("/requests/" + id);
  } catch (e) {
    if (e.status !== 404) throw e;
    return fill(main, h("a", { class: "back", href: "#/requests" }, icon("back"), "کارتابل"),
      emptyState("inbox", "درخواست در دسترس نیست", "ممکن است به واحد دیگری ارجاع شده باشد."));
  }
  const show = (next) => { d = next; draw(); };
  const timeline = h("div");
  function draw() {
    const transitions = d.actions.filter((a) => a.startsWith("status:")).map((a) => a.slice(7))
      .sort((a, b) => Object.keys(TRANSITION_BTN).indexOf(a) - Object.keys(TRANSITION_BTN).indexOf(b));
    const buttons = transitions.map((s) => {
      const [label, ic, cls] = TRANSITION_BTN[s];
      return iconBtn(ic, label, () => statusDialog(d, s, show), cls);
    });
    if (d.actions.includes("assign")) buttons.push(iconBtn("user", "ارجاع به کارشناس", () => assignDialog(d, show)));
    if (d.actions.includes("transfer")) buttons.push(iconBtn("transfer", "ارجاع به واحد دیگر", () => transferDialog(d)));
    const info = (label, value) => (value ? h("div", { class: "info-row" }, h("span", { class: "muted" }, label), h("span", {}, value)) : null);
    fill(main,
      h("a", { class: "back", href: "#/requests" }, icon("back"), "کارتابل"),
      pageHead(d.type_name, null, ...buttons),
      h("div", { class: "subhead" }, h("span", { class: "mono code" }, d.tracking_code), statusBadge(d.status), priorityBadge(d.priority),
        badge(RL.category[d.category] || d.category, CATEGORY_TONE[d.category]),
        h("span", { class: "muted" }, icon("clock"), " " + fmtDate(d.created_at, true) + " (" + ago(d.created_at) + ")")),
      h("div", { class: "req-grid" },
        h("div", { class: "stack" },
          h("div", { class: "card" },
            h("h3", {}, "شرح درخواست"),
            h("div", { class: "pre req-desc" }, d.description),
            d.location ? h("p", { class: "with-icon" }, icon("place"), d.location) : null,
            d.answers.length ? h("div", { class: "answers" }, d.answers.map((a) => info(a.label, faDigits(a.value)))) : null),
          d.resolution ? h("div", { class: "card ok-card" }, h("h3", {}, "نتیجه رسیدگی"), h("div", { class: "pre" }, d.resolution)) : null,
          d.satisfaction ? h("div", { class: "card" }, h("h3", {}, "نظر درخواست‌کننده"), stars(d.satisfaction),
            d.satisfaction_comment ? h("p", { class: "pre" }, d.satisfaction_comment) : null) : null,
          h("div", { class: "card" },
            h("h3", {}, "روند رسیدگی"),
            timeline,
            composer(d, show))),
        h("div", { class: "stack" },
          h("div", { class: "card" },
            h("h3", {}, "مشخصات"),
            info("درخواست‌کننده", d.requester.full_name + " (" + faDigits(d.requester.personnel_code) + ")"),
            info("سمت", d.requester.job_title),
            info("واحد درخواست‌کننده", d.requester_unit_name),
            info("واحد مسئول", d.handler_unit_name),
            info("کارشناس", d.assigned_to ? d.assigned_to.full_name : "هنوز ارجاع نشده"),
            info("پذیرش", d.accepted_at ? fmtDate(d.accepted_at, true) : null),
            info("انجام", d.done_at ? fmtDate(d.done_at, true) : null),
            info("بسته شدن", d.closed_at ? fmtDate(d.closed_at, true) : null),
            d.status === "done" && d.reopen_until
              ? h("p", { class: "muted small" }, "اگر درخواست‌کننده تا " + fmtDate(d.reopen_until, true) + " اعتراض نکند، خودکار بسته می‌شود.")
              : null),
          attachmentsCard(d))),
    );
    drawTimeline();
  }
  function drawTimeline() {
    fill(timeline, h("ol", { class: "timeline" }, d.events.map((e) => {
      const tone = e.kind === "status" || e.kind === "rated" || e.kind === "reopened" ? STATUS_TONE[e.to_status] || "gray"
        : e.kind === "transfer" ? "amber" : e.kind === "created" ? "blue" : "gray";
      return h("li", { class: "tl-item" },
        h("span", { class: "tl-dot " + tone }),
        h("div", { class: "tl-body" },
          h("div", { class: "tl-head" }, h("b", {}, eventTitle(e)),
            h("span", { class: "muted" }, (e.actor ? e.actor.full_name + " · " : "") + fmtDate(e.at, true))),
          e.note ? h("div", { class: "pre tl-note" + (e.kind === "comment" ? " bubble" : "") }, e.note) : null,
          e.attachment ? attachmentLink(e.attachment) : null));
    })));
  }
  draw();
}

function eventTitle(e) {
  switch (e.kind) {
    case "created": return "درخواست ثبت شد";
    case "status": return e.to_status === "closed" && !e.actor ? "بسته شدن خودکار" : "وضعیت: " + (RL.status[e.to_status] || e.to_status);
    case "assign": return "ارجاع به کارشناس";
    case "transfer": return "ارجاع به واحد دیگر";
    case "comment": return "یادداشت";
    case "attachment": return "پیوست";
    case "rated": return "امتیاز درخواست‌کننده";
    case "reopened": return "اعتراض و بازگشایی";
    default: return e.kind;
  }
}

function stars(n) {
  return h("div", { class: "stars", title: fa(n) + " از ۵" }, [1, 2, 3, 4, 5].map((i) => icon("star", i <= n ? "on" : "off")));
}

const isImage = (a) => a.content_type.startsWith("image/");

function attachmentLink(a) {
  return h("a", { class: "attachment", href: a.url, target: "_blank", rel: "noopener" },
    isImage(a) ? h("img", { src: a.url, alt: a.original_name, loading: "lazy" }) : icon("pdf"),
    h("span", {}, a.original_name), h("span", { class: "muted" }, fmtSize(a.size)));
}

function attachmentsCard(d) {
  const files = d.events.filter((e) => e.attachment).map((e) => e.attachment);
  if (!files.length) return null;
  return h("div", { class: "card" }, h("h3", {}, "پیوست‌ها (" + fa(files.length) + ")"),
    h("div", { class: "attach-grid" }, files.map(attachmentLink)));
}

/** نوشتن یادداشت و افزودن پیوست زیر روند رسیدگی. */
function composer(d, show) {
  const canComment = d.actions.includes("comment");
  const canAttach = d.actions.includes("attach");
  if (!canComment && !canAttach) return null;
  const text = h("textarea", { placeholder: "یادداشت برای درخواست‌کننده یا همکاران…", maxlength: "2000" });
  const file = h("input", { type: "file", accept: ".jpg,.jpeg,.png,.webp,.pdf", hidden: true });
  const progress = h("span", { class: "muted" });
  const send = h("button", { class: "btn primary", type: "button", onclick: async () => {
    if (!text.value.trim()) return text.focus();
    send.disabled = true;
    try {
      show(await api("/requests/" + d.id + "/comments", { method: "POST", body: { text: text.value.trim() } }));
      toast("یادداشت ثبت شد");
    } catch (e) { showError(e); } finally { send.disabled = false; }
  } }, icon("chat"), h("span", {}, "ثبت یادداشت"));
  file.addEventListener("change", async () => {
    const f = file.files[0];
    if (!f) return;
    try {
      const res = await uploadFile(f, (p) => (progress.textContent = "در حال بارگذاری " + fa(Math.round(p * 100)) + "٪"),
        "/requests/" + d.id + "/attachments");
      progress.textContent = "";
      toast("پیوست افزوده شد");
      show(res);
    } catch (e) { progress.textContent = ""; showError(e); }
  });
  return h("div", { class: "composer" },
    canComment ? text : null,
    h("div", { class: "toolbar" },
      canComment ? send : null,
      canAttach ? iconBtn("attach", "پیوست عکس یا PDF", () => file.click()) : null,
      progress, file));
}

function statusDialog(d, next, show) {
  const needsResolution = next === "done";
  const needsNote = next === "rejected";
  const resolution = h("textarea", { required: needsResolution, placeholder: "چه کاری انجام شد؟ (به درخواست‌کننده نمایش داده می‌شود)" });
  const note = h("textarea", { required: needsNote, placeholder: needsNote ? "دلیل رد را بنویسید" : "اختیاری" });
  const [label] = TRANSITION_BTN[next];
  dialog(label + ": " + d.tracking_code, h("div", { class: "form" },
    needsResolution ? field("نتیجه رسیدگی", resolution) : null,
    field(needsNote ? "دلیل رد" : next === "on_hold" ? "منتظر چه هستید؟ (مثلاً قطعه)" : "توضیح", note),
    next === "accepted" && !d.assigned_to ? h("p", { class: "muted" }, "درخواست به نام شما ارجاع می‌شود.") : null), {
    saveLabel: label,
    onSave: async () => {
      show(await api("/requests/" + d.id + "/status", { method: "POST", body: {
        status: next, note: note.value.trim() || null, resolution: resolution.value.trim() || null,
      } }));
      toast("وضعیت به‌روز شد");
    },
  });
}

async function assignDialog(d, show) {
  let agents;
  try { agents = await api("/requests/" + d.id + "/agents"); } catch (e) { return showError(e); }
  const who = select([["", "— بدون کارشناس —"], ...agents.map((a) => [a.id, a.full_name + (a.job_title ? " (" + a.job_title + ")" : "")])],
    d.assigned_to ? d.assigned_to.id : "");
  dialog("ارجاع به کارشناس", h("div", { class: "form" }, field("کارشناس واحد " + d.handler_unit_name, who)), {
    saveLabel: "ارجاع",
    onSave: async () => {
      show(await api("/requests/" + d.id + "/assign", { method: "POST", body: { user_id: who.value || null } }));
      toast("ارجاع شد");
    },
  });
}

async function transferDialog(d) {
  const { units } = await supportCatalog();
  const targets = units.filter((u) => u.id !== d.handler_unit_id);
  if (!targets.length) return toast("واحد پشتیبانی دیگری تعریف نشده است", true);
  const unit = select(targets.map((u) => [u.id, u.name]), targets[0].id);
  const note = h("textarea", { required: true, placeholder: "مثلاً: مشکل از برق پریز است، نه از دستگاه" });
  dialog("ارجاع به واحد دیگر", h("div", { class: "form" }, field("واحد مقصد", unit), field("دلیل ارجاع", note),
    h("p", { class: "muted" }, "درخواست در کارتابل واحد مقصد با وضعیت «ثبت‌شده» قرار می‌گیرد.")), {
    saveLabel: "ارجاع",
    onSave: async () => {
      const res = await api("/requests/" + d.id + "/transfer", { method: "POST", body: { unit_id: unit.value, note: note.value.trim() } });
      toast("به واحد " + targets.find((u) => u.id === unit.value).name + " ارجاع شد");
      location.hash = res ? "#/requests/" + d.id : "#/requests";
      if (res) route();
    },
  });
}

// ------------------------------------------------------------------ داشبورد

async function renderRequestDashboard(main) {
  const days = select([["7", "۷ روز اخیر"], ["30", "۳۰ روز اخیر"], ["90", "۹۰ روز اخیر"], ["365", "یک سال اخیر"]], "30");
  const box = h("div");
  async function load() {
    const s = await api("/requests/stats?days=" + days.value);
    const rows = (list) => list.map((r) => ({ label: r.label, value: r.count }));
    fill(box,
      h("div", { class: "kpis" },
        kpi("inbox", "کل درخواست‌ها", fa(s.total), null, "blue"),
        kpi("build", "باز", fa(s.open), s.critical_open ? fa(s.critical_open) + " مورد بحرانی" : null, s.critical_open ? "red" : "amber"),
        kpi("clock", "میانگین تا پذیرش", hoursLabel(s.avg_response_hours), null, "gray"),
        kpi("check", "میانگین تا انجام", hoursLabel(s.avg_resolution_hours), fa(s.done) + " درخواست انجام شده", "green"),
        kpi("star", "رضایت", s.avg_satisfaction ? fa(s.avg_satisfaction) + " از ۵" : "—", fa(s.rated) + " امتیاز", "amber")),
      h("div", { class: "grid2" },
        h("div", { class: "card" }, h("h3", {}, "بر اساس وضعیت"), barList(rows(s.by_status))),
        h("div", { class: "card" }, h("h3", {}, "بر اساس نوع درخواست"), barList(rows(s.by_type))),
        h("div", { class: "card" }, h("h3", {}, "بر اساس واحد مسئول"), barList(rows(s.by_handler_unit))),
        h("div", { class: "card" }, h("h3", {}, "بر اساس واحد درخواست‌کننده"), barList(rows(s.by_requester_unit)))),
      h("div", { class: "card mt" }, h("h3", {}, "تعداد ثبت در هر روز"),
        barList(s.daily.map((r) => ({ label: fmtDate(r.key + "T12:00"), value: r.count })))),
    );
  }
  days.addEventListener("change", () => load().catch(showError));
  fill(main,
    pageHead("درخواست‌های پشتیبانی", "آمار درخواست‌هایی که در کارتابل شما دیده می‌شوند", days,
      iconBtn("download", "خروجی اکسل (CSV)", () => downloadFile("/requests/export.csv?days=" + days.value, "requests.csv").catch(showError))),
    tabs([["queue", "کارتابل", "inbox"], ["dashboard", "داشبورد و آمار", "dashboard"]], "dashboard",
      (t) => (location.hash = t === "dashboard" ? "#/requests/dashboard" : "#/requests")),
    box,
  );
  await load();
}

// ------------------------------------------------------------------ انواع درخواست

async function renderRequestTypes(main) {
  await catalog();
  const types = await api("/admin/request-types");
  fill(main,
    pageHead("انواع درخواست", "هر نوع درخواست به یک واحد پشتیبانی می‌رسد؛ می‌توانید چند فیلد ساده هم برایش تعریف کنید",
      iconBtn("add", "نوع جدید", () => requestTypeDialog(null), "primary")),
    types.length
      ? h("div", { class: "card flush" }, table(["نوع درخواست", "دسته", "واحد مسئول", "فیلدها", "درخواست‌ها", "وضعیت", ""],
        types.map((t) => ({ item: t, cells: [
          h("div", { class: "person" }, h("b", {}, t.name), t.description ? h("span", { class: "muted" }, t.description) : null),
          badge(RL.category[t.category] || t.category, CATEGORY_TONE[t.category]),
          t.handler_unit_name || "—",
          t.fields.length ? t.fields.map((f) => h("span", { class: "tag gray" }, f.label + (f.required ? " *" : ""))) : h("span", { class: "muted" }, "—"),
          fa(t.request_count),
          t.is_active ? badge("فعال", "green") : badge("غیرفعال", "gray"),
          h("div", { class: "actions" },
            h("button", { class: "icon-btn", type: "button", title: "ویرایش", onclick: (e) => { e.stopPropagation(); requestTypeDialog(t); } }, icon("edit")),
            t.request_count ? null : h("button", { class: "icon-btn danger", type: "button", title: "حذف", onclick: async (e) => {
              e.stopPropagation();
              if (!confirm("نوع «" + t.name + "» حذف شود؟")) return;
              try { await api("/admin/request-types/" + t.id, { method: "DELETE" }); toast("حذف شد"); route(); } catch (err) { showError(err); }
            } }, icon("delete"))),
        ] })), (t) => requestTypeDialog(t)))
      : emptyState("category", "هنوز نوع درخواستی تعریف نشده", "مثلاً «خرابی پرینتر» با واحد مسئول «فناوری اطلاعات».",
        iconBtn("add", "نوع جدید", () => requestTypeDialog(null), "primary")),
  );
}

/** سازنده فیلدهای نوع درخواست: برچسب، نوع، گزینه‌ها و لازم بودن. شناسه فیلد خودکار ساخته می‌شود. */
function fieldBuilder(initial) {
  const rows = [];
  const box = h("div", { class: "field-rows" });
  const empty = h("p", { class: "muted" }, "فیلدی تعریف نشده؛ کاربر فقط شرح و مکان را می‌نویسد.");
  const used = new Set(initial.map((f) => f.id));
  const newId = () => { let i = 1; while (used.has("f" + i)) i++; used.add("f" + i); return "f" + i; };
  function add(f) {
    const label = h("input", { type: "text", required: true, maxlength: "200", placeholder: "مثلاً شماره اموال", value: f ? f.label : "" });
    const kind = select(Object.entries(RL.fieldKind), f ? f.kind : "text");
    const options = h("textarea", { placeholder: "هر گزینه در یک خط", rows: "3" }, f && f.options ? f.options.join("\n") : "");
    const required = h("input", { type: "checkbox", checked: f ? f.required : false });
    const optWrap = h("div", { class: "field-opts" }, options);
    const sync = () => { optWrap.hidden = kind.value !== "select"; };
    kind.addEventListener("change", sync);
    sync();
    const entry = { id: f ? f.id : newId(), label, kind, options, required };
    const row = h("div", { class: "field-row" },
      h("div", { class: "field-main" }, label, kind,
        h("label", { class: "check" }, required, "لازم"),
        h("button", { class: "icon-btn danger", type: "button", title: "حذف فیلد", onclick: () => {
          rows.splice(rows.indexOf(entry), 1); row.remove(); empty.hidden = rows.length > 0;
        } }, icon("delete"))),
      optWrap);
    rows.push(entry);
    box.append(row);
    empty.hidden = true;
  }
  initial.forEach(add);
  empty.hidden = rows.length > 0;
  return {
    el: h("div", { class: "aud-group" },
      h("div", { class: "aud-title" }, "فیلدهای اضافه", h("span", { class: "muted" }, " (اختیاری)")),
      empty, box,
      h("button", { class: "btn small secondary", type: "button", onclick: () => add(null) }, icon("add"), h("span", {}, "افزودن فیلد"))),
    value: () => rows.map((r) => ({
      id: r.id,
      label: r.label.value.trim(),
      kind: r.kind.value,
      options: r.kind.value === "select" ? r.options.value.split("\n").map((s) => s.trim()).filter(Boolean) : [],
      required: r.required.checked,
    })),
  };
}

function requestTypeDialog(t) {
  const name = h("input", { type: "text", required: true, maxlength: "200", value: t ? t.name : "", placeholder: "مثلاً خرابی پرینتر" });
  const category = select(Object.entries(RL.category), t ? t.category : "it");
  const units = [...state.units].sort((a, b) => (a.type === "support" ? 0 : 1) - (b.type === "support" ? 0 : 1));
  const unit = select(units.map((u) => [u.id, u.name + (u.type === "support" ? "" : " (" + (L.unitType[u.type] || u.type) + ")")]),
    t ? t.handler_unit_id : (units[0] || {}).id);
  // برای نوع جدید، واحد هم‌نام دسته (مثلاً «تأسیسات») پیش‌فرض می‌شود
  if (!t) {
    const byCategory = () => {
      const match = units.find((u) => u.name === RL.category[category.value]);
      if (match) unit.value = match.id;
    };
    category.addEventListener("change", byCategory);
    byCategory();
  }
  const desc = h("textarea", { placeholder: "راهنمای کوتاه برای کاربر (اختیاری)" }, t && t.description ? t.description : "");
  const order = h("input", { type: "number", value: t ? String(t.sort_order) : "0" });
  const active = checkbox("در اپ برای ثبت درخواست نمایش داده شود", t ? t.is_active : true);
  const fields = fieldBuilder(t ? t.fields : []);
  dialog(t ? "ویرایش نوع درخواست" : "نوع درخواست جدید", h("div", { class: "form" },
    field("نام", name),
    h("div", { class: "row" }, field("دسته", category), field("واحد مسئول رسیدگی", unit)),
    field("توضیح", desc),
    h("div", { class: "row" }, field("ترتیب نمایش", order), h("div", { class: "check-cell" }, active.el)),
    fields.el), {
    wide: true,
    onSave: async () => {
      const f = fields.value();
      if (f.some((x) => !x.label)) throw new Error("برچسب همه فیلدها را بنویسید");
      if (f.some((x) => x.kind === "select" && x.options.length < 2)) throw new Error("فیلد انتخابی دست‌کم دو گزینه لازم دارد");
      const body = {
        name: name.value.trim(), category: category.value, handler_unit_id: unit.value,
        description: desc.value.trim() || null, sort_order: parseInt(order.value, 10) || 0,
        is_active: active.input.checked, fields: f,
      };
      if (t) await api("/admin/request-types/" + t.id, { method: "PATCH", body });
      else await api("/admin/request-types", { method: "POST", body });
      toast("ذخیره شد");
      route();
    },
  });
}
