/*
 * ترجمه محتوای عمومی (بخش‌ها، پرسش‌ها، اطلاعیه‌ها) به زبان‌های مراجعان و تأیید آن.
 * پس از notifications.js و پیش از app.js بارگذاری می‌شود (از h، api، select، toast و showError استفاده می‌کند).
 * مراجع فقط ترجمه تأییدشده را می‌بیند؛ اگر متن فارسی عوض شود، ترجمه «نیازمند بازبینی» می‌شود و تا ذخیره و تأیید دوباره، فارسی نمایش داده می‌شود.
 */
"use strict";

Object.assign(ICONS, {
  translate: "M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z",
});

const TR_LANGS = [["en", "انگلیسی"], ["az", "آذربایجانی"], ["tr", "ترکی استانبولی"], ["ar", "عربی"]];
const TR_ENTITIES = [["unit", "بخش‌ها و درمانگاه‌ها"], ["faq", "پرسش‌های متداول"], ["announcement", "اطلاعیه‌های عمومی"], ["education", "آموزش به بیمار"]];
const TR_FIELDS = {
  name: "نام", location: "مکان", hours: "ساعات کار", description: "توضیح",
  question: "پرسش", answer: "پاسخ", title: "عنوان", body: "متن",
};
const TR_STATUS = {
  missing: ["ترجمه نشده", "gray"],
  draft: ["منتظر تأیید", "amber"],
  approved: ["تأیید شده", "green"],
  outdated: ["نیازمند بازبینی", "red"],
};

async function renderTranslations(main) {
  const lang = select(TR_LANGS, "en");
  const entity = select(TR_ENTITIES, "unit");
  const summary = h("span", { class: "muted" });
  const list = h("div", { class: "stack" });

  async function load() {
    list.replaceChildren(h("p", { class: "muted" }, "در حال بارگذاری…"));
    const items = await api(`/admin/translations?entity_type=${entity.value}&language=${lang.value}`);
    const counts = { missing: 0, draft: 0, approved: 0, outdated: 0 };
    items.forEach((i) => i.fields.forEach((f) => counts[f.status]++));
    summary.textContent = Object.entries(counts).filter(([, n]) => n).map(([s, n]) => `${TR_STATUS[s][0]}: ${fa(n)}`).join(" · ");
    list.replaceChildren(...(items.length
      ? items.map((item) => translationCard(item, lang.value, load))
      : [h("p", { class: "muted" }, "محتوای عمومی برای ترجمه نیست")]));
  }

  lang.addEventListener("change", () => load().catch(showError));
  entity.addEventListener("change", () => load().catch(showError));
  main.replaceChildren(
    h("div", { class: "toolbar" }, h("h1", { class: "grow" }, "ترجمه محتوا برای مراجعان"), entity, lang),
    h("p", { class: "muted" },
      "متن فارسی اصل است. مراجع فقط ترجمه «تأیید شده» را می‌بیند؛ هر ویرایش ترجمه دوباره تأیید لازم دارد و اگر متن فارسی تغییر کند، تا بازبینی ترجمه، فارسی نمایش داده می‌شود."),
    summary,
    list,
  );
  await load();
}

function translationCard(item, language, reload) {
  const rtl = language === "ar";
  return h("div", { class: "card" },
    h("div", { class: "card-head" }, h("h2", {}, item.label)),
    item.fields.map((f) => {
      const [statusText, tone] = TR_STATUS[f.status];
      const multi = f.source.length > 80 || f.source.includes("\n");
      const input = h(multi ? "textarea" : "input", { type: multi ? null : "text", dir: rtl ? "rtl" : "ltr", value: multi ? null : f.text || "" });
      if (multi) input.value = f.text || "";
      const key = { entity_type: item.entity_type, entity_id: item.entity_id, language, field: f.field };
      const save = h("button", { class: "btn small secondary", onclick: async () => {
        if (!input.value.trim()) return toast("متن ترجمه خالی است", true);
        try {
          await api("/admin/translations", { method: "PUT", body: { ...key, text: input.value.trim() } });
          toast("ذخیره شد؛ برای نمایش به مراجعان تأیید کنید");
          await reload();
        } catch (e) { showError(e); }
      } }, "ذخیره");
      const approve = h("button", { class: "btn small", disabled: f.status !== "draft", onclick: async () => {
        try {
          await api("/admin/translations/approve", { method: "POST", body: key });
          toast("تأیید شد");
          await reload();
        } catch (e) { showError(e); }
      } }, "تأیید");
      return h("div", { class: "form" },
        h("div", { class: "toolbar" },
          h("strong", { class: "grow" }, TR_FIELDS[f.field] || f.field),
          h("span", { class: "badge " + tone }, statusText)),
        h("div", { class: "pre muted" }, f.source),
        input,
        h("div", { class: "toolbar" }, save, approve));
    }));
}
