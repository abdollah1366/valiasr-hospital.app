/* آموزش به بیمار: کتابخانه مقاله، تصویر، ویدئو، صوت و PDF با دسته‌بندی و منبع علمی.
 * پس از tokens.js و پیش از app.js بارگذاری می‌شود. */

const EDU = {
  kinds: [["article", "مقاله"], ["image", "تصویر"], ["video", "ویدئو"], ["audio", "فایل صوتی"], ["pdf", "PDF"]],
  care: [
    ["prevention", "پیشگیری"], ["before_procedure", "قبل از عمل یا اقدام"], ["after_procedure", "بعد از عمل یا اقدام"],
    ["medication", "دارو"], ["nutrition", "تغذیه"], ["exercise", "ورزش و حرکت"], ["home_care", "مراقبت در منزل"],
    ["warning_signs", "علائم هشدار"], ["other", "سایر"],
  ],
  accept: { image: "image/jpeg,image/png,image/webp", pdf: "application/pdf", audio: "audio/mpeg,audio/mp4,audio/ogg", video: "video/mp4,video/webm" },
};

function educationDialog(item, specialties, reload) {
  const title = h("input", { type: "text", required: true, maxlength: "300", value: item ? item.title : "" });
  const summary = h("textarea", { rows: "2", maxlength: "1000" }, item && item.summary ? item.summary : "");
  const kind = select(EDU.kinds, item ? item.kind : "article");
  const care = select(EDU.care, item ? item.care_type : "other");
  const condition = h("input", { type: "text", maxlength: "150", placeholder: "مثلاً دیابت", value: item && item.condition ? item.condition : "" });
  const spec = select([["", "—"], ...specialties.map((s) => [s.id, s.name])], item && item.specialty_id ? item.specialty_id : "");
  const body = h("textarea", { rows: "10", placeholder: "متن ساده و قابل فهم برای عموم" }, item && item.body ? item.body : "");
  const url = h("input", { type: "url", dir: "ltr", placeholder: "https://…", value: item && item.external_url ? item.external_url : "" });
  const source = h("textarea", { rows: "2", placeholder: "مثلاً راهنمای بالینی وزارت بهداشت، ۱۴۰۳" }, item && item.source ? item.source : "");
  const reviewed = h("input", { type: "date", dir: "ltr", value: item && item.reviewed_on ? item.reviewed_on : "" });
  const published = checkbox("منتشر شود (برای بیماران دیده شود)", item ? item.is_published : false);
  const fileState = h("span", { class: "tag" }, item && item.has_file ? "فایل: " + (item.file_name || "ثبت شده") : "بدون فایل");
  const file = h("input", { type: "file", accept: EDU.accept[item ? item.kind : "pdf"] || "", disabled: !item || item.kind === "article", onchange: async (ev) => {
    const f = ev.target.files[0];
    if (!f) return;
    try {
      const res = await uploadFile(f, (p) => (fileState.textContent = "در حال بارگذاری " + fa(Math.round(p * 100)) + "٪"), "/admin/education/" + item.id + "/file");
      fileState.textContent = "فایل: " + res.file_name;
      toast("فایل ذخیره شد");
      await reload();
    } catch (e) { fileState.textContent = "بارگذاری ناموفق"; showError(e); }
  } });
  const del = item ? h("button", { class: "btn danger", type: "button", onclick: async (ev) => {
    if (!confirm("«" + item.title + "» حذف شود؟")) return;
    try {
      await api("/admin/education/" + item.id, { method: "DELETE" });
      ev.target.closest("dialog").close();
      toast("حذف شد");
      await reload();
    } catch (e) { showError(e); }
  } }, "حذف") : null;
  dialog(item ? "ویرایش آموزش" : "آموزش جدید", h("div", { class: "form" },
    field("عنوان", title), field("خلاصه (یکی دو جمله)", summary),
    h("div", { class: "row" }, field("نوع محتوا", kind), field("نوع مراقبت", care)),
    h("div", { class: "row" }, field("بیماری یا موضوع", condition), field("تخصص مرتبط", spec)),
    field("متن مقاله (برای نوع «مقاله» الزامی است؛ برای بقیه توضیح تکمیلی)", body),
    h("div", { class: "row" }, field("فایل (jpg/png/webp، pdf، mp3/m4a/ogg، mp4/webm)", h("div", { class: "row" }, fileState, file)),
      field("یا پیوند ویدئو/فایل بیرونی", url)),
    h("div", { class: "row" }, field("منبع علمی", source), field("تاریخ آخرین بازبینی علمی", reviewed)),
    published.el,
    item ? null : h("p", { class: "muted" }, "فایل را پس از ذخیره‌ی اولیه اضافه کنید. ترجمه‌ها از صفحه‌ی «ترجمه محتوا» ثبت و تأیید می‌شوند.")), {
    wide: true,
    extraButtons: [del],
    onSave: async () => {
      const data = {
        title: title.value.trim(), summary: summary.value.trim() || null, body: body.value.trim() || null,
        kind: kind.value, care_type: care.value, condition: condition.value.trim() || null,
        specialty_id: spec.value || null, external_url: url.value.trim() || null, source: source.value.trim() || null,
        reviewed_on: reviewed.value || null, is_published: published.input.checked,
      };
      if (item) await api("/admin/education/" + item.id, { method: "PATCH", body: data });
      else await api("/admin/education", { method: "POST", body: data });
      toast("ذخیره شد");
      await reload();
    },
  });
}

async function renderEducation(main) {
  const list = h("div");
  const specialties = await api("/public/specialties");
  const label = (pairs, key) => (pairs.find((p) => p[0] === key) || [0, key])[1];
  async function load() {
    const rows = await api("/admin/education");
    list.replaceChildren(table(["عنوان", "نوع", "مراقبت", "بیماری", "وضعیت", "امتیاز", "دیده‌شده / ارسال‌شده", "بازبینی"], rows.map((i) => ({
      item: i,
      cells: [i.title, label(EDU.kinds, i.kind), label(EDU.care, i.care_type), i.condition || "—",
        i.is_published ? badge("منتشر شده", "green") : badge("پیش‌نویس", "gray"),
        educationStars(i.rating_average, i.rating_count), fa(i.seen_count) + " / " + fa(i.assigned_count),
        i.reviewed_on ? new Date(i.reviewed_on).toLocaleDateString("fa-IR") : "—"],
    })), (i) => educationDialog(i, specialties, load)));
  }
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "آموزش به بیمار"),
      h("button", { class: "btn", onclick: () => educationDialog(null, specialties, load) }, "آموزش جدید")),
    list);
  await load();
}
