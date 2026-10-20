/*
 * برنامه شیفت بخش‌ها در پنل: فهرست ماه، جدول ماهانه و ویرایش آن، تأیید مترون، کشیک امروز،
 * کدهای شیفت، تنظیمات واحدها و تعطیلات.
 * پس از requests.js و پیش از app.js بارگذاری می‌شود (از icon، badge، kpi، dialog و ... استفاده می‌کند).
 */
"use strict";

Object.assign(ICONS, {
  calendar: "M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2zm-8 4H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z",
  clock: "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
  tune: "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
  send: "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z",
  undo: "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
  chevronRight: "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z",
  chevronLeft: "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z",
  repeat: "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z",
  personAdd: "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
  phone: "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z",
});

// ------------------------------------------------------------------ برچسب‌ها

const SL = {
  status: { draft: "پیش‌نویس", submitted: "در انتظار تأیید", published: "منتشرشده" },
  kind: { work: "کاری", off: "استراحت", leave: "مرخصی یا مأموریت" },
  period: { morning: "صبح", evening: "عصر", night: "شب" },
  months: ["فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"],
  weekdayShort: ["د", "س", "چ", "پ", "ج", "ش", "ی"], // date.weekday(): دوشنبه = ۰
  weekdays: ["دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه"],
  warning: {
    overlap: "همپوشانی", short_rest: "استراحت کوتاه", consecutive: "روزهای پشت سر هم",
    wrong_weekday: "روز هفته نادرست", over_hours: "ساعت زیاد", understaffed: "کمبود نیرو",
  },
};
const SCHEDULE_TONE = { draft: "gray", submitted: "amber", published: "green" };
const scheduleBadge = (s) => badge(SL.status[s] || s, SCHEDULE_TONE[s]);

/** رنگ خانه جدول از روی برچسب: صبح، عصر، شب، آنکال، استراحت، مرخصی، ترکیبی. */
function shiftTone(label, kind) {
  if (kind === "leave") return "s-leave";
  if (kind === "off") return "s-off";
  const first = (label || "").trim().split(/\s+/)[0];
  if (first.startsWith("آ")) return "s-oncall";
  if ((label || "").trim().split(/\s+/).length > 2 || /ساعت/.test(label || "")) return "s-long";
  if (first === "ص") return label.includes("ش") ? "s-long" : "s-morning";
  if (first === "ع") return label.includes("ش") ? "s-evening-night" : "s-evening";
  if (first === "ش" || first.startsWith("شب")) return "s-night";
  return "s-other";
}

const hoursText = (n) => (n === null || n === undefined ? "—" : fa(Math.round(n * 10) / 10));

/** سال و ماه شمسی امروز از تقویم مرورگر. */
function currentJalali() {
  const parts = new Intl.DateTimeFormat("en-u-ca-persian-nu-latn", { year: "numeric", month: "numeric" }).formatToParts(new Date());
  return { y: Number(parts.find((p) => p.type === "year").value), m: Number(parts.find((p) => p.type === "month").value) };
}
const shiftMonth = ({ y, m }, d) => {
  const i = y * 12 + (m - 1) + d;
  return { y: Math.floor(i / 12), m: (i % 12) + 1 };
};
const monthTitle = ({ y, m }) => SL.months[m - 1] + " " + faDigits(y);
const jdate = (jalali) => faDigits(jalali.split("/").slice(1).map(Number).join("/"));

let shiftTypesCache = null;
async function shiftTypes(force = false) {
  if (!shiftTypesCache || force) {
    const rows = await api("/shift-types");
    shiftTypesCache = { list: rows, byCode: new Map(rows.map((t) => [t.code, t])) };
  }
  return shiftTypesCache;
}

function monthPicker(month, onChange) {
  return h("div", { class: "month-picker" },
    h("button", { class: "icon-btn", type: "button", title: "ماه قبل", onclick: () => onChange(shiftMonth(month, -1)) }, icon("chevronRight")),
    h("b", {}, monthTitle(month)),
    h("button", { class: "icon-btn", type: "button", title: "ماه بعد", onclick: () => onChange(shiftMonth(month, 1)) }, icon("chevronLeft")));
}

// ------------------------------------------------------------------ فهرست برنامه‌های ماه

const scheduleMonth = { value: null };

