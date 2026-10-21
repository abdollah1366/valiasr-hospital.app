/*
 * پزشکان و نوبت‌دهی در پنل: گروه‌های تخصصی، معرفی پزشکان (مدیر)، وقت‌ها و نوبت‌های هر پزشک (منشی)
 * و فهرست بیماران امروز برای خود پزشک.
 * پس از translations.js و پیش از app.js بارگذاری می‌شود (از icon، badge، dialog، table و ... استفاده می‌کند).
 */
"use strict";

Object.assign(ICONS, {
  doctor: "M19 8h-2v3h-3v2h3v3h2v-3h3v-2h-3V8zM4 8c0 2.21 1.79 4 4 4s4-1.79 4-4-1.79-4-4-4-4 1.79-4 4zm-2 8v2h12v-2c0-2.66-5.33-4-6-4s-6 1.34-6 4z",
  event: "M17 12h-5v5h5v-5zM16 1v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2h-1V1h-2zm3 18H5V8h14v11z",
  patients: "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
  block: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 3.58-8 8-8 1.85 0 3.55.63 4.9 1.69L5.69 16.9C4.63 15.55 4 13.85 4 12zm8 8c-1.85 0-3.55-.63-4.9-1.69L18.31 7.1C19.37 8.45 20 10.15 20 12c0 4.42-3.58 8-8 8z",
});

const AL = {
  status: { booked: "رزرو شده", attended: "مراجعه کرد", no_show: "نیامد", cancelled: "لغو شده" },
  tone: { booked: "blue", attended: "green", no_show: "amber", cancelled: "red" },
  source: { app: "اپ", secretary: "منشی" },
  icons: {
    stethoscope: "عمومی", heart: "قلب", bone: "استخوان", brain: "مغز", psychology: "روان", baby: "نوزاد",
    female: "زنان", child: "کودک", eye: "چشم", ear: "گوش", tooth: "دندان", lungs: "ریه", stomach: "گوارش",
    kidney: "کلیه", skin: "پوست", scalpel: "جراحی", blood: "خون", ribbon: "سرطان", radiology: "تصویربرداری", lab: "آزمایشگاه",
  },
};

const TZ = "Asia/Tehran";
const apptBadge = (s) => badge(AL.status[s] || s, AL.tone[s]);
const timeOf = (iso) => new Intl.DateTimeFormat("fa-IR", { timeZone: TZ, hour: "2-digit", minute: "2-digit" }).format(new Date(iso));
const dayTitle = (d) => new Intl.DateTimeFormat("fa-IR-u-ca-persian", { timeZone: TZ, weekday: "long", day: "numeric", month: "long" }).format(d);

/** تاریخ میلادی yyyy-mm-dd روز محلی تهران (برای پارامتر start سرور). */
function isoDay(d) {
  const p = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(d);
  const v = (t) => p.find((x) => x.type === t).value;
  return v("year") + "-" + v("month") + "-" + v("day");
}
const addDays = (d, n) => new Date(d.getTime() + n * 86400000);

/** فهرست انتخاب روز با نام شمسی، از امروز تا days روز بعد. */
function daySelect(days = 90, value) {
  const today = new Date();
  const opts = Array.from({ length: days }, (_, i) => {
    const d = addDays(today, i);
    return [isoDay(d), dayTitle(d)];
  });
  return select(opts, value || opts[0][0]);
}

let doctorCache = null;
async function doctorsList(force = false) {
  if (!doctorCache || force) doctorCache = await api("/admin/doctors");
  return doctorCache;
}

// ------------------------------------------------------------------ گروه‌های تخصصی

