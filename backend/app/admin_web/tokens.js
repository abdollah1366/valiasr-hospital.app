/* باشگاه مراجعان: اهدای ژتون، تعریف امتیازها و تحویل کد استفاده (پذیرش).
 * پس از reviews.js و پیش از app.js بارگذاری می‌شود. */

const TK = {
  status: { issued: "تحویل‌نشده", used: "تحویل شد", cancelled: "باطل" },
  tone: { issued: "amber", used: "green", cancelled: "gray" },
};

function awardDialog(p, reload) {
  const amount = h("input", { type: "number", required: true, value: "10" });
  const note = h("input", { type: "text", required: true, maxlength: "300", placeholder: "دلیل، مثلاً هدیه همکاری" });
  dialog("ژتون برای " + (p.full_name || p.mobile), h("div", { class: "form" },
    h("p", { class: "muted" }, "موجودی فعلی: " + fa(p.balance) + " (عدد منفی از موجودی کم می‌کند)"),
    field("تعداد ژتون", amount), field("دلیل", note)), {
    saveLabel: "ثبت",
    onSave: async () => {
      await api("/manage/tokens/award", { method: "POST", body: { patient_id: p.id, amount: parseInt(amount.value, 10) || 0, note: note.value.trim() } });
      toast("ثبت شد");
      await reload();
    },
  });
}

function rewardDialog(r, reload) {
  const title = h("input", { type: "text", required: true, value: r ? r.title : "" });
  const desc = h("textarea", {}, r && r.description ? r.description : "");
  const cost = h("input", { type: "number", required: true, min: "1", value: r ? String(r.cost) : "100" });
  dialog(r ? "ویرایش امتیاز" : "امتیاز جدید", h("div", { class: "form" },
    field("عنوان", title), field("توضیح (اختیاری)", desc), field("بهای ژتون", cost)), {
    onSave: async () => {
      const body = { title: title.value.trim(), description: desc.value.trim() || null, cost: parseInt(cost.value, 10) || 1 };
      if (r) await api("/manage/tokens/rewards/" + r.id, { method: "PATCH", body });
      else await api("/manage/tokens/rewards", { method: "POST", body });
      toast("ذخیره شد");
      await reload();
    },
  });
}

async function renderTokens(main) {
  const canManage = can("token.manage");
  const found = h("div");
  const rewards = h("div");
  const voucherBox = h("div");
  const q = h("input", { type: "search", placeholder: "بخشی از شماره موبایل (حداقل ۳ رقم)", dir: "ltr" });
  const code = h("input", { type: "text", placeholder: "کد استفاده مراجع", dir: "ltr" });

  async function search() {
    if (q.value.trim().length < 3) return found.replaceChildren();
    const rows = await api("/manage/tokens/patients?q=" + encodeURIComponent(q.value.trim()));
    found.replaceChildren(table(["موبایل", "نام", "موجودی ژتون", ""], rows.map((p) => ({
      item: p,
      cells: [h("span", { dir: "ltr" }, p.mobile), p.full_name || "—", fa(p.balance),
        h("button", { class: "btn small", onclick: () => awardDialog(p, search) }, "اهدا / کسر")],
    }))));
  }
  async function loadRewards() {
    const rows = await api("/manage/tokens/rewards");
    rewards.replaceChildren(table(["امتیاز", "بهای ژتون", "وضعیت", ""], rows.map((r) => ({
      item: r,
      cells: [r.title, fa(r.cost), r.is_active ? badge("فعال", "green") : badge("غیرفعال", "gray"),
        h("div", { class: "toolbar" },
          h("button", { class: "btn small secondary", onclick: () => rewardDialog(r, loadRewards) }, "ویرایش"),
          h("button", { class: "btn small secondary", onclick: async () => {
            try { await api("/manage/tokens/rewards/" + r.id, { method: "PATCH", body: { is_active: !r.is_active } }); await loadRewards(); } catch (e) { showError(e); }
          } }, r.is_active ? "غیرفعال کن" : "فعال کن"))],
    }))));
  }
  async function lookup() {
    const c = code.value.trim();
    if (!c) return voucherBox.replaceChildren();
    const v = await api("/manage/tokens/vouchers/" + encodeURIComponent(c));
    async function act(kind) {
      try { await api("/manage/tokens/vouchers/" + encodeURIComponent(v.code) + "/" + kind, { method: "POST" }); toast("ثبت شد"); await lookup(); } catch (e) { showError(e); }
    }
    voucherBox.replaceChildren(h("div", { class: "card" },
      h("div", {}, h("strong", {}, v.reward_title), " ", badge(TK.status[v.status] || v.status, TK.tone[v.status])),
      h("div", { class: "muted" }, (v.patient_name || "—") + " · ", h("span", { dir: "ltr" }, v.patient_mobile), " · بها: " + fa(v.cost) + " ژتون"),
      v.status === "issued" ? h("div", { class: "toolbar" },
        h("button", { class: "btn", onclick: () => act("use") }, "تحویل شد"),
        canManage ? h("button", { class: "btn secondary", onclick: () => act("cancel") }, "باطل و بازپرداخت") : null) : null));
  }
  q.oninput = () => search().catch(showError);
  code.onkeydown = (ev) => { if (ev.key === "Enter") lookup().catch(showError); };

  main.replaceChildren(h("h1", {}, "باشگاه ژتون"),
    h("h2", {}, "کد استفاده مراجع"),
    h("div", { class: "toolbar" }, code, h("button", { class: "btn small", onclick: () => lookup().catch(showError) }, "بررسی")),
    voucherBox,
    canManage ? h("h2", {}, "اهدای ژتون") : null, canManage ? q : null, canManage ? found : null,
    canManage ? h("div", { class: "toolbar" }, h("h2", { class: "grow" }, "امتیازهای قابل دریافت"),
      h("button", { class: "btn small", onclick: () => rewardDialog(null, loadRewards) }, "امتیاز جدید")) : null,
    canManage ? rewards : null);
  if (canManage) await loadRewards();
}