async function renderSchedules(main, sub) {
  if (sub[0]) return renderSchedule(main, sub[0]);
  const month = scheduleMonth.value || shiftMonth(currentJalali(), 1);
  const rows = await api(`/schedules?jyear=${month.y}&jmonth=${month.m}`);
  const approver = can("schedule.approve");
  const go = (m) => { scheduleMonth.value = m; route(); };
  const withS = rows.filter((r) => r.schedule);
  const count = (st) => withS.filter((r) => r.schedule.status === st).length;
  const pending = count("submitted");

  async function create(row, copy) {
    try {
      const d = await api("/schedules", { method: "POST", body: { unit_id: row.unit_id, jyear: month.y, jmonth: month.m, copy_previous: copy } });
      location.hash = "#/schedules/" + d.id;
    } catch (e) { showError(e); }
  }

  const sorted = [...rows].sort((a, b) => {
    const rank = (r) => (r.schedule ? (r.schedule.status === "submitted" ? 0 : r.schedule.status === "draft" ? 1 : 2) : r.can_create ? 3 : 4);
    return rank(a) - rank(b) || a.unit_name.localeCompare(b.unit_name, "fa");
  });
  const list = sorted.length
    ? h("div", { class: "card flush" }, table(["واحد", "وضعیت", "نیروها", "آخرین تغییر", ""],
      sorted.map((r) => ({
        item: r,
        cells: [
          h("div", { class: "person" }, h("b", {}, r.unit_name), h("span", { class: "muted" }, L.unitType[r.unit_type] || "")),
          r.schedule
            ? [scheduleBadge(r.schedule.status),
                approver && r.schedule.status === "submitted" ? h("div", { class: "muted small" }, "منتظر تأیید شما") : null,
                r.schedule.status === "draft" && r.schedule.return_note ? h("div", { class: "muted small" }, "برگشت‌خورده") : null]
            : h("span", { class: "muted" }, "ساخته نشده"),
          r.schedule ? fa(r.schedule.member_count) + " نفر" : "—",
          r.schedule ? h("span", { class: "muted nowrap" }, ago(r.schedule.updated_at)) : "—",
          r.can_create
            ? h("div", { class: "actions" },
                h("button", { class: "btn small", type: "button", onclick: (e) => { e.stopPropagation(); create(r, false); } }, "ساخت برنامه"),
                h("button", { class: "btn small secondary", type: "button", onclick: (e) => { e.stopPropagation(); create(r, true); } }, "کپی از ماه قبل"))
            : r.schedule ? h("span", { class: "link" }, "باز کردن") : null,
        ],
      })), (r) => r.schedule && (location.hash = "#/schedules/" + r.schedule.id)))
    : emptyState("calendar", "واحدی نیست", "برنامه هیچ واحدی برای شما قابل دیدن نیست.");

  fill(main,
    pageHead("برنامه شیفت", approver
      ? "برنامه ماهانه همه واحدها؛ برنامه‌های منتظر تأیید بالاتر آمده‌اند"
      : "برنامه ماهانه نیروهای واحد شما؛ پس از نوشتن، برای تأیید مترون بفرستید",
      monthPicker(month, go)),
    h("div", { class: "kpis" },
      kpi("calendar", "برنامه ساخته‌شده", fa(withS.length) + " از " + fa(rows.length), null, "blue"),
      kpi("send", "منتظر تأیید", fa(pending), approver && pending ? "برای بررسی روی ردیف بزنید" : null, pending ? "amber" : "gray"),
      kpi("check", "منتشرشده", fa(count("published")), null, "green"),
      kpi("edit", "پیش‌نویس", fa(count("draft")), null, "gray")),
    list);
}

// ------------------------------------------------------------------ جدول یک برنامه