async function renderSpecialties(main) {
  const list = h("div");
  async function draw() {
    const items = await api("/admin/specialties");
    list.replaceChildren(table(
      ["نام", "آیکون", "تعداد پزشک", "وضعیت", ""],
      items.map((s) => ({
        item: s,
        cells: [s.name, AL.icons[s.icon] || s.icon, fa(s.doctor_count), s.is_active ? badge("فعال", "green") : badge("غیرفعال", "gray"),
          h("button", { class: "btn small secondary", onclick: (e) => { e.stopPropagation(); specialtyDialog(s, draw); } }, "ویرایش")],
      })),
      (s) => specialtyDialog(s, draw),
    ));
  }
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "گروه‌های تخصصی"),
      h("button", { class: "btn", onclick: () => specialtyDialog(null, draw) }, "گروه جدید")),
    h("p", { class: "muted" }, "مراجعان پزشکان را با این گروه‌ها پیدا می‌کنند. ترتیب کمتر بالاتر نشان داده می‌شود."),
    list,
  );
  await draw();
}

function specialtyDialog(s, reload) {
  const name = h("input", { type: "text", required: true, value: s ? s.name : "" });
  const iconSel = select(Object.entries(AL.icons), s ? s.icon : "stethoscope");
  const order = h("input", { type: "number", value: s ? s.sort_order : 0 });
  const desc = h("textarea", { rows: "5" }, s && s.description ? s.description : "");
  const active = checkbox("فعال (در اپ دیده شود)", s ? s.is_active : true);
  const del = s ? h("button", { class: "btn danger", type: "button", onclick: async (ev) => {
    if (!confirm("گروه «" + s.name + "» حذف شود؟")) return;
    try {
      await api("/admin/specialties/" + s.id, { method: "DELETE" });
      ev.target.closest("dialog").close();
      toast("حذف شد");
      await reload();
    } catch (e) { showError(e); }
  } }, "حذف") : null;
  dialog(s ? "ویرایش گروه تخصصی" : "گروه تخصصی جدید", h("div", { class: "form" },
    h("div", { class: "row" }, field("نام", name), field("آیکون در اپ", iconSel)),
    field("ترتیب نمایش", order),
    field("معرفی گروه (در صفحه گروه برای مراجع)", desc),
    active.el), {
    extraButtons: [del],
    onSave: async () => {
      const body = { name: name.value.trim(), icon: iconSel.value, sort_order: Number(order.value) || 0,
        description: desc.value.trim() || null, is_active: active.input.checked };
      if (s) await api("/admin/specialties/" + s.id, { method: "PUT", body });
      else await api("/admin/specialties", { method: "POST", body });
      toast("ذخیره شد");
      await reload();
    },
  });
}

// ------------------------------------------------------------------ پزشکان

function doctorPhoto(d, size = 40) {
  if (!d.has_photo) return h("div", { class: "doc-avatar", style: null }, (d.full_name.replace(/^دکتر\s*/, "").trim().charAt(0) || "د"));
  return h("img", { class: "doc-avatar", src: API + "/public/doctors/" + d.id + "/photo?v=" + Date.now(), alt: "", width: String(size), height: String(size) });
}

async function renderDoctors(main) {
  const [specs] = await Promise.all([api("/admin/specialties"), catalog()]);
  const q = h("input", { type: "search", placeholder: "جستجوی نام پزشک یا تخصص" });
  const list = h("div");
  async function draw(force = true) {
    const all = await doctorsList(force);
    const term = q.value.trim();
    const items = term ? all.filter((d) => (d.full_name + " " + d.specialty_name + " " + (d.title || "")).includes(term)) : all;
    list.replaceChildren(table(
      ["", "نام", "تخصص", "منشی‌ها", "حساب پزشک", "نوبت اینترنتی", ""],
      items.map((d) => ({
        item: d,
        cells: [doctorPhoto(d), h("div", {}, h("div", {}, d.full_name, d.is_featured ? badge("برگزیده", "amber") : null), h("div", { class: "muted" }, d.title || "")),
          d.specialty_name, d.secretaries.map((s) => s.full_name).join("، ") || "—",
          d.user ? d.user.full_name + " (" + faDigits(d.user.personnel_code) + ")" : "—",
          !d.is_active ? badge("غیرفعال", "gray") : d.booking_enabled ? badge("باز", "green") : badge("بسته", "red"),
          h("button", { class: "btn small secondary", onclick: (e) => { e.stopPropagation(); doctorDialog(d, specs, draw); } }, "ویرایش")],
      })),
      (d) => doctorDialog(d, specs, draw),
    ));
  }
  q.addEventListener("input", () => draw(false).catch(showError));
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "معرفی پزشکان"), q,
      h("button", { class: "btn", onclick: () => doctorDialog(null, specs, draw) }, "پزشک جدید")),
    list,
  );
  await draw();
}

