/*
 * زنگوله اعلان‌های پنل: شمارنده خوانده‌نشده‌ها (هر ۶۰ ثانیه) و فهرست کشویی.
 * پس از schedules.js و پیش از app.js بارگذاری می‌شود (از icon، ago، api و can استفاده می‌کند).
 */
"use strict";

Object.assign(ICONS, {
  notifications: "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
  doneAll: "M18 7l-1.41-1.41-6.34 6.34 1.41 1.41L18 7zm4.24-1.41L11.66 16.17 7.48 12l-1.41 1.41L11.66 19l12-12-1.42-1.41zM.41 13.41L6 19l1.41-1.41L1.83 12 .41 13.41z",
});

// نوع پیوند ← [آیکون، رنگ، نشانی صفحه پنل در صورت دسترسی]
const NOTIF_LINKS = {
  request: ["build", "blue", (id) => (can(["request.handle", "request.read"]) ? "#/requests/" + id : null)],
  incident: ["warning", "red", () => null],
  announcement: ["bell", "teal", () => null],
  course: ["school", "green", (id) => (can("training.manage") ? "#/courses/" + id : null)],
  exam: ["quiz", "amber", (id) => (can("training.manage") ? "#/exams/" + id : null)],
  chat: ["chat", "teal", () => null],
  schedule: ["calendar", "blue", (id) => (can(["schedule.write", "schedule.approve", "schedule.read.all"]) ? "#/schedules/" + id : null)],
};

const notif = { count: 0, important: 0, timer: null, panel: null, badges: new Set() };

function paintBadges() {
  for (const b of notif.badges) {
    if (!b.isConnected) {
      notif.badges.delete(b);
      continue;
    }
    b.textContent = notif.count > 99 ? "۹۹+" : fa(notif.count);
    b.hidden = notif.count === 0;
    b.classList.toggle("important", notif.important > 0);
  }
}

async function refreshUnread() {
  if (!state.me || document.hidden) return;
  try {
    const c = await api("/notifications/unread-count");
    notif.count = c.count;
    notif.important = c.important;
    paintBadges();
  } catch {
    /* شبکه قطع است؛ دور بعد دوباره */
  }
}

function startNotifPolling() {
  if (notif.timer) return;
  refreshUnread();
  notif.timer = setInterval(refreshUnread, 60000);
  document.addEventListener("visibilitychange", () => !document.hidden && refreshUnread());
}

/** دکمه زنگوله کنار نام کاربر در نوار کناری. */
function bellButton() {
  startNotifPolling();
  const count = h("span", { class: "bell-count", hidden: true });
  notif.badges.add(count);
  const btn = h("button", {
    class: "bell", type: "button", title: "اعلان‌ها", "aria-label": "اعلان‌ها", "aria-haspopup": "dialog",
    onclick: (ev) => {
      ev.stopPropagation();
      notif.panel ? closeNotifPanel() : openNotifPanel(btn);
    },
  }, icon("notifications"), count);
  queueMicrotask(paintBadges);
  return btn;
}

function closeNotifPanel() {
  notif.panel?.remove();
  notif.panel = null;
  document.removeEventListener("click", outsideClick);
  document.removeEventListener("keydown", escClose);
}

function outsideClick(ev) {
  if (notif.panel && !notif.panel.contains(ev.target)) closeNotifPanel();
}

function escClose(ev) {
  if (ev.key === "Escape") closeNotifPanel();
}

window.addEventListener("hashchange", closeNotifPanel);

function openNotifPanel(anchor) {
  const list = h("div", { class: "notif-list" }, h("div", { class: "loading" }, h("span", { class: "spinner" }), "در حال بارگذاری…"));
  const readAll = h("button", { class: "btn small secondary", type: "button", onclick: async () => {
    readAll.disabled = true;
    try {
      await api("/notifications/read-all", { method: "POST" });
      list.querySelectorAll(".notif.unread").forEach((el) => el.classList.remove("unread"));
      notif.count = notif.important = 0;
      paintBadges();
    } catch (e) {
      toast(e.message, true);
    } finally {
      readAll.disabled = false;
    }
  } }, icon("doneAll"), h("span", {}, "همه خوانده شد"));
  const panel = h("div", { class: "notif-panel", role: "dialog", "aria-label": "اعلان‌ها" },
    h("div", { class: "notif-head" }, h("h3", {}, "اعلان‌ها"), readAll),
    list);
  const r = anchor.getBoundingClientRect();
  panel.style.top = Math.max(12, r.top) + "px";
  document.body.append(panel);
  notif.panel = panel;
  document.addEventListener("click", outsideClick);
  document.addEventListener("keydown", escClose);
  loadNotifPage(list, null);
}

async function loadNotifPage(list, before) {
  let page;
  try {
    page = await api("/notifications?limit=20" + (before ? "&before=" + encodeURIComponent(before) : ""));
  } catch (e) {
    fill(list, h("p", { class: "error notif-empty" }, e.message));
    return;
  }
  notif.count = page.unread;
  paintBadges();
  if (!before) list.replaceChildren();
  list.querySelector(".notif-more")?.remove();
  if (!before && !page.items.length) {
    list.append(h("div", { class: "empty notif-empty" }, icon("notifications", "big"), h("h3", {}, "اعلانی ندارید"),
      h("p", { class: "muted" }, "درخواست تازه، ارجاع یا اطلاعیه برای شما اینجا نشان داده می‌شود.")));
    return;
  }
  list.append(...page.items.map(notifRow));
  if (page.next_before) {
    const more = h("button", { class: "btn small secondary notif-more", type: "button", onclick: () => {
      more.disabled = true;
      loadNotifPage(list, page.next_before);
    } }, "اعلان‌های قدیمی‌تر");
    list.append(more);
  }
}

function notifRow(n) {
  const [iconName, tone, link] = NOTIF_LINKS[n.link_type] || ["notifications", "gray", () => null];
  const href = n.link_id ? link(n.link_id) : null;
  const row = h("button", {
    type: "button",
    class: "notif" + (n.read ? "" : " unread") + (n.important ? " important" : "") + (href ? "" : " nolink"),
    onclick: async () => {
      if (row.classList.contains("unread")) {
        row.classList.remove("unread");
        notif.count = Math.max(0, notif.count - 1);
        paintBadges();
        api(`/notifications/${n.id}/read`, { method: "POST" }).catch(() => {});
      }
      if (href) {
        closeNotifPanel();
        location.hash = href;
      }
    },
  },
  h("span", { class: "notif-icon " + tone }, icon(iconName)),
  h("span", { class: "notif-text" },
    h("span", { class: "notif-title" }, n.title),
    n.body ? h("span", { class: "notif-body" }, n.body) : null,
    h("span", { class: "notif-meta" }, ago(n.created_at),
      n.important ? h("span", { class: "badge amber" }, "مهم") : null,
      href ? null : h("span", {}, "· مشاهده در اپ"))),
  h("span", { class: "notif-dot", "aria-hidden": "true" }));
  return row;
}