async function renderSchedule(main, id) {
  const [d, types] = await Promise.all([api("/schedules/" + id), shiftTypes()]);
  const editable = d.actions.includes("edit");
  const allowed = types.list.filter((t) => t.is_active && d.settings.shift_groups.includes(t.group));
  const pending = new Map(); // "userId|date" → کد یا null
  const key = (u, day) => u + "|" + day;
  const selection = new Set();
  let anchor = null;
  const cellEls = new Map();

  const base = new Map();
  for (const m of d.members) for (const c of m.cells) base.set(key(m.user_id, c.day), c);
  const valueAt = (k) => (pending.has(k) ? pending.get(k) : base.get(k)?.code ?? null);

  const saveBtn = h("button", { class: "btn", type: "button", disabled: true, onclick: () => save() }, icon("check"), h("span", {}, "ذخیره تغییرات"));
  const undoBtn = h("button", { class: "btn secondary", type: "button", disabled: true, onclick: () => { pending.clear(); paintAll(); } }, icon("undo"), h("span", {}, "لغو تغییرات"));
  const codeInput = h("input", { type: "text", inputmode: "numeric", class: "code-input", placeholder: "کد", dir: "ltr", list: "shift-codes" });
  const codeHint = h("span", { class: "muted small" });
  const datalist = h("datalist", { id: "shift-codes" }, allowed.map((t) => h("option", { value: String(t.code) }, t.label + " · " + t.title)));

  // ------- نقاشی خانه‌ها
  function paintCell(k) {
    const td = cellEls.get(k);
    if (!td) return;
    const code = valueAt(k);
    const orig = base.get(k);
    const t = code !== null ? types.byCode.get(code) : null;
    const label = code === null ? (orig && orig.code === null && !pending.has(k) ? orig.label : "") : t ? t.label : String(code);
    const kind = t ? t.kind : orig && !pending.has(k) ? orig.kind : null;
    td.className = "sc " + (label ? shiftTone(label, kind) : "") + (pending.has(k) ? " dirty" : "") + (selection.has(k) ? " sel" : "")
      + (td.dataset.holiday ? " holiday" : "") + (td.dataset.covered && !label ? " covered" : "");
    td.textContent = label ? faDigits(label) : "";
    td.title = t ? `${faDigits(t.code)} · ${t.title}` : "";
  }
  function paintAll() {
    for (const k of cellEls.keys()) paintCell(k);
    const n = pending.size;
    saveBtn.disabled = !n;
    undoBtn.disabled = !n;
    saveBtn.querySelector("span").textContent = n ? `ذخیره تغییرات (${fa(n)})` : "ذخیره تغییرات";
  }

  function pick(k, ev) {
    if (!editable) return;
    const [u, day] = k.split("|");
    if (ev && ev.shiftKey && anchor && anchor.split("|")[0] === u) {
      const days = d.days.map((x) => x.date);
      const a = days.indexOf(anchor.split("|")[1]);
      const b = days.indexOf(day);
      if (!(ev.ctrlKey || ev.metaKey)) selection.clear();
      for (let i = Math.min(a, b); i <= Math.max(a, b); i++) selection.add(key(u, days[i]));
    } else if (ev && (ev.ctrlKey || ev.metaKey)) {
      selection.has(k) ? selection.delete(k) : selection.add(k);
      anchor = k;
    } else {
      selection.clear();
      selection.add(k);
      anchor = k;
    }
    paintAll();
    codeInput.focus();
    codeInput.select();
  }

  function apply(code) {
    if (!selection.size) return toast("ابتدا خانه‌ای را انتخاب کنید", true);
    if (code !== null) {
      const t = types.byCode.get(code);
      if (!t) return toast(`کد ${faDigits(code)} تعریف نشده است`, true);
      if (!t.is_active) return toast(`کد ${faDigits(code)} دیگر استفاده نمی‌شود` + (t.replaced_by ? `؛ به جای آن کد ${faDigits(t.replaced_by)}` : ""), true);
      if (!d.settings.shift_groups.includes(t.group)) return toast(`کد ${faDigits(code)} (${t.group_name}) برای این واحد مجاز نیست`, true);
    }
    for (const k of selection) {
      const orig = base.get(k)?.code ?? null;
      if (code === orig) pending.delete(k);
      else pending.set(k, code);
    }
    // پس از ثبت، یک خانه جلو برو تا نوشتن پشت سر هم سریع باشد
    if (selection.size === 1) {
      const [u, day] = [...selection][0].split("|");
      const days = d.days.map((x) => x.date);
      const next = days[days.indexOf(day) + 1];
      if (next) { selection.clear(); selection.add(key(u, next)); anchor = key(u, next); }
    }
    codeInput.value = "";
    codeHint.textContent = "";
    paintAll();
  }

  codeInput.addEventListener("input", () => {
    const t = types.byCode.get(Number(toLatin(codeInput.value)));
    codeHint.textContent = t ? `${t.label} · ${t.title}` : "";
  });
  codeInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      const v = toLatin(codeInput.value.trim());
      if (v) apply(Number(v));
    } else if ((e.key === "Delete" || e.key === "Backspace") && !codeInput.value) {
      e.preventDefault();
      apply(null);
    } else if (["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key) && !codeInput.value && anchor) {
      e.preventDefault();
      const days = d.days.map((x) => x.date);
      const users = d.members.map((m) => m.user_id);
      let [u, day] = anchor.split("|");
      let di = days.indexOf(day);
      let ui = users.indexOf(u);
      if (e.key === "ArrowLeft") di = Math.min(days.length - 1, di + 1); // راست‌چین: چپ یعنی روز بعد
      if (e.key === "ArrowRight") di = Math.max(0, di - 1);
      if (e.key === "ArrowDown") ui = Math.min(users.length - 1, ui + 1);
      if (e.key === "ArrowUp") ui = Math.max(0, ui - 1);
      pick(key(users[ui], days[di]), e.shiftKey ? e : null);
    }
  });

  async function save() {
    const cells = [...pending].map(([k, code]) => {
      const [user_id, day] = k.split("|");
      return { user_id, day, code };
    });
    const send = async (reason) => {
      await api(`/schedules/${id}/entries`, { method: "PUT", body: { cells, reason } });
      toast("برنامه ذخیره شد");
      route();
    };
    if (d.status !== "published") return send(null).catch(showError);
    const reason = h("input", { type: "text", maxlength: "300", required: true, placeholder: "مثلاً کمبود نیروی شب" });
    dialog("علت تغییر برنامه منتشرشده", h("div", { class: "form" },
      h("p", { class: "muted" }, `${fa(cells.length)} خانه تغییر می‌کند. فقط افرادی که خانه‌شان عوض شده اعلان می‌گیرند و تغییر در تاریخچه ثبت می‌شود.`),
      field("علت", reason)), { saveLabel: "ذخیره و اطلاع به افراد", onSave: () => send(reason.value.trim()) });
    reason.focus();
  }

  // ------- جدول
  const minStaff = d.settings.min_staff || {};
  const coverage = new Map(d.coverage.map((c) => [c.date, c]));
  const warnByCell = new Map();
  for (const w of d.warnings) {
    if (w.user_id && w.day) warnByCell.set(key(w.user_id, w.day), w);
  }
  const showOvertime = d.members.some((m) => m.overtime_hours !== null);
  const headRow = h("tr", {},
    h("th", { class: "sticky-col" }, "نام"),
    d.days.map((day) => h("th", { class: "day-h" + (day.is_holiday ? " holiday" : ""), title: day.weekday_name + " " + faDigits(day.jalali) + (day.holiday_title ? " · " + day.holiday_title : "") },
      h("div", {}, fa(day.day)), h("div", { class: "wd" }, SL.weekdayShort[day.weekday]))),
    h("th", {}, "کار"), h("th", {}, "آنکال"), showOvertime ? h("th", {}, "اضافه") : null);

  const bodyRows = d.members.map((m) => {
    // خانه‌هایی که شیفت چندروزه روز قبل آن‌ها را هم گرفته
    const covered = new Set();
    const days = d.days.map((x) => x.date);
    for (const c of m.cells) {
      const i = days.indexOf(c.day);
      for (let s = 1; s <= c.span_days; s++) if (days[i + s]) covered.add(days[i + s]);
    }
    return h("tr", {},
      h("th", { class: "sticky-col member" },
        h("div", { class: "person" }, h("b", {}, m.full_name, m.is_guest ? h("span", { class: "badge blue tiny" }, "قرضی") : null),
          h("span", { class: "muted small" }, [m.job_title, faDigits(m.personnel_code)].filter(Boolean).join(" · "))),
        editable && d.actions.includes("members") && d.status === "draft"
          ? h("button", { class: "row-x", type: "button", title: "حذف از برنامه", onclick: () => removeMember(m) }, "×") : null),
      d.days.map((day) => {
        const k = key(m.user_id, day.date);
        const w = warnByCell.get(k);
        const td = h("td", { "data-holiday": day.is_holiday ? "1" : null, "data-covered": covered.has(day.date) ? "1" : null,
          onclick: (e) => pick(k, e) });
        cellEls.set(k, td);
        if (w) td.setAttribute("data-warn", w.message);
        return td;
      }),
      h("td", { class: "num-col" }, hoursText(m.work_hours)),
      h("td", { class: "num-col muted" }, m.oncall_hours ? hoursText(m.oncall_hours) : "—"),
      showOvertime ? h("td", { class: "num-col" }, m.overtime_hours ? hoursText(m.overtime_hours) : "—") : null);
  });

  const covRow = (p) => h("tr", { class: "cov-row" },
    h("th", { class: "sticky-col" }, "نیروی " + SL.period[p] + (minStaff[p] ? ` (حداقل ${fa(minStaff[p])})` : "")),
    d.days.map((day) => {
      const n = coverage.get(day.date)?.[p] ?? 0;
      return h("td", { class: minStaff[p] && n < minStaff[p] ? "low" : day.is_holiday ? "holiday" : null }, fa(n));
    }),
    h("td", {}), h("td", {}), showOvertime ? h("td", {}) : null);

  const grid = h("div", { class: "roster-wrap" },
    h("table", { class: "roster" + (editable ? " editable" : "") },
      h("thead", {}, headRow),
      h("tbody", {}, bodyRows),
      h("tfoot", {}, ["morning", "evening", "night"].map(covRow))));
  paintAll();

  // ------- کارهای برنامه
  async function act(path, body, ok) {
    try {
      await api(`/schedules/${id}/${path}`, { method: "POST", body });
      toast(ok);
      route();
    } catch (e) { showError(e); }
  }
  const unsaved = () => pending.size && toast("ابتدا تغییرات را ذخیره یا لغو کنید", true);
  const actions = [];
  if (d.actions.includes("submit")) actions.push(iconBtn("send", "ارسال برای تأیید", () => unsaved() || act("submit", undefined, "برنامه برای مترون فرستاده شد"), "primary"));
  if (d.actions.includes("approve")) actions.push(iconBtn("check", "تأیید و انتشار", () => act("approve", undefined, "برنامه منتشر شد و به کارکنان اطلاع داده شد"), "primary"));
  if (d.actions.includes("return")) actions.push(iconBtn("undo", "برگرداندن برای اصلاح", () => returnDialog(), "danger"));
  if (d.actions.includes("members")) actions.push(iconBtn("personAdd", "افزودن نیرو", () => addMemberDialog()));
  if (d.actions.includes("changes")) actions.push(iconBtn("history", "تاریخچه تغییرات", () => changesDialog()));
  if (d.actions.includes("export")) actions.push(iconBtn("download", "خروجی اکسل", () => downloadFile(`/schedules/${id}/export.csv`, "schedule.csv").catch(showError)));
  if (d.actions.includes("delete")) actions.push(iconBtn("delete", "حذف پیش‌نویس", () => deleteDialog(), "danger"));

  function returnDialog() {
    const note = h("textarea", { rows: "3", maxlength: "500", required: true, placeholder: "مثلاً نیروی شب پنجشنبه‌ها کم است" });
    dialog("برگرداندن برنامه به مسئول واحد", h("div", { class: "form" }, field("علت و اصلاح لازم", note)),
      { saveLabel: "برگرداندن", onSave: async () => {
        await api(`/schedules/${id}/return`, { method: "POST", body: { note: note.value.trim() } });
        toast("برنامه برگشت داده شد");
        route();
      } });
  }
  function deleteDialog() {
    dialog("حذف پیش‌نویس", h("p", {}, "پیش‌نویس این ماه و همه خانه‌هایش حذف شود؟"), { saveLabel: "حذف", onSave: async () => {
      await api("/schedules/" + id, { method: "DELETE" });
      location.hash = "#/schedules";
    } });
  }
  async function removeMember(m) {
    if (pending.size) return unsaved();
    dialog("حذف از برنامه", h("p", {}, `${m.full_name} و خانه‌هایش از این برنامه حذف شود؟`), { saveLabel: "حذف", onSave: async () => {
      await api(`/schedules/${id}/members/${m.user_id}`, { method: "DELETE" });
      route();
    } });
  }
  function addMemberDialog() {
    if (pending.size) return unsaved();
    const q = h("input", { type: "search", placeholder: "نام یا کد پرسنلی" });
    const results = h("div", { class: "search-results" });
    const have = new Set(d.members.map((m) => m.user_id));
    let timer;
    q.addEventListener("input", () => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        if (!q.value.trim()) return fill(results);
        const ps = await api("/chat/people?q=" + encodeURIComponent(q.value.trim())).catch(() => []);
        fill(results, ...ps.filter((p) => !have.has(p.id)).map((p) => h("button", { class: "result", type: "button", onclick: async () => {
          try {
            await api(`/schedules/${id}/members`, { method: "POST", body: { user_id: p.id } });
            dlg.close();
            toast(p.full_name + " به برنامه افزوده شد");
            route();
          } catch (e) { showError(e); }
        } }, h("b", {}, p.full_name), " ", h("span", { class: "muted" }, [p.job_title, p.unit_name, faDigits(p.personnel_code)].filter(Boolean).join(" · ")))));
      }, 250);
    });
    const dlg = dialog("افزودن نیرو به برنامه", h("div", { class: "form" },
      h("p", { class: "muted" }, "نیروی واحد دیگر به‌عنوان «قرضی» افزوده می‌شود و برنامه‌اش را در اپ می‌بیند."), q, results));
    q.focus();
  }
  async function changesDialog() {
    const rows = await api(`/schedules/${id}/changes`).catch((e) => (showError(e), null));
    if (!rows) return;
    dialog("تاریخچه تغییرات پس از انتشار", rows.length
      ? table(["زمان", "نفر", "روز", "تغییر", "علت", "توسط"], rows.map((r) => ({ item: r, cells: [
          h("span", { class: "nowrap" }, fmtDate(r.at, true)), r.person.full_name, jdate(r.jalali),
          h("span", { class: "nowrap" }, faDigits(r.old_label || "خالی") + " ← " + faDigits(r.new_label || "خالی")),
          r.reason, r.changed_by ? r.changed_by.full_name : "—"] })))
      : emptyState("history", "تغییری ثبت نشده", "پس از انتشار، هر تغییر خانه‌ها اینجا با علتش دیده می‌شود."), { wide: true });
  }
  function patternDialog() {
    const who = select(d.members.map((m) => [m.user_id, m.full_name]), d.members[0]?.user_id);
    const from = select(d.days.map((x) => [x.date, fa(x.day) + " " + x.weekday_name]), d.days[0].date);
    const pattern = h("input", { type: "text", dir: "ltr", required: true, placeholder: "31,32,33,50,50" });
    dialog("پر کردن با الگوی چرخشی", h("div", { class: "form" },
      h("p", { class: "muted" }, "کدها را به ترتیب و با ویرگول بنویسید. الگو از روز انتخاب‌شده تا آخر ماه تکرار می‌شود. برای خانه خالی ۰ بنویسید. پیش از ذخیره می‌توانید نتیجه را ببینید و لغو کنید."),
      field("نیرو", who), field("از روز", from), field("الگو", pattern)), { saveLabel: "پر کن", onSave: () => {
        const codes = toLatin(pattern.value).split(/[,،\s]+/).filter(Boolean).map(Number);
        if (!codes.length || codes.some((c) => Number.isNaN(c))) throw new Error("الگو را با عدد و ویرگول بنویسید");
        for (const c of codes) {
          const t = types.byCode.get(c);
          if (c && (!t || !t.is_active || !d.settings.shift_groups.includes(t.group))) throw new Error(`کد ${faDigits(c)} برای این واحد مجاز نیست`);
        }
        const days = d.days.map((x) => x.date);
        const start = days.indexOf(from.value);
        for (let i = start; i < days.length; i++) {
          const k = key(who.value, days[i]);
          const code = codes[(i - start) % codes.length] || null;
          if (code === (base.get(k)?.code ?? null)) pending.delete(k);
          else pending.set(k, code);
        }
        paintAll();
        return true;
      } });
  }

  // ------- کدهای پرکاربرد برای کلیک سریع
  const quick = [31, 32, 33, 34, 35, 36, 50, 20].map((c) => types.byCode.get(c)).filter((t) => t && allowed.includes(t));
  const palette = editable
    ? h("div", { class: "card palette" },
        h("div", { class: "palette-row" },
          h("label", { class: "code-box" }, h("span", { class: "muted small" }, "کد خانه‌های انتخاب‌شده"), codeInput),
          codeHint, datalist,
          h("div", { class: "spacer" }),
          iconBtn("repeat", "الگوی چرخشی", patternDialog),
          undoBtn, saveBtn),
        h("div", { class: "palette-row" },
          quick.map((t) => h("button", { class: "chip-code " + shiftTone(t.label, t.kind), type: "button", title: t.title, onclick: () => apply(t.code) },
            h("b", {}, faDigits(t.code)), " ", faDigits(t.label))),
          h("button", { class: "chip-code", type: "button", onclick: () => apply(null) }, "پاک کردن"),
          h("span", { class: "muted small" }, "روی خانه بزنید، کد را بنویسید و Enter بزنید. با Shift چند روز پشت سر هم و با Ctrl چند خانه جدا انتخاب می‌شود. پاک کردن با Delete انجام می‌شود.")))
    : null;

  const warnBox = d.warnings.length
    ? h("details", { class: "card warnings", open: d.warnings.length <= 6 },
        h("summary", {}, icon("warning"), ` ${fa(d.warnings.length)} هشدار`, h("span", { class: "muted small" }, " (ذخیره و ارسال را نمی‌گیرد)")),
        h("ul", {}, d.warnings.map((w) => {
          const m = w.user_id ? d.members.find((x) => x.user_id === w.user_id) : null;
          return h("li", { class: w.user_id && w.day ? "clickable" : null, onclick: w.user_id && w.day ? () => pick(key(w.user_id, w.day)) : null },
            badge(SL.warning[w.code] || w.code, w.code === "understaffed" ? "amber" : "red"), " ",
            m ? h("b", {}, m.full_name + ": ") : null, faDigits(w.message));
        })))
    : null;

  const status = d.status;
  fill(main,
    h("a", { class: "back", href: "#/schedules" }, icon("chevronRight"), "همه واحدها"),
    pageHead(d.unit_name + " – " + monthTitle({ y: d.jyear, m: d.jmonth }), null, ...actions),
    h("div", { class: "subhead" }, scheduleBadge(status),
      h("span", { class: "muted" }, fa(d.members.length) + " نفر"),
      d.published_at ? h("span", { class: "muted" }, "انتشار: " + fmtDate(d.published_at)) : null,
      status === "submitted" ? h("span", { class: "muted" }, "تا تصمیم مترون قابل تغییر نیست") : null),
    status === "draft" && d.return_note ? h("div", { class: "notice" }, icon("undo"), h("div", {}, h("b", {}, "برگشت از دفتر پرستاری: "), d.return_note)) : null,
    status === "published" && editable ? h("div", { class: "notice info" }, icon("bell"), "برنامه منتشر شده است. هر تغییر علت لازم دارد و به همان افراد اعلان می‌دهد.") : null,
    palette, warnBox,
    d.members.length ? grid : emptyState("people", "نیرویی در برنامه نیست", "با «افزودن نیرو» کارکنان را اضافه کنید."),
    legend(allowed));
}