function doctorDialog(d, specs, reload) {
  const name = h("input", { type: "text", required: true, value: d ? d.full_name : "دکتر " });
  const spec = select(specs.map((s) => [s.id, s.name]), d ? d.specialty_id : specs[0] && specs[0].id);
  const title = h("input", { type: "text", value: d && d.title ? d.title : "", placeholder: "مثلاً فوق تخصص قلب و عروق" });
  const code = h("input", { type: "text", dir: "ltr", value: d && d.medical_code ? d.medical_code : "" });
  const clinics = (state.units || []).filter((u) => u.type === "clinic");
  const clinic = select([["", "—"], ...clinics.map((u) => [u.id, u.name])], d && d.clinic_unit_id ? d.clinic_unit_id : "");
  const bio = h("textarea", { rows: "6" }, d && d.bio ? d.bio : "");
  const minutes = h("input", { type: "number", min: "5", max: "240", value: d ? d.visit_minutes : 15 });
  const order = h("input", { type: "number", value: d ? d.sort_order : 0 });
  const user = h("input", { type: "text", dir: "ltr", value: d && d.user ? d.user.personnel_code : "", placeholder: "کد پرسنلی" });
  const secretaries = h("input", { type: "text", dir: "ltr", value: d ? d.secretaries.map((s) => s.personnel_code).join(", ") : "",
    placeholder: "مثلاً 1013, 1020" });
  const featured = checkbox("در «پزشکان برگزیده» صفحه اول اپ", d ? d.is_featured : false);
  const booking = checkbox("نوبت‌گیری اینترنتی باز است", d ? d.booking_enabled : true);
  const active = checkbox("فعال (در اپ دیده شود)", d ? d.is_active : true);
  const photoState = h("span", { class: "tag" }, d && d.has_photo ? "عکس ثبت شده" : "بدون عکس");
  const photo = h("input", { type: "file", accept: "image/jpeg,image/png,image/webp", disabled: !d, onchange: async (ev) => {
    const file = ev.target.files[0];
    if (!file) return;
    try {
      await uploadFile(file, () => {}, "/admin/doctors/" + d.id + "/photo");
      photoState.textContent = "عکس ثبت شده";
      toast("عکس ذخیره شد");
      await reload();
    } catch (e) { showError(e); }
  } });
  const del = d ? h("button", { class: "btn danger", type: "button", onclick: async (ev) => {
    if (!confirm("پرونده «" + d.full_name + "» حذف شود؟")) return;
    try {
      await api("/admin/doctors/" + d.id, { method: "DELETE" });
      ev.target.closest("dialog").close();
      toast("حذف شد");
      await reload();
    } catch (e) { showError(e); }
  } }, "حذف") : null;
  dialog(d ? "ویرایش پزشک" : "پزشک جدید", h("div", { class: "form" },
    h("div", { class: "row" }, field("نام و نام خانوادگی", name), field("گروه تخصصی", spec)),
    h("div", { class: "row" }, field("عنوان تخصص", title), field("شماره نظام پزشکی", code)),
    h("div", { class: "row" }, field("درمانگاه", clinic), field("مدت هر ویزیت (دقیقه)", minutes)),
    field("معرفی و سوابق پزشک", bio),
    h("div", { class: "row" }, field("حساب کارکنان خود پزشک (برای دیدن فهرست بیماران)", user),
      field("منشی‌ها (کد پرسنلی، با ویرگول)", secretaries)),
    h("div", { class: "row" }, field("ترتیب نمایش", order), field("عکس پزشک (jpg، png یا webp)", h("div", { class: "row" }, photoState, photo))),
    featured.el, booking.el, active.el,
    d ? null : h("p", { class: "muted" }, "عکس را پس از ذخیره پزشک اضافه کنید.")), {
    wide: true,
    extraButtons: [del],
    onSave: async () => {
      const body = {
        full_name: name.value.trim(), specialty_id: spec.value, title: title.value.trim() || null,
        medical_code: code.value.trim() || null, clinic_unit_id: clinic.value || null, bio: bio.value.trim() || null,
        visit_minutes: Number(minutes.value) || 15, sort_order: Number(order.value) || 0,
        user_personnel_code: user.value.trim() || null,
        secretary_codes: secretaries.value.split(/[,،\s]+/).map((s) => s.trim()).filter(Boolean),
        is_featured: featured.input.checked, booking_enabled: booking.input.checked, is_active: active.input.checked,
      };
      if (d) await api("/admin/doctors/" + d.id, { method: "PUT", body });
      else await api("/admin/doctors", { method: "POST", body });
      toast("ذخیره شد");
      await reload();
    },
  });
}

