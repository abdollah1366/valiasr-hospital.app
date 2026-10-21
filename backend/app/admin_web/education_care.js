/* آموزش به بیمار، بخش تعاملی: ارسال آموزش برای یک مراجع و پیگیری دیده شدن، و تیکت‌های مراجعان.
 * پس از education.js و پیش از app.js بارگذاری می‌شود. */

const EDC = {
  statusTone: { open: "amber", closed: "gray" },
  statusText: { open: "باز", closed: "بسته" },
};

function educationStars(avg, count) {
  return count ? "★ " + fa(avg) + " (" + fa(count) + ")" : "—";
}

async function renderEducationSend(main) {
  const published = (await api("/admin/education")).filter((i) => i.is_published);
  const patientQ = h("input", { type: "search", dir: "ltr", placeholder: "بخشی از شماره موبایل بیمار یا همراه (حداقل ۳ رقم)" });
  const patients = h("div");
  const panel = h("div");
  const unseen = h("div");
  let current = null;

  async function loadUnseen() {
    const rows = await api("/admin/education/assignments?only_unseen=true");
    unseen.replaceChildren(table(["مراجع", "آموزش", "ارسال", ""], rows.map((r) => ({
      item: r,
      cells: [(r.patient_name || "—") + " · " + r.patient_mobile, r.item_title, fmtDate(r.assigned_at, true),
        h("button", { class: "btn small secondary", onclick: () => pickPatient({ id: r.patient_id, mobile: r.patient_mobile, full_name: r.patient_name }) }, "باز کردن")],
    }))));
  }

  async function searchPatients() {
    if (patientQ.value.trim().length < 3) return patients.replaceChildren();
    const rows = await api("/admin/education/patients?q=" + encodeURIComponent(patientQ.value.trim()));
    patients.replaceChildren(table(["موبایل", "نام", "ارسال‌شده", "دیده‌نشده", ""], rows.map((p) => ({
      item: p,
      cells: [h("span", { dir: "ltr" }, p.mobile), p.full_name || "—", fa(p.assigned_count), fa(p.unseen_count),
        h("button", { class: "btn small", onclick: () => pickPatient(p) }, "انتخاب")],
    }))));
  }

  async function pickPatient(p) {
    current = p;
    const picked = new Set();
    const filter = h("input", { type: "search", placeholder: "جست‌وجو در عنوان آموزش‌ها" });
    const note = h("input", { type: "text", maxlength: "300", placeholder: "یادداشت برای بیمار (اختیاری)، مثلاً پیش از عمل بخوانید" });
    const list = h("div", { class: "check-list" });
    const count = h("span", { class: "muted" }, "۰ انتخاب");
    const history = h("div");

    function drawList() {
      const q = filter.value.trim();
      list.replaceChildren(...published.filter((i) => !q || i.title.includes(q) || (i.condition || "").includes(q)).map((i) => {
        const c = checkbox(i.title + (i.condition ? " · " + i.condition : ""), picked.has(i.id));
        c.input.onchange = () => {
          if (c.input.checked) picked.add(i.id); else picked.delete(i.id);
          count.textContent = fa(picked.size) + " انتخاب";
        };
        return c.el;
      }));
    }
    async function loadHistory() {
      const rows = await api("/admin/education/assignments?patient_id=" + p.id);
      history.replaceChildren(table(["آموزش", "ارسال", "دیده شد", "دفعات", ""], rows.map((r) => ({
        item: r,
        cells: [r.item_title, fmtDate(r.assigned_at, true),
          r.first_seen_at ? badge("دیده شد · " + fmtDate(r.first_seen_at, true), "green") : badge("هنوز ندیده", "amber"),
          fa(r.view_count),
          h("button", { class: "btn small secondary", onclick: async () => {
            if (!confirm("این ارسال لغو شود؟")) return;
            try { await api("/admin/education/assignments/" + r.id, { method: "DELETE" }); await loadHistory(); await loadUnseen(); } catch (e) { showError(e); }
          } }, "لغو")],
      }))));
    }
    filter.oninput = drawList;
    drawList();
    panel.replaceChildren(
      h("h2", {}, "ارسال آموزش برای " + (p.full_name || "") + " ", h("span", { dir: "ltr" }, p.mobile)),
      filter, list, note,
      h("div", { class: "toolbar" }, count,
        h("button", { class: "btn", onclick: async () => {
          if (!picked.size) return toast("حداقل یک آموزش را انتخاب کنید", true);
          try {
            const res = await api("/admin/education/assignments", { method: "POST", body: { patient_id: p.id, item_ids: [...picked], note: note.value.trim() || null } });
            toast(fa(res.added) + " آموزش فرستاده شد" + (res.already ? " (" + fa(res.already) + " مورد قبلاً فرستاده بود)" : ""));
            picked.clear(); count.textContent = "۰ انتخاب"; drawList();
            await loadHistory(); await loadUnseen(); await searchPatients();
          } catch (e) { showError(e); }
        } }, "فرستادن به مراجع")),
      h("h2", {}, "آموزش‌های فرستاده‌شده و دیده شدن"), history);
    await loadHistory();
  }

  patientQ.oninput = () => searchPatients().catch(showError);
  main.replaceChildren(h("h1", {}, "ارسال آموزش به بیمار"),
    h("p", { class: "muted" }, "بیمار یا همراه باید با موبایل خودش در اپ ثبت‌نام کرده باشد؛ آموزش در بخش «آموزش‌های من» اپ او نشان داده می‌شود."),
    patientQ, patients, panel,
    h("h2", {}, "ارسال‌های دیده‌نشده"), unseen);
  await loadUnseen();
}