function legend(allowed) {
  const sample = [
    ["s-morning", "صبح"], ["s-evening", "عصر"], ["s-night", "شب"], ["s-evening-night", "عصر و شب"],
    ["s-long", "ترکیبی و طولانی"], ["s-oncall", "آنکال"], ["s-off", "استراحت"], ["s-leave", "مرخصی"],
  ];
  return h("div", { class: "legend" }, sample.map(([c, t]) => h("span", {}, h("i", { class: "sw " + c }), t)),
    h("span", {}, h("i", { class: "sw covered" }), "ادامه شیفت روز قبل"),
    h("span", { class: "muted" }, fa(allowed.length) + " کد برای این واحد مجاز است"));
}

const toLatin = (s) => String(s).replace(/[۰-۹]/g, (c) => "۰۱۲۳۴۵۶۷۸۹".indexOf(c)).replace(/[٠-٩]/g, (c) => "٠١٢٣٤٥٦٧٨٩".indexOf(c));

// ------------------------------------------------------------------ کشیک امروز

async function renderOnDuty(main, sub) {
  const day = sub[0] || null;
  const r = await api("/schedules/on-duty" + (day ? "?day=" + day : ""));
  const shift = (n) => {
    const t = new Date(r.day + "T12:00:00");
    t.setDate(t.getDate() + n);
    location.hash = "#/on-duty/" + t.toISOString().slice(0, 10);
  };
  const timeOf = (iso) => faDigits(iso.slice(11, 16));
  const dayOf = (iso) => {
    const diff = Math.round((Date.parse(iso.slice(0, 10)) - Date.parse(r.day)) / 864e5);
    return diff ? ` (${diff > 0 ? "+" : "−"}${fa(Math.abs(diff))} روز)` : "";
  };
  const units = r.units.map((u) => {
    const groups = { morning: [], evening: [], night: [], other: [] };
    for (const p of u.people) {
      const ps = p.periods.length ? p.periods : ["other"];
      for (const g of ps) groups[g].push(p);
    }
    const now = u.people.filter((p) => p.on_duty_now).length;
    const personRow = (p) => h("div", { class: "duty-person" + (p.on_duty_now ? " now" : "") },
      h("span", { class: "sw " + shiftTone(p.label, "work") }),
      h("div", { class: "grow" }, h("b", {}, p.person.full_name), " ", h("span", { class: "muted small" }, p.person.job_title || "")),
      h("span", { class: "muted small nowrap", title: fmtDate(p.starts_at, true) + " تا " + fmtDate(p.ends_at, true) }, faDigits(p.label) + " · " + timeOf(p.starts_at) + "–" + timeOf(p.ends_at) + dayOf(p.ends_at)),
      p.mobile ? h("a", { class: "icon-btn", href: "tel:" + p.mobile, title: faDigits(p.mobile) }, icon("phone")) : null,
      p.oncall_now ? badge("آنکال", "blue") : null);
    return h("div", { class: "card duty-card" },
      h("div", { class: "card-head" }, h("h3", {}, u.unit_name),
        r.now ? badge(fa(now) + " نفر الان سر کار", now ? "green" : "red") : null,
        u.published ? null : badge("برنامه منتشر نشده", "amber")),
      u.people.length
        ? ["morning", "evening", "night", "other"].filter((g) => groups[g].length).map((g) =>
            h("div", { class: "duty-group" }, h("div", { class: "duty-title" }, g === "other" ? "سایر (آنکال یا ساعت خاص)" : SL.period[g] + " · " + fa(groups[g].length) + " نفر"),
              groups[g].map(personRow)))
        : h("p", { class: "muted" }, "کسی در این روز شیفت ندارد"));
  });
  fill(main,
    pageHead("کشیک", "نیروهای سر کار هر واحد بر اساس برنامه‌های منتشرشده",
      h("div", { class: "month-picker" },
        h("button", { class: "icon-btn", type: "button", title: "روز قبل", onclick: () => shift(-1) }, icon("chevronRight")),
        h("b", {}, fmtDate(r.day + "T12:00:00")),
        h("button", { class: "icon-btn", type: "button", title: "روز بعد", onclick: () => shift(1) }, icon("chevronLeft"))),
      day ? iconBtn("clock", "امروز", () => (location.hash = "#/on-duty")) : null),
    units.length ? h("div", { class: "duty-grid" }, units) : emptyState("clock", "برنامه‌ای منتشر نشده", "برای این روز هیچ واحدی برنامه منتشرشده ندارد."));
}