// ------------------------------------------------------------------ نوبت‌دهی (منشی)

async function renderBooking(main, sub) {
  const doctors = await doctorsList(true);
  if (!doctors.length) {
    main.replaceChildren(h("h1", {}, "نوبت‌دهی"), h("div", { class: "card muted" }, "هنوز پزشکی به شما سپرده نشده است."));
    return;
  }
  let doctorId = sub[0] && doctors.some((d) => d.id === sub[0]) ? sub[0] : doctors[0].id;
  let weekStart = new Date();
  const docSel = select(doctors.map((d) => [d.id, d.full_name + " · " + d.specialty_name]), doctorId);
  const weekLabel = h("span", { class: "grow center" });
  const board = h("div", { class: "week-board" });
  const search = h("input", { type: "search", placeholder: "جستجو: کد پیگیری، موبایل یا نام بیمار" });
  const results = h("div");
  const doctor = () => doctors.find((d) => d.id === doctorId);

  async function load() {
    const start = isoDay(weekStart);
    const slots = await api("/manage/doctors/" + doctorId + "/slots?start=" + start + "&days=7");
    weekLabel.textContent = dayTitle(weekStart) + " تا " + dayTitle(addDays(weekStart, 6));
    const byDay = new Map();
    for (let i = 0; i < 7; i++) byDay.set(isoDay(addDays(weekStart, i)), []);
    for (const s of slots) {
      const k = isoDay(new Date(s.starts_at));
      if (byDay.has(k)) byDay.get(k).push(s);
    }
    const freeSlots = slots.filter((s) => !s.is_blocked && new Date(s.starts_at) > new Date()
      && s.appointments.filter((a) => a.status !== "cancelled" && a.status !== "no_show").length < s.capacity);
    board.replaceChildren(...[...byDay.entries()].map(([day, list]) => {
      const active = list.reduce((n, s) => n + s.appointments.filter((a) => a.status !== "cancelled").length, 0);
      return h("div", { class: "day-col" },
        h("div", { class: "day-head" }, h("div", {}, dayTitle(new Date(day + "T12:00:00+03:30"))),
          h("div", { class: "muted" }, list.length ? fa(active) + " نوبت از " + fa(list.reduce((n, s) => n + s.capacity, 0)) : "وقتی تعریف نشده"),
          list.length ? h("button", { class: "btn small danger", type: "button", title: "غیبت پزشک: لغو همه نوبت‌های این روز",
            onclick: () => cancelDay(day) }, "لغو روز") : null),
        list.map((s) => slotCard(s, freeSlots)));
    }));
  }

  function slotCard(s, freeSlots) {
    const live = s.appointments.filter((a) => a.status !== "cancelled");
    const full = live.length >= s.capacity;
    const past = new Date(s.starts_at) <= new Date();
    return h("div", { class: "slot" + (s.is_blocked ? " blocked" : full ? " full" : "") + (past ? " past" : "") },
      h("button", { class: "slot-head", type: "button", onclick: () => slotDialog(s) },
        h("b", {}, timeOf(s.starts_at)),
        s.is_blocked ? badge("بسته", "gray") : s.capacity > 1 ? h("span", { class: "muted" }, fa(live.length) + "/" + fa(s.capacity)) : null,
        !s.is_blocked && !full && !past ? h("span", { class: "slot-free" }, "خالی") : null),
      s.appointments.map((a) => h("button", { class: "appt " + (a.status || ""), type: "button", onclick: () => apptDialog(a, freeSlots) },
        h("span", { class: "grow" }, a.patient_name), apptBadge(a.status))));
  }

  function slotDialog(s) {
    const live = s.appointments.filter((a) => a.status !== "cancelled");
    const canBook = !s.is_blocked && live.length < s.capacity && new Date(s.starts_at) > new Date();
    const pname = h("input", { type: "text" });
    const pmobile = h("input", { type: "text", inputmode: "tel", dir: "ltr", placeholder: "09xxxxxxxxx" });
    const note = h("input", { type: "text" });
    const cap = h("input", { type: "number", min: "1", max: "50", value: s.capacity });
    const blockBtn = h("button", { class: "btn secondary", type: "button", onclick: async (ev) => {
      try {
        await api("/manage/slots/" + s.id, { method: "PATCH", body: { is_blocked: !s.is_blocked } });
        ev.target.closest("dialog").close();
        await load();
      } catch (e) { showError(e); }
    } }, icon("block"), h("span", {}, s.is_blocked ? "باز کردن وقت" : "بستن وقت"));
    const capBtn = h("button", { class: "btn secondary", type: "button", onclick: async (ev) => {
      try {
        await api("/manage/slots/" + s.id, { method: "PATCH", body: { capacity: Number(cap.value) } });
        ev.target.closest("dialog").close();
        await load();
      } catch (e) { showError(e); }
    } }, "ثبت ظرفیت");
    const delBtn = s.appointments.length ? null : h("button", { class: "btn danger", type: "button", onclick: async (ev) => {
      try {
        await api("/manage/slots/" + s.id, { method: "DELETE" });
        ev.target.closest("dialog").close();
        await load();
      } catch (e) { showError(e); }
    } }, "حذف وقت");
    dialog("وقت " + timeOf(s.starts_at) + " · " + dayTitle(new Date(s.starts_at)), h("div", { class: "form" },
      canBook ? h("div", { class: "card soft" }, h("b", {}, "نوبت دادن به مراجع تلفنی یا حضوری"),
        h("div", { class: "row" }, field("نام و نام خانوادگی بیمار", pname), field("موبایل", pmobile)),
        field("یادداشت (اختیاری)", note)) : h("p", { class: "muted" }, s.is_blocked ? "این وقت بسته است." : "این وقت پر شده یا گذشته است."),
      h("div", { class: "row" }, field("ظرفیت این وقت", cap), h("div", { class: "toolbar" }, capBtn, blockBtn, delBtn)),
    ), {
      saveLabel: "ثبت نوبت",
      onSave: canBook ? async () => {
        await api("/manage/appointments", { method: "POST",
          body: { slot_id: s.id, patient_name: pname.value.trim(), patient_mobile: pmobile.value.trim(), note: note.value.trim() || null } });
        toast("نوبت ثبت شد");
        await load();
      } : undefined,
    });
  }

  function apptDialog(a, freeSlots) {
    const targets = freeSlots.filter((s) => s.id !== a.slot_id);
    const move = select([["", "— انتخاب وقت خالی همین هفته —"], ...targets.map((s) => [s.id, dayTitle(new Date(s.starts_at)) + " ساعت " + timeOf(s.starts_at)])], "");
    const act = (path, body, msg) => async (ev) => {
      try {
        await api("/manage/appointments/" + a.id + path, { method: "POST", body });
        ev.target.closest("dialog").close();
        toast(msg);
        await load();
      } catch (e) { showError(e); }
    };
    const booked = a.status === "booked";
    dialog("نوبت " + a.patient_name, h("div", { class: "form" },
      h("div", { class: "row" },
        h("div", {}, h("div", { class: "muted" }, "زمان"), dayTitle(new Date(a.starts_at)) + " ساعت " + timeOf(a.starts_at)),
        h("div", {}, h("div", { class: "muted" }, "وضعیت"), apptBadge(a.status))),
      h("div", { class: "row" },
        h("div", {}, h("div", { class: "muted" }, "موبایل"), h("a", { href: "tel:" + a.patient_mobile, dir: "ltr" }, a.patient_mobile)),
        h("div", {}, h("div", { class: "muted" }, "کد پیگیری"), h("span", { class: "mono" }, a.tracking_code))),
      a.note ? h("div", {}, h("div", { class: "muted" }, "یادداشت"), a.note) : null,
      h("div", { class: "muted" }, "ثبت از: " + (AL.source[a.source] || a.source) + " · " + fmtDate(a.created_at, true)),
      booked ? h("div", { class: "row" }, field("جابجایی به", move),
        h("div", { class: "toolbar" }, h("button", { class: "btn", type: "button", onclick: async (ev) => {
          if (!move.value) return toast("یک وقت خالی انتخاب کنید", true);
          await act("/move", { slot_id: move.value }, "نوبت جابجا شد")(ev);
        } }, icon("repeat"), h("span", {}, "جابجا کن")))) : null,
    ), {
      extraButtons: [
        a.status !== "cancelled" ? h("button", { class: "btn secondary", type: "button", onclick: act("/status", { status: "attended" }, "حضور ثبت شد") }, "مراجعه کرد") : null,
        a.status !== "cancelled" ? h("button", { class: "btn secondary", type: "button", onclick: act("/status", { status: "no_show" }, "ثبت شد") }, "نیامد") : null,
        booked ? h("button", { class: "btn danger", type: "button", onclick: (ev) => confirm("نوبت لغو شود؟") && act("/cancel", undefined, "نوبت لغو شد")(ev) }, "لغو نوبت") : null,
      ],
    });
  }

  async function cancelDay(day) {
    if (!confirm("همه وقت‌های «" + dayTitle(new Date(day + "T12:00:00+03:30")) + "» بسته و نوبت‌ها لغو شود؟ مراجعان لغو را در اپ می‌بینند.")) return;
    try {
      const r = await api("/manage/doctors/" + doctorId + "/cancel-day", { method: "POST", body: { date: day } });
      toast(fa(r.cancelled) + " نوبت لغو شد");
      await load();
    } catch (e) { showError(e); }
  }

  function sessionDialog() {
    const day = daySelect(120);
    const startT = h("input", { type: "time", value: "09:00", required: true });
    const endT = h("input", { type: "time", value: "12:00", required: true });
    const minutes = h("input", { type: "number", min: "5", max: "240", value: doctor().visit_minutes });
    const cap = h("input", { type: "number", min: "1", max: "50", value: 1 });
    const weeks = h("input", { type: "number", min: "1", max: "26", value: 1 });
    dialog("تعریف نوبت کاری " + doctor().full_name, h("div", { class: "form" },
      field("روز", day),
      h("div", { class: "row" }, field("از ساعت", startT), field("تا ساعت", endT)),
      h("div", { class: "row" }, field("مدت هر وقت (دقیقه)", minutes), field("ظرفیت هر وقت", cap)),
      field("تکرار در همین روز هفته‌های بعد (تعداد هفته)", weeks),
      h("p", { class: "muted" }, "وقت‌هایی که از قبل تعریف شده‌اند دوباره ساخته نمی‌شوند.")), {
      saveLabel: "ساخت وقت‌ها",
      onSave: async () => {
        const r = await api("/manage/doctors/" + doctorId + "/sessions", { method: "POST", body: {
          date: day.value, start: startT.value, end: endT.value, minutes: Number(minutes.value) || null,
          capacity: Number(cap.value) || 1, repeat_weeks: Number(weeks.value) || 1 } });
        toast(fa(r.created) + " وقت ساخته شد" + (r.skipped ? " (" + fa(r.skipped) + " وقت از قبل بود)" : ""));
        await load();
      },
    });
  }

  async function doSearch(ev) {
    ev.preventDefault();
    const term = search.value.trim();
    if (term.length < 3) return results.replaceChildren();
    try {
      const rows = await api("/manage/appointments?q=" + encodeURIComponent(term));
      results.replaceChildren(h("div", { class: "card" }, table(["زمان", "پزشک", "بیمار", "موبایل", "کد", "وضعیت"],
        rows.map((a) => ({ item: a, cells: [dayTitle(new Date(a.starts_at)) + " " + timeOf(a.starts_at), a.doctor_name, a.patient_name,
          h("span", { dir: "ltr" }, a.patient_mobile), h("span", { class: "mono" }, a.tracking_code), apptBadge(a.status)] })),
        (a) => apptDialog(a, []))));
    } catch (e) { showError(e); }
  }

  docSel.addEventListener("change", () => { doctorId = docSel.value; history.replaceState(null, "", "#/booking/" + doctorId); load().catch(showError); });
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "نوبت‌دهی"), docSel,
      h("button", { class: "btn", onclick: sessionDialog }, icon("event"), h("span", {}, "تعریف نوبت کاری"))),
    h("form", { class: "toolbar", onsubmit: doSearch }, search, h("button", { class: "btn secondary", type: "submit" }, "جستجو")),
    results,
    h("div", { class: "toolbar" },
      h("button", { class: "btn small secondary", onclick: () => { weekStart = addDays(weekStart, -7); load().catch(showError); } }, icon("chevronRight"), "هفته قبل"),
      weekLabel,
      h("button", { class: "btn small secondary", onclick: () => { weekStart = new Date(); load().catch(showError); } }, "امروز"),
      h("button", { class: "btn small secondary", onclick: () => { weekStart = addDays(weekStart, 7); load().catch(showError); } }, "هفته بعد", icon("chevronLeft"))),
    board,
  );
  await load();
}