function ticketMedia(att) {
  if (!att) return null;
  if (att.content_type.startsWith("image/")) return h("a", { href: att.url, target: "_blank", rel: "noopener" }, h("img", { src: att.url, alt: att.original_name, style: "max-width:260px;border-radius:8px" }));
  if (att.content_type.startsWith("audio/")) return h("audio", { src: att.url, controls: true });
  if (att.content_type.startsWith("video/")) return h("video", { src: att.url, controls: true, style: "max-width:320px" });
  return h("a", { href: att.url, target: "_blank", rel: "noopener" }, att.original_name);
}

async function ticketDialog(id, reload) {
  const t = await api("/admin/education/tickets/" + id);
  const text = h("textarea", { rows: "3", maxlength: "4000", placeholder: "پاسخ شما به مراجع" });
  const file = h("input", { type: "file", accept: "image/jpeg,image/png,image/webp,audio/mpeg,audio/mp4,audio/ogg,video/mp4,video/webm" });
  const thread = h("div", { class: "thread" }, t.messages.map((m) => h("div", { class: "card", style: m.from_staff ? "margin-inline-start:32px" : "margin-inline-end:32px" },
    h("div", { class: "muted" }, (m.from_staff ? (m.staff_name || "کارشناس") : "مراجع") + " · " + fmtDate(m.created_at, true)),
    m.body ? h("div", {}, m.body) : null, ticketMedia(m.attachment))));
  const reopen = async () => { await ticketDialog(id, reload); };
  dialog(t.subject + " — " + (t.patient_name || "") + " " + t.patient_mobile, h("div", { class: "form" },
    thread, field("پاسخ", text), field("پیوست (عکس، صوت یا فیلم)", file)), {
    wide: true, saveLabel: "فرستادن پاسخ",
    extraButtons: [h("button", { class: "btn secondary", type: "button", onclick: async (ev) => {
      try { await api("/admin/education/tickets/" + id + "/" + (t.status === "open" ? "close" : "reopen"), { method: "POST" }); ev.target.closest("dialog").close(); await reload(); } catch (e) { showError(e); }
    } }, t.status === "open" ? "بستن تیکت" : "باز کردن دوباره")],
    onSave: async () => {
      const f = file.files[0];
      if (!text.value.trim() && !f) throw new Error("متن یا پیوند را وارد کنید");
      if (text.value.trim()) await api("/admin/education/tickets/" + id + "/messages", { method: "POST", body: { body: text.value.trim() } });
      if (f) await uploadFile(f, () => {}, "/admin/education/tickets/" + id + "/attachment");
      toast("فرستاده شد");
      await reload();
      setTimeout(reopen, 0);
    },
  });
}

async function renderEducationTickets(main) {
  const list = h("div");
  const status = select([["", "همه"], ["open", "باز"], ["closed", "بسته"]], "open");
  async function load() {
    const rows = await api("/admin/education/tickets" + (status.value ? "?status=" + status.value : ""));
    list.replaceChildren(table(["موضوع", "مراجع", "آخرین پیام", "وضعیت", "به‌روزرسانی"], rows.map((t) => ({
      item: t,
      cells: [h("span", {}, t.unread ? badge("جدید", "red") : null, " ", t.subject),
        (t.patient_name || "—") + " · " + t.patient_mobile, t.last_message || "—",
        badge(EDC.statusText[t.status], EDC.statusTone[t.status]), fmtDate(t.updated_at, true)],
    })), (t) => ticketDialog(t.id, load).catch(showError)));
  }
  status.onchange = () => load().catch(showError);
  main.replaceChildren(h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "تیکت‌های آموزش به بیمار"), status), list);
  await load();
}