// ------------------------------------------------------------------ کدها، تنظیمات و تعطیلات

async function renderShiftCodes(main, sub) {
  const tab = sub[0] || "codes";
  const box = h("div");
  fill(main,
    pageHead("تنظیمات برنامه شیفت", "کدهای شیفت بیمارستان، کدهای مجاز و حداقل نیروی هر واحد، و تعطیلات رسمی"),
    tabs([["codes", "کدهای شیفت", "category"], ["units", "تنظیمات واحدها", "tune"], ["holidays", "تعطیلات", "calendar"]], tab,
      (t) => (location.hash = "#/shift-codes/" + t)),
    box);
  if (tab === "units") return unitSettings(box);
  if (tab === "holidays") return holidays(box);
  return codeList(box);
}

const segText = (s) => `${s.oncall ? "آنکال " : ""}${faDigits(s.start)}${s.start_day ? " (+" + fa(s.start_day) + ")" : ""} تا ${faDigits(s.end)}${s.end_day ? " (+" + fa(s.end_day) + ")" : ""}`;

async function codeList(box) {
  const types = await shiftTypes(true);
  const groups = [...new Map(types.list.map((t) => [t.group, t.group_name]))];
  const group = select([["", "همه گروه‌ها"], ...groups], "");
  const status = select([["active", "فعال"], ["inactive", "غیرفعال و جایگزین‌شده"], ["review", "نیازمند بازبینی"], ["", "همه"]], "active");
  const q = h("input", { type: "search", placeholder: "شماره یا عنوان کد" });
  const list = h("div");
  function draw() {
    const text = toLatin(q.value.trim());
    const rows = types.list.filter((t) =>
      (!group.value || t.group === group.value)
      && (status.value === "" || (status.value === "active" && t.is_active) || (status.value === "inactive" && !t.is_active) || (status.value === "review" && t.review_note))
      && (!text || String(t.code).startsWith(text) || t.title.includes(text) || t.label.includes(text)));
    fill(list, h("div", { class: "card flush" }, table(["کد", "برچسب", "عنوان", "ساعت‌ها", "کار / آنکال", "گروه", ""],
      rows.map((t) => ({ item: t, cells: [
        h("b", { class: "mono" }, String(t.code)),
        h("span", { class: "chip-code " + shiftTone(t.label, t.kind) }, faDigits(t.label)),
        h("div", { class: "clamp1", title: t.title }, t.title),
        t.segments.length ? h("div", { class: "small nowrap" }, t.segments.map((s) => h("div", {}, segText(s)))) : h("span", { class: "muted" }, SL.kind[t.kind]),
        t.kind === "work" ? h("span", { class: "nowrap" }, hoursText(t.work_hours) + (t.oncall_hours ? " / " + hoursText(t.oncall_hours) : "")) : "—",
        t.group_name,
        [t.is_active ? null : badge(t.replaced_by ? "جایگزین: " + faDigits(t.replaced_by) : "غیرفعال", "gray"),
          t.review_note ? h("span", { title: t.review_note }, badge("بازبینی", "amber")) : null],
      ] })), (t) => codeDialog(t, types, () => codeList(box)))));
  }
  [group, status].forEach((el) => el.addEventListener("change", draw));
  q.addEventListener("input", draw);
  fill(box,
    h("div", { class: "toolbar" }, q, group, status, h("div", { class: "spacer" }),
      iconBtn("add", "کد تازه", () => codeDialog(null, types, () => codeList(box)), "primary")),
    types.list.some((t) => t.review_note)
      ? h("div", { class: "notice" }, icon("warning"), `${fa(types.list.filter((t) => t.review_note).length)} کد ابهام دارد و باید یک بار بازبینی شود (فیلتر «نیازمند بازبینی»).`)
      : null,
    list);
  draw();
}

