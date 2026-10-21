/* نظرات بیماران درباره پزشکان: صف تایید برای مدیر پزشکان، پاسخ مدیر سیستم و پاسخ خود پزشک.
 * پس از appointments.js و پیش از app.js بارگذاری می‌شود. */

const RV = {
  status: { pending: "در انتظار تایید", approved: "منتشرشده", rejected: "ردشده" },
  tone: { pending: "amber", approved: "green", rejected: "gray" },
};

const reviewStars = (n) => "★".repeat(n) + "☆".repeat(5 - n);

function reviewCell(r) {
  return h("div", {},
    h("div", { class: "amber" }, reviewStars(r.stars)),
    h("div", {}, r.comment || h("span", { class: "muted" }, "فقط ستاره")),
    r.doctor_reply ? h("div", { class: "muted" }, "پاسخ پزشک: " + r.doctor_reply) : null,
    r.admin_reply ? h("div", { class: "muted" }, "پاسخ مدیریت: " + r.admin_reply) : null);
}

function replyDialog(title, url, current, reload) {
  const text = h("textarea", { required: true, maxlength: "1500" }, current || "");
  dialog(title, h("div", { class: "form" }, field("متن پاسخ", text)), {
    saveLabel: "ثبت پاسخ",
    onSave: async () => {
      await api(url, { method: "POST", body: { text: text.value.trim() } });
      toast("پاسخ ثبت شد");
      await reload();
    },
  });
}

async function renderReviews(main) {
  const canModerate = can("review.moderate");
  const status = select([["", "همه"], ...Object.entries(RV.status)], canModerate ? "pending" : "approved");
  const list = h("div");
  async function load() {
    const rows = await api("/manage/reviews" + (status.value ? "?status=" + status.value : ""));
    list.replaceChildren(table(["پزشک", "تاریخ ویزیت", "نظر", "وضعیت", ""], rows.map((r) => ({
      item: r,
      cells: [r.doctor_name, fmtDate(r.visit_at), reviewCell(r), badge(RV.status[r.status] || r.status, RV.tone[r.status]),
        h("div", { class: "toolbar" },
          canModerate && r.status !== "approved" ? h("button", { class: "btn small", onclick: () => moderate(r, "approved") }, "انتشار") : null,
          canModerate && r.status !== "rejected" ? h("button", { class: "btn small secondary", onclick: () => moderate(r, "rejected") }, "رد") : null,
          r.status === "approved" ? h("button", { class: "btn small secondary", onclick: () => replyDialog("پاسخ مدیریت", "/manage/reviews/" + r.id + "/admin-reply", r.admin_reply, load) }, "پاسخ") : null)],
    }))));
  }
  async function moderate(r, st) {
    try {
      await api("/manage/reviews/" + r.id + "/moderate", { method: "POST", body: { status: st } });
      toast(st === "approved" ? "نظر منتشر شد" : "نظر رد شد");
      await load();
    } catch (e) { showError(e); }
  }
  status.onchange = () => load().catch(showError);
  main.replaceChildren(h("h1", {}, "نظرات بیماران"),
    h("p", { class: "muted" }, "ستاره‌ها بلافاصله محاسبه می‌شوند؛ متن نظر پس از تایید منتشر می‌شود. نام بیمار هرگز نمایش داده نمی‌شود."),
    h("div", { class: "toolbar" }, status), list);
  await load();
}

async function renderMyReviews(main) {
  const list = h("div");
  async function load() {
    const rows = await api("/doctor/me/reviews");
    list.replaceChildren(table(["تاریخ ویزیت", "نظر", ""], rows.map((r) => ({
      item: r,
      cells: [fmtDate(r.visit_at), reviewCell(r),
        h("button", { class: "btn small secondary", onclick: () => replyDialog("پاسخ به نظر", "/doctor/me/reviews/" + r.id + "/reply", r.doctor_reply, load) }, r.doctor_reply ? "ویرایش پاسخ" : "پاسخ")],
    }))));
  }
  main.replaceChildren(h("h1", {}, "نظرات من"), list);
  await load();
}