// ------------------------------------------------------------------ بیماران من (پزشک)

async function renderMyPatients(main) {
  let day = new Date();
  const label = h("span", { class: "grow center" });
  const list = h("div");
  const kpis = h("div", { class: "kpis" });
  async function load() {
    label.textContent = dayTitle(day);
    const rows = await api("/doctor/me/appointments?start=" + isoDay(day));
    const live = rows.filter((a) => a.status !== "cancelled");
    kpis.replaceChildren(
      kpi("patients", "نوبت‌های روز", fa(live.length)),
      kpi("check", "مراجعه کرده", fa(live.filter((a) => a.status === "attended").length), null, "green"),
      kpi("clock", "در انتظار", fa(live.filter((a) => a.status === "booked").length), null, "blue"));
    list.replaceChildren(table(["ساعت", "بیمار", "موبایل", "یادداشت", "وضعیت", ""],
      rows.map((a) => ({ item: a, cells: [timeOf(a.starts_at), a.patient_name, h("span", { dir: "ltr" }, a.patient_mobile), a.note || "—", apptBadge(a.status),
        a.status === "cancelled" ? "" : h("div", { class: "toolbar" },
          h("button", { class: "btn small secondary", onclick: () => mark(a, "attended") }, "مراجعه کرد"),
          h("button", { class: "btn small secondary", onclick: () => mark(a, "no_show") }, "نیامد"))] }))));
  }
  async function mark(a, status) {
    try {
      await api("/doctor/me/appointments/" + a.id + "/status", { method: "POST", body: { status } });
      await load();
    } catch (e) { showError(e); }
  }
  main.replaceChildren(
    h("h1", {}, "بیماران من"),
    h("div", { class: "toolbar" },
      h("button", { class: "btn small secondary", onclick: () => { day = addDays(day, -1); load().catch(showError); } }, icon("chevronRight"), "روز قبل"),
      label,
      h("button", { class: "btn small secondary", onclick: () => { day = new Date(); load().catch(showError); } }, "امروز"),
      h("button", { class: "btn small secondary", onclick: () => { day = addDays(day, 1); load().catch(showError); } }, "روز بعد", icon("chevronLeft"))),
    kpis, list);
  await load();
}