function segmentEditor(initial) {
  const rows = h("div", { class: "field-rows" });
  const items = [];
  function add(s = { start: "07:30", start_day: 0, end: "14:30", end_day: 0, oncall: false }) {
    const start = h("input", { type: "time", value: s.start, required: true });
    const end = h("input", { type: "time", value: s.end, required: true });
    const sd = select([["0", "همان روز"], ["1", "فردا"], ["2", "پس‌فردا"]], String(s.start_day));
    const ed = select([["0", "همان روز"], ["1", "فردا"], ["2", "پس‌فردا"], ["3", "سه روز بعد"]], String(s.end_day));
    const oncall = checkbox("آنکال", s.oncall);
    const item = { get: () => ({ start: start.value, start_day: Number(sd.value), end: end.value, end_day: Number(ed.value), oncall: oncall.input.checked }) };
    const row = h("div", { class: "seg-row" }, h("span", {}, "از"), start, sd, h("span", {}, "تا"), end, ed, oncall.el,
      h("button", { class: "row-x", type: "button", onclick: () => { row.remove(); items.splice(items.indexOf(item), 1); } }, "×"));
    items.push(item);
    rows.append(row);
  }
  (initial || []).forEach(add);
  return { el: h("div", {}, rows, h("button", { class: "btn small secondary", type: "button", onclick: () => add() }, "افزودن بازه")), value: () => items.map((i) => i.get()) };
}

function codeDialog(t, types, reload) {
  const isNew = !t;
  t = t || { code: "", title: "", label: "", kind: "work", group: "general", segments: [], hours: null, productivity: false, overtime: null, weekday: null, is_active: true, replaced_by: null, review_note: null };
  const groups = [...new Map(types.list.map((x) => [x.group, x.group_name]))];
  const code = h("input", { type: "number", min: "1", max: "9999", value: t.code, required: true, disabled: !isNew });
  const title = h("input", { type: "text", maxlength: "300", value: t.title, required: true });
  const label = h("input", { type: "text", maxlength: "30", value: t.label, required: true });
  const kind = select(Object.entries(SL.kind), t.kind);
  const group = select(groups, t.group);
  const segs = segmentEditor(t.segments);
  const hours = h("input", { type: "number", min: "0", max: "96", step: "0.5", value: t.hours ?? "", placeholder: "از روی بازه‌ها" });
  const productivity = checkbox("بهره‌وری", t.productivity);
  const overtime = select([["", "نامشخص"], ["true", "با اضافه‌کار"], ["false", "بدون اضافه‌کار"]], t.overtime === null ? "" : String(t.overtime));
  const weekday = select([["", "همه روزها"], ...SL.weekdays.map((w, i) => [String(i), "فقط " + w])], t.weekday === null ? "" : String(t.weekday));
  const active = checkbox("فعال", t.is_active);
  const replaced = h("input", { type: "number", min: "1", value: t.replaced_by ?? "", placeholder: "کد جایگزین برای کد قدیمی" });
  const note = h("textarea", { rows: "2", maxlength: "1000" }, t.review_note || "");
  dialog(isNew ? "کد شیفت تازه" : "کد " + faDigits(t.code), h("div", { class: "form" },
    h("div", { class: "grid2" }, field("شماره کد", code), field("برچسب کوتاه جدول", label)),
    field("عنوان کامل", title),
    h("div", { class: "grid2" }, field("نوع", kind), field("گروه", group)),
    h("div", { class: "aud-group" }, h("div", { class: "aud-title" }, "بازه‌های ساعت"), segs.el),
    h("div", { class: "grid2" }, field("ساعت مصوب (اختیاری)", hours), field("اضافه‌کار", overtime)),
    h("div", { class: "grid2" }, field("روز هفته", weekday), field("جایگزین", replaced)),
    h("div", { class: "segmented" }, productivity.el, active.el),
    field("یادداشت بازبینی (پس از رفع ابهام پاک کنید)", note)), { wide: true, onSave: async () => {
      const body = {
        title: title.value.trim(), label: label.value.trim(), kind: kind.value, group: group.value, segments: segs.value(),
        hours: hours.value === "" ? null : Number(hours.value), productivity: productivity.input.checked,
        overtime: overtime.value === "" ? null : overtime.value === "true", weekday: weekday.value === "" ? null : Number(weekday.value),
        is_active: active.input.checked, replaced_by: replaced.value === "" ? null : Number(replaced.value),
        review_note: note.value.trim() || null,
      };
      if (isNew) await api("/admin/shift-types", { method: "POST", body: { code: Number(code.value), ...body } });
      else await api("/admin/shift-types/" + t.code, { method: "PATCH", body });
      toast("کد ذخیره شد");
      shiftTypesCache = null;
      reload();
    } });
}

async function unitSettings(box) {
  const [rows, types] = await Promise.all([api("/admin/schedule-settings"), shiftTypes()]);
  const groups = [...new Map(types.list.map((t) => [t.group, t.group_name]))];
  const gname = new Map(groups);
  fill(box, h("div", { class: "card flush" }, table(["واحد", "گروه کدهای مجاز", "حداقل نیرو (صبح / عصر / شب)", "ساعت موظف / سقف", ""],
    rows.map((s) => ({ item: s, cells: [
      h("b", {}, s.unit_name),
      h("div", { class: "chips" }, s.shift_groups.map((g) => h("span", { class: "chip" }, gname.get(g) || g))),
      ["morning", "evening", "night"].map((p) => fa(s.min_staff[p] || 0)).join(" / "),
      (s.required_monthly_hours === null ? "—" : fa(s.required_monthly_hours)) + " / " + (s.max_monthly_hours === null ? "—" : fa(s.max_monthly_hours)),
      s.is_default ? badge("پیش‌فرض", "gray") : null,
    ] })), (s) => settingsDialog(s, groups, () => unitSettings(box)))));
}

function settingsDialog(s, groups, reload) {
  const checks = groups.map(([g, name]) => ({ g, c: checkbox(name, s.shift_groups.includes(g)) }));
  const num = (v, min, max) => h("input", { type: "number", min: String(min), max: String(max), value: v ?? "" });
  const ms = Object.fromEntries(["morning", "evening", "night"].map((p) => [p, num(s.min_staff[p] || 0, 0, 200)]));
  const consecutive = num(s.max_consecutive, 1, 31);
  const rest = num(s.min_rest_hours, 0, 24);
  const required = num(s.required_monthly_hours, 0, 744);
  const max = num(s.max_monthly_hours, 0, 744);
  dialog("تنظیمات برنامه " + s.unit_name, h("div", { class: "form" },
    h("div", { class: "aud-group" }, h("div", { class: "aud-title" }, "گروه کدهای مجاز در این واحد"), h("div", { class: "checklist" }, checks.map((x) => x.c.el))),
    h("div", { class: "grid3" }, field("حداقل نیروی صبح", ms.morning), field("حداقل نیروی عصر", ms.evening), field("حداقل نیروی شب", ms.night)),
    h("div", { class: "grid2" }, field("حداکثر روز کاری پشت سر هم", consecutive), field("حداقل استراحت میان دو شیفت (ساعت)", rest)),
    h("div", { class: "grid2" }, field("ساعت موظف ماهانه (برای اضافه‌کار)", required), field("سقف ساعت ماهانه (هشدار)", max))), { onSave: async () => {
      const opt = (el) => (el.value === "" ? null : Number(el.value));
      await api("/admin/schedule-settings/" + s.unit_id, { method: "PUT", body: {
        shift_groups: checks.filter((x) => x.c.input.checked).map((x) => x.g),
        min_staff: Object.fromEntries(Object.entries(ms).map(([p, el]) => [p, Number(el.value || 0)])),
        max_consecutive: Number(consecutive.value || 6), min_rest_hours: Number(rest.value || 0),
        required_monthly_hours: opt(required), max_monthly_hours: opt(max),
      } });
      toast("تنظیمات ذخیره شد");
      reload();
    } });
}

async function holidays(box) {
  const year = currentJalali().y;
  const ysel = select([year - 1, year, year + 1].map((y) => [String(y), faDigits(y)]), String(year));
  const list = h("div");
  async function load() {
    const rows = await api("/holidays?jyear=" + ysel.value);
    fill(list, h("div", { class: "card flush" }, table(["تاریخ", "مناسبت", ""], rows.map((r) => ({ item: r, cells: [
      faDigits(r.jalali), r.title,
      h("button", { class: "btn small danger", type: "button", onclick: async () => {
        await api("/admin/holidays/" + r.day, { method: "DELETE" }).catch(showError);
        load();
      } }, "حذف"),
    ] })))));
  }
  ysel.addEventListener("change", () => load().catch(showError));
  const add = () => {
    const day = h("input", { type: "date", required: true });
    const preview = h("span", { class: "muted small date-preview" });
    day.addEventListener("input", () => (preview.textContent = day.value ? fmtDate(day.value + "T12:00:00") : ""));
    const title = h("input", { type: "text", maxlength: "200", required: true, placeholder: "مثلاً عید فطر" });
    dialog("تعطیل رسمی", h("div", { class: "form" }, field("تاریخ (میلادی)", h("div", { class: "date-field" }, day, preview)), field("مناسبت", title)),
      { onSave: async () => {
        await api("/admin/holidays", { method: "POST", body: { day: day.value, title: title.value.trim() } });
        load();
      } });
  };
  fill(box,
    h("div", { class: "toolbar" }, ysel, h("span", { class: "muted" }, "جمعه‌ها خودکار تعطیل حساب می‌شوند."), h("div", { class: "spacer" }), iconBtn("add", "افزودن تعطیل", add, "primary")),
    list);
  await load();
}
