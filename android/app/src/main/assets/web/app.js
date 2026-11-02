"use strict";
/* Khóa TV — web điện thoại 2.9. Tab dưới + mục Cài đặt đọc từ mảng cấu hình
   (cùng cấu trúc 8 mục như SettingsRegistry bên TV). Không tải lại trang:
   mọi thao tác gọi fetch POST rồi cập nhật tại chỗ + toast. */

/* --- Cấu hình --- */
var TABS = ["Hôm nay", "Mã thưởng", "Lịch", "Giới hạn", "Cài đặt"];
var TAB_IDS = { "Hôm nay": "tab-today", "Mã thưởng": "tab-rewards", "Lịch": "tab-schedules", "Giới hạn": "tab-limits", "Cài đặt": "tab-settings" };
var TAB_ICONS = { "Hôm nay": "🏠", "Mã thưởng": "🎁", "Lịch": "🗓", "Giới hạn": "⏳", "Cài đặt": "⚙️" };

/* 8 mục Cài đặt — giống SettingsRegistry.kt bên TV. */
/* v4.0: menu cài đặt gom nhóm 2 cấp (user 2026-11-02) — nhóm "Khóa TV"
   và nhóm "Màn hình chính", đồng bộ với rail cài đặt trên TV. */
var SETTING_GROUPS = [
  { title: "Khóa TV", desc: "Lịch khóa, giới hạn, mã PIN", items: [
    { id: "schedules",    title: "Lịch khóa",        desc: "Ngày + giờ khóa, khóa qua đêm" },
    { id: "daily_limits", title: "Giới hạn ngày",    desc: "Giới hạn xem từng ngày trong tuần" },
    { id: "news_slot",    title: "Giờ thời sự",      desc: "Khung giờ chỉ xem thời sự" },
    { id: "app_rules",    title: "Giới hạn từng app", desc: "Chặn / giới hạn riêng từng app" },
    { id: "lock_screen",  title: "Màn hình khóa",    desc: "Lời nhắc + ảnh nền" },
    { id: "pin",          title: "Mã PIN",           desc: "Đổi mã PIN mở khóa TV" },
    { id: "webpin",       title: "Mã PIN web",       desc: "Đổi mã PIN vào web + đăng xuất" },
    { id: "bonus",        title: "Phút thưởng",      desc: "Quỹ phút thưởng cho con" },
    { id: "access",       title: "Trợ năng",         desc: "Trợ năng + chống tắt" }
  ]},
  { title: "Màn hình chính", desc: "Ảnh nền, widget, thông báo", items: [
    { id: "home_wallpaper", title: "Ảnh nền", desc: "Tải ảnh từ điện thoại lên TV" },
    { id: "widgets", title: "Widget", desc: "Bật/tắt các widget (đồng bộ với TV)" },
    { id: "countdown", title: "Đếm ngược", desc: "Mốc đếm ngược trên widget" },
    { id: "push", title: "Thông báo đẩy", desc: "Khung giờ + chế độ popup" },
    { id: "launcher_style", title: "Kiểu icon & thời tiết", desc: "Kiểu icon, thành phố thời tiết" },
    { id: "hidden_apps", title: "Ứng dụng đã ẩn", desc: "Hiện lại app đã ẩn" }
  ]}
];

var LIMIT_OPTIONS = [[5, "5 phút"], [10, "10 phút"], [15, "15 phút"], [30, "30 phút"],
  [60, "60 phút"], [90, "90 phút"], [120, "120 phút"], [180, "180 phút"],
  [240, "240 phút"], [-1, "Không giới hạn"]];
var APP_RULE_MODES = [
  ["normal", "Bình thường"],
  ["limit", "Giới hạn riêng"],
  ["free", "Không tính giờ"],
  ["blocked", "Chặn"]
];
var APP_RULE_MINUTES = [15, 30, 60];
var DAY_NAMES = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];
var DAY_SHORT = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
var KEY_ROWS = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["⌫", "0", "✓"]];

/* --- Trạng thái --- */
var statusData = null;
var settingsData = null;
var stagedImg = null; /* data-URL ảnh nền đang sửa, chưa Áp dụng (hành vi 2.1) */

/* --- Helper --- */
function $(id) { return document.getElementById(id); }
function esc(s) {
  return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
    .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
function fmtClock(min) {
  var m = Number(min) || 0;
  return ("0" + Math.floor(m / 60)).slice(-2) + ":" + ("0" + (m % 60)).slice(-2);
}
/* ≥60 phút → "1g 12p", <60 → "45p", -1/không giới hạn → "Không giới hạn". */
function fmtShort(min) {
  if (min == null || min < 0) return "Không giới hạn";
  if (min >= 60) {
    var h = Math.floor(min / 60), r = min % 60;
    return r ? h + "g " + r + "p" : h + "g";
  }
  return min + "p";
}
function fmtLimit(min) { return min <= 0 ? "không giới hạn" : fmtShort(min); }

var toastTimer = null;
function toast(msg, ok) {
  var t = $("toast");
  t.textContent = msg;
  t.className = "toast " + (ok ? "ok" : "err");
  t.hidden = false;
  if (toastTimer) clearTimeout(toastTimer);
  // Tin báo lỗi để 8s cho kịp đọc (nhất là câu lỗi chẩn đoán khi tải ảnh),
  // tin thành công vẫn 2.5s như cũ.
  toastTimer = setTimeout(function () { t.hidden = true; }, ok ? 2500 : 8000);
}

function postForm(path, params) {
  var body = Object.keys(params).map(function (k) {
    return encodeURIComponent(k) + "=" + encodeURIComponent(params[k]);
  }).join("&");
  return fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body
  }).then(function (r) { return r.ok; }).catch(function () { return false; });
}
function postFormJson(path, params) {
  var body = Object.keys(params).map(function (k) {
    return encodeURIComponent(k) + "=" + encodeURIComponent(params[k]);
  }).join("&");
  return fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body
  }).then(function (r) {
    return r.json().catch(function () { return { ok: r.ok }; });
  }).catch(function () { return { ok: false }; });
}
function postJson(path, params) {
  var body = Object.keys(params).map(function (k) {
    return encodeURIComponent(k) + "=" + encodeURIComponent(params[k]);
  }).join("&");
  return fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body
  }).then(function (r) { return r.json().catch(function () { return { ok: false }; }); })
    .catch(function () { return { ok: false }; });
}
/* v2.3: true khi lần fetch JSON gần nhất bị 401 (phiên hết hạn) — để
   loadStatus biết mà hiện màn hình nhập PIN thay vì im lặng. */
var lastWas401 = false;
function getJson(path) {
  return fetch(path).then(function (r) {
    lastWas401 = (r.status === 401);
    return r.ok ? r.json() : null;
  }).catch(function () { return null; });
}

/* --- Tab dưới --- */
function buildTabbar() {
  var bar = $("tabbar");
  TABS.forEach(function (name) {
    var b = document.createElement("button");
    b.className = "tabbtn";
    b.dataset.tab = name;
    b.innerHTML = '<span class="tabicon">' + TAB_ICONS[name] + '</span><span>' + esc(name) + "</span>";
    b.addEventListener("click", function () { showTab(name); });
    bar.appendChild(b);
  });
}
function showTab(name) {
  TABS.forEach(function (n) {
    $(TAB_IDS[n]).classList.toggle("active", n === name);
  });
  document.querySelectorAll(".tabbtn").forEach(function (b) {
    b.classList.toggle("active", b.dataset.tab === name);
  });
  if (name === "Hôm nay") { loadStatus(); loadReport(); }
  if (name === "Mã thưởng") { loadRewardCodes(); }
  if (name === "Lịch") loadSchedules();
  if (name === "Giới hạn") loadLimits();
  if (name === "Cài đặt") renderSettingsList();
  window.scrollTo(0, 0);
}

/* --- Đăng nhập mã PIN web (v2.3) --- */
var authed = false;
function showLoginScreen(msg) {
  authed = false;
  $("loginScreen").hidden = false;
  document.querySelector("main").style.display = "none";
  $("tabbar").style.display = "none";
  $("btnLogout").hidden = true;
  if (msg) {
    var e = $("loginError");
    e.textContent = msg;
    e.hidden = false;
  }
}
function hideLoginScreen() {
  authed = true;
  $("loginScreen").hidden = true;
  document.querySelector("main").style.display = "";
  $("tabbar").style.display = "";
  $("btnLogout").hidden = false;
}
function doLogin() {
  var pin = $("loginPin").value.trim();
  if (!/^\d{4,6}$/.test(pin)) {
    var err0 = $("loginError");
    err0.textContent = "Mã PIN web là 4–6 chữ số.";
    err0.hidden = false;
    return;
  }
  var btn = $("btnLogin");
  btn.disabled = true;
  btn.textContent = "Đang kiểm tra...";
  postJson("/api/webpin-login", { pin: pin }).then(function (res) {
    btn.disabled = false;
    btn.textContent = "Mở khóa";
    if (res && res.ok) {
      $("loginPin").value = "";
      $("loginError").hidden = true;
      hideLoginScreen();
      showTab("Hôm nay");
      loadStatus();
      loadSettings();
      loadReport();
      loadAppLabels();
    } else {
      var err = $("loginError");
      err.textContent = (res && res.error) || "Sai mã PIN";
      err.hidden = false;
    }
  });
}
function doLogout() {
  postJson("/api/webpin-logout", {}).then(function () {
    showLoginScreen();
    $("loginPin").value = "";
    $("loginPin").focus();
  });
}

/* --- Trang Hôm nay --- */
function renderHome(s) {
  var pill = $("pill");
  var locked = !!s.locked;
  pill.textContent = locked ? "ĐÃ KHÓA" : "ĐANG MỞ";
  pill.className = "pill " + (locked ? "locked" : "open");
  $("hero").textContent = fmtShort(s.remainingMin);
  $("subline").textContent = "còn lại hôm nay · đã xem " + s.usedTodayMin + "p / " + fmtLimit(s.dailyLimitMin);
  var prog = $("progress");
  var pct = s.dailyLimitMin > 0 ? Math.min(100, Math.round(s.usedTodayMin * 100 / s.dailyLimitMin)) : 100;
  prog.style.width = pct + "%";
  prog.classList.toggle("locked", locked);
  $("bonusPool").textContent = s.bonusMin > 0 ? s.bonusMin + " phút" : "0 phút";
  /* "Khóa đêm": ưu tiên lịch qua đêm trong lịch hôm nay (field nightLock
     do /api/status trả), không có thì "Không có lịch khóa". */
  $("nightLock").textContent = s.nightLock ? s.nightLock : "Không có lịch khóa";
  if (settingsData) {
    $("newsTime").textContent = fmtClock(settingsData.newsStartMin) + " – " + fmtClock(settingsData.newsEndMin);
  }
}
function loadStatus() {
  getJson("/api/status").then(function (s) {
    if (!s) { if (lastWas401) showLoginScreen(); return; }
    statusData = s;
    renderHome(s);
  });
}
function loadSettings() {
  getJson("/api/settings").then(function (s) {
    if (!s) return;
    settingsData = s;
    if (statusData) renderHome(statusData);
  });
}
function loadReport() {
  getJson("/api/report").then(function (d) {
    if (!d) return;
    renderReport(d);
  });
}
function renderReport(d) {
  var el = $("reportBody");
  var html = "";
  var today = d.today || {};
  html += '<div class="info-row"><span class="muted">Hôm nay</span><b>' +
    (today.total || 0) + "p / " + fmtLimit(today.limit) + "</b></div>";
  var perApp = today.perApp || {};
  var keys = Object.keys(perApp).sort(function (a, b) { return perApp[b] - perApp[a]; });
  keys.forEach(function (pkg) {
    html += '<div class="info-row"><span class="muted">' + esc(appLabel(pkg)) +
      '</span><b>' + perApp[pkg] + " phút</b></div>";
  });
  var days = (d.days || []).slice(-7).reverse();
  if (days.length) {
    html += '<div class="lbl">7 ngày gần nhất</div>';
    var max = Math.max.apply(null, days.map(function (x) { return x.total; }).concat([1]));
    days.forEach(function (x) {
      var w = Math.max(2, Math.round(x.total * 100 / max));
      html += '<div class="rowsub">' + esc(fmtDateShort(x.date)) + " — " + x.total +
        ' phút</div><div class="bar"><div style="width:' + w + '%"></div></div>';
    });
  }
  el.innerHTML = html;
  el.classList.remove("muted");
}
function fmtDateShort(date) { /* yyyy-MM-dd -> dd/MM */
  var p = String(date || "").split("-");
  return p.length === 3 ? p[2] + "/" + p[1] : date;
}
/* Nhãn app cho báo cáo: lấy từ /api/apprules (cache), fallback tên gói. */
var appLabels = {};
function loadAppLabels() {
  getJson("/api/apprules").then(function (d) {
    if (!d) return;
    (d.apps || []).forEach(function (a) { appLabels[a.pkg] = a.label; });
  });
}
function appLabel(pkg) { return appLabels[pkg] || pkg; }

/* --- Tab Lịch --- */
function loadSchedules() {
  getJson("/api/schedules").then(function (d) {
    renderSchedules(d ? d.schedules || [] : []);
  });
}
function renderSchedules(list) {
  var el = $("scheduleList");
  el.innerHTML = "";
  if (!list.length) {
    el.innerHTML = '<p class="muted">Chưa có lịch khóa nào.</p>';
    return;
  }
  list.forEach(function (s) {
    var row = document.createElement("div");
    row.className = "rowcard";
    var main = document.createElement("div");
    main.className = "rowmain";
    main.innerHTML = "<b>" + esc(s.summary) + "</b>";
    var acts = document.createElement("div");
    acts.className = "rowactions";
    var editBtn = document.createElement("button");
    editBtn.className = "btn small ghost";
    editBtn.textContent = "Sửa";
    editBtn.addEventListener("click", function () { fillScheduleForm(s); });
    var delBtn = document.createElement("button");
    delBtn.className = "btn small danger";
    delBtn.textContent = "Xóa";
    delBtn.addEventListener("click", function () {
      if (!confirm("Xóa lịch này?")) return;
      postForm("/api/settings-schedule-delete", { id: s.id }).then(function (ok) {
        toast(ok ? "Đã xóa lịch khóa." : "Không xóa được, thử lại nhé.", ok);
        if (ok) loadSchedules();
      });
    });
    acts.appendChild(editBtn);
    acts.appendChild(delBtn);
    row.appendChild(main);
    row.appendChild(acts);
    el.appendChild(row);
  });
}
function buildDayBoxes() {
  var box = $("schedDays");
  DAY_SHORT.forEach(function (label, d) {
    var lab = document.createElement("label");
    var cb = document.createElement("input");
    cb.type = "checkbox";
    cb.id = "schedDay" + d;
    cb.checked = d < 5; /* mặc định T2–T6 */
    lab.appendChild(cb);
    lab.appendChild(document.createTextNode(" " + label));
    box.appendChild(lab);
  });
}
function fillScheduleForm(s) {
  $("schedId").value = s.id;
  $("schedFormTitle").textContent = "Sửa lịch khóa";
  for (var d = 0; d < 7; d++) $("schedDay" + d).checked = (s.days || []).indexOf(d) >= 0;
  $("schedStart").value = fmtClock(s.startMin);
  $("schedEnd").value = fmtClock(s.endMin);
  $("schedCancelEdit").hidden = false;
  $("schedStart").scrollIntoView({ block: "center" });
}
function resetScheduleForm() {
  $("schedId").value = "";
  $("schedFormTitle").textContent = "Thêm lịch khóa mới";
  for (var d = 0; d < 7; d++) $("schedDay" + d).checked = d < 5;
  $("schedStart").value = "21:00";
  $("schedEnd").value = "07:00";
  $("schedCancelEdit").hidden = true;
}
function saveSchedule() {
  var params = { id: $("schedId").value, start: $("schedStart").value, end: $("schedEnd").value };
  var anyDay = false;
  for (var d = 0; d < 7; d++) {
    if ($("schedDay" + d).checked) { params["d" + d] = "1"; anyDay = true; }
  }
  if (!anyDay) { toast("Chọn ít nhất một ngày trong tuần.", false); return; }
  if (!params.start || !params.end) { toast("Giờ khóa không hợp lệ.", false); return; }
  postForm("/api/settings-schedule", params).then(function (ok) {
    toast(ok ? "Đã lưu lịch khóa." : "Không lưu được, thử lại nhé.", ok);
    if (ok) { resetScheduleForm(); loadSchedules(); }
  });
}

/* --- Tab Giới hạn --- */
function loadLimits() {
  getJson("/api/limits").then(function (d) {
    renderLimits(d ? d.limits || {} : {});
  });
}
function renderLimits(limits) {
  var el = $("limitList");
  el.innerHTML = "";
  for (var d = 0; d < 7; d++) {
    (function (day) {
      var row = document.createElement("div");
      row.className = "rowcard";
      var main = document.createElement("div");
      main.className = "rowmain";
      main.innerHTML = "<b>" + esc(DAY_NAMES[day]) + "</b>";
      var sel = document.createElement("select");
      sel.setAttribute("aria-label", "Giới hạn " + DAY_NAMES[day]);
      LIMIT_OPTIONS.forEach(function (o) {
        var opt = document.createElement("option");
        opt.value = o[0];
        opt.textContent = o[1];
        if (String(limits[day]) === String(o[0])) opt.selected = true;
        sel.appendChild(opt);
      });
      sel.addEventListener("change", function () {
        var p = {};
        p["limit" + day] = sel.value;
        postForm("/api/settings-limits", p).then(function (ok) {
          toast(ok ? "Đã lưu giới hạn " + DAY_NAMES[day] + "." : "Không lưu được, thử lại nhé.", ok);
          if (ok) loadLimits();
        });
      });
      row.appendChild(main);
      row.appendChild(sel);
      el.appendChild(row);
    })(d);
  }
}

/* --- Tab Cài đặt (gom nhóm 2 cấp) --- */
function renderSettingsList() {
  var el = $("settingsList");
  el.innerHTML = "";
  SETTING_GROUPS.forEach(function (g) {
    var h = document.createElement("div");
    h.className = "group-head";
    h.innerHTML = "<b>" + esc(g.title) + '</b><br><span class="muted small">' +
      esc(g.desc) + "</span>";
    el.appendChild(h);
    g.items.forEach(function (item) {
      var row = document.createElement("button");
      row.className = "setting-row";
      row.innerHTML = "<span><b>" + esc(item.title) + '</b><br><span class="muted small">' +
        esc(item.desc) + '</span></span><span class="chev">›</span>';
      row.addEventListener("click", function () { openSetting(item.id); });
      el.appendChild(row);
    });
  });
  $("settingsPanel").innerHTML = "";
}
function openSetting(id) {
  if (id === "schedules") { showTab("Lịch"); return; }
  if (id === "daily_limits") { showTab("Giới hạn"); return; }
  var panel = $("settingsPanel");
  panel.innerHTML = "";
  var back = document.createElement("button");
  back.className = "btn ghost wide";
  back.textContent = "← Cài đặt";
  back.addEventListener("click", renderSettingsList);
  var card = document.createElement("div");
  card.className = "card";
  panel.appendChild(back);
  panel.appendChild(card);
  if (id === "news_slot") panelNews(card);
  else if (id === "app_rules") panelAppRules(card);
  else if (id === "lock_screen") panelLockScreen(card);
  else if (id === "home_wallpaper") panelHomeWallpaper(card);
  else if (id === "widgets") panelWidgets(card);
  else if (id === "countdown") panelCountdown(card);
  else if (id === "push") panelPush(card);
  else if (id === "launcher_style") panelLauncherStyle(card);
  else if (id === "hidden_apps") panelHiddenApps(card);
  else if (id === "pin") panelPin(card);
  else if (id === "webpin") panelWebPin(card);
  else if (id === "bonus") panelBonus(card);
  else if (id === "access") panelAccess(card);
  card.scrollIntoView({ block: "start" });
}
/* Widget màn hình chính: bật/tắt 5 widget (đồng bộ với nút trên TV,
   user 2026-11-02 — trước đây chỉ đổi được trên TV). */
var WIDGET_DEFS = [
  ["tinker", "Widget Tinker", "Thông báo từ Tinker"],
  ["fare", "Widget vé máy bay", "Theo dõi giá vé Đà Nẵng"],
  ["countdown", "Widget đếm ngược", "Đếm ngược chuyến đi Đà Nẵng"],
  ["family", "Widget gia đình", "Bảng trạng thái gia đình"],
  ["camera", "Widget camera", "Xem camera (sắp có)"]
];
function panelWidgets(card) {
  panelTitle(card, "Widget màn hình chính",
    "Bật/tắt các widget trên màn hình chính TV — đổi là lưu ngay, giống nút trên TV.");
  var list = document.createElement("div");
  list.innerHTML = '<p class="muted">Đang tải…</p>';
  card.appendChild(list);
  getJson("/api/widgets").then(function (d) {
    list.innerHTML = "";
    if (!d) { list.innerHTML = '<p class="muted">Không tải được, thử lại nhé.</p>'; return; }
    var state = {};
    WIDGET_DEFS.forEach(function (w) { state[w[0]] = !!d[w[0]]; });
    WIDGET_DEFS.forEach(function (w) {
      var key = w[0];
      var row = document.createElement("div");
      row.className = "rowcard";
      var main = document.createElement("div");
      main.className = "rowmain";
      main.innerHTML = "<b>" + esc(w[1]) + "</b>";
      var sub = document.createElement("div");
      sub.className = "rowsub";
      sub.textContent = w[2];
      var btn = document.createElement("button");
      btn.className = "btn wide";
      var paint = function () {
        btn.textContent = w[1] + ": " + (state[key] ? "Bật" : "Tắt");
        btn.className = "btn wide" + (state[key] ? " success" : "");
      };
      paint();
      btn.addEventListener("click", function () {
        state[key] = !state[key];
        paint();
        var body = {};
        WIDGET_DEFS.forEach(function (x) { body[x[0]] = state[x[0]] ? "1" : "0"; });
        postForm("/api/settings-widgets", body).then(function (ok) {
          toast(ok ? "Đã lưu widget." : "Không lưu được, thử lại nhé.", ok);
        });
      });
      main.appendChild(sub);
      row.appendChild(main);
      row.appendChild(btn);
      list.appendChild(row);
    });
  });
}

/* Đếm ngược: nhãn + ngày mốc (đồng bộ với TV, user 2026-11-02). */
function panelCountdown(card) {
  panelTitle(card, "Đếm ngược", "Mốc đếm ngược hiện trên widget màn hình chính.");
  var wrap = document.createElement("div");
  wrap.innerHTML = '<p class="muted">Đang tải…</p>';
  card.appendChild(wrap);
  getJson("/api/launcher").then(function (d) {
    wrap.innerHTML = "";
    if (!d) { wrap.innerHTML = '<p class="muted">Không tải được, thử lại nhé.</p>'; return; }
    var lab = document.createElement("div");
    lab.innerHTML = '<div class="rowsub">Tên mốc</div>';
    var inpLabel = document.createElement("input");
    inpLabel.type = "text"; inpLabel.maxLength = 24;
    inpLabel.value = d.cdLabel || "";
    inpLabel.className = "wide";
    lab.appendChild(inpLabel);
    var dt = document.createElement("div");
    dt.innerHTML = '<div class="rowsub" style="margin-top:8px">Ngày mốc</div>';
    var inpDate = document.createElement("input");
    inpDate.type = "date"; inpDate.value = d.cdDate || "";
    dt.appendChild(inpDate);
    var btn = document.createElement("button");
    btn.className = "btn wide"; btn.style.marginTop = "10px";
    btn.textContent = "Lưu đếm ngược";
    btn.addEventListener("click", function () {
      postForm("/api/settings-countdown", {
        label: inpLabel.value, date: inpDate.value
      }).then(function (ok) {
        toast(ok ? "Đã lưu đếm ngược." : "Không lưu được, thử lại nhé.", ok);
      });
    });
    wrap.appendChild(lab); wrap.appendChild(dt); wrap.appendChild(btn);
  });
}

/* Thông báo đẩy: khung giờ popup + chế độ popup (đồng bộ với TV). */
var PUSH_MODES = [["always", "Luôn bật"], ["home", "Chỉ ở màn hình chính"], ["silent", "Im lặng (chỉ chuông)"]];
function panelPush(card) {
  panelTitle(card, "Thông báo đẩy", "Khung giờ hiện popup và chế độ hiện thông báo.");
  var wrap = document.createElement("div");
  wrap.innerHTML = '<p class="muted">Đang tải…</p>';
  card.appendChild(wrap);
  getJson("/api/launcher").then(function (d) {
    wrap.innerHTML = "";
    if (!d) { wrap.innerHTML = '<p class="muted">Không tải được, thử lại nhé.</p>'; return; }
    var mkNum = function (label, val) {
      var box = document.createElement("div");
      box.innerHTML = '<div class="rowsub">' + esc(label) + "</div>";
      var sel = document.createElement("select");
      for (var h = 0; h < 24; h++) {
        var o = document.createElement("option");
        o.value = h; o.textContent = h + " giờ";
        if (h === val) o.selected = true;
        sel.appendChild(o);
      }
      box.appendChild(sel);
      return { box: box, sel: sel };
    };
    var s = mkNum("Popup từ giờ", Number(d.pushStart) || 7);
    var e = mkNum("Đến giờ", Number(d.pushEnd) || 21);
    wrap.appendChild(s.box); wrap.appendChild(e.box);
    var mbox = document.createElement("div");
    mbox.innerHTML = '<div class="rowsub" style="margin-top:8px">Chế độ popup</div>';
    var msel = document.createElement("select");
    PUSH_MODES.forEach(function (m) {
      var o = document.createElement("option");
      o.value = m[0]; o.textContent = m[1];
      if (m[0] === d.popupMode) o.selected = true;
      msel.appendChild(o);
    });
    mbox.appendChild(msel);
    wrap.appendChild(mbox);
    var btn = document.createElement("button");
    btn.className = "btn wide"; btn.style.marginTop = "10px";
    btn.textContent = "Lưu thông báo đẩy";
    btn.addEventListener("click", function () {
      postForm("/api/settings-push", {
        start: s.sel.value, end: e.sel.value, mode: msel.value
      }).then(function (ok) {
        toast(ok ? "Đã lưu thông báo đẩy." : "Không lưu được, thử lại nhé.", ok);
      });
    });
    wrap.appendChild(btn);
  });
}

/* Kiểu icon + thành phố thời tiết (đồng bộ với TV). */
function panelLauncherStyle(card) {
  panelTitle(card, "Kiểu icon & thời tiết", "Kiểu icon ứng dụng và thành phố hiện thời tiết.");
  var wrap = document.createElement("div");
  wrap.innerHTML = '<p class="muted">Đang tải…</p>';
  card.appendChild(wrap);
  getJson("/api/launcher").then(function (d) {
    wrap.innerHTML = "";
    if (!d) { wrap.innerHTML = '<p class="muted">Không tải được, thử lại nhé.</p>'; return; }
    var ibox = document.createElement("div");
    ibox.innerHTML = '<div class="rowsub">Kiểu icon ứng dụng</div>';
    var isel = document.createElement("select");
    [["default", "Mặc định"], ["circle", "Tròn"]].forEach(function (m) {
      var o = document.createElement("option");
      o.value = m[0]; o.textContent = m[1];
      if (m[0] === d.iconStyle) o.selected = true;
      isel.appendChild(o);
    });
    isel.addEventListener("change", function () {
      postForm("/api/settings-iconstyle", { style: isel.value }).then(function (ok) {
        toast(ok ? "Đã đổi kiểu icon." : "Không lưu được, thử lại nhé.", ok);
      });
    });
    ibox.appendChild(isel);
    var wbox = document.createElement("div");
    wbox.innerHTML = '<div class="rowsub" style="margin-top:8px">Thành phố thời tiết</div>';
    var wsel = document.createElement("select");
    [["hanoi", "Hà Nội"], ["danang", "Đà Nẵng"], ["hcmc", "TP.HCM"]].forEach(function (m) {
      var o = document.createElement("option");
      o.value = m[0]; o.textContent = m[1];
      if (m[0] === d.weatherCity) o.selected = true;
      wsel.appendChild(o);
    });
    wsel.addEventListener("change", function () {
      postForm("/api/settings-weathercity", { city: wsel.value }).then(function (ok) {
        toast(ok ? "Đã đổi thành phố." : "Không lưu được, thử lại nhé.", ok);
      });
    });
    wbox.appendChild(wsel);
    wrap.appendChild(ibox); wrap.appendChild(wbox);
  });
}

/* Ứng dụng đã ẩn: hiện lại app đã ẩn khỏi màn hình chính. */
function panelHiddenApps(card) {
  panelTitle(card, "Ứng dụng đã ẩn", "App đang bị ẩn khỏi màn hình chính — bấm để hiện lại.");
  var list = document.createElement("div");
  list.innerHTML = '<p class="muted">Đang tải…</p>';
  card.appendChild(list);
  var load = function () {
    getJson("/api/hidden-apps").then(function (d) {
      list.innerHTML = "";
      var apps = (d && d.apps || []).filter(function (a) { return a.hidden; });
      if (!apps.length) { list.innerHTML = '<p class="muted">Không có app nào bị ẩn.</p>'; return; }
      apps.forEach(function (a) {
        var row = document.createElement("div");
        row.className = "rowcard";
        var main = document.createElement("div");
        main.className = "rowmain";
        main.innerHTML = "<b>" + esc(a.label) + "</b>";
        var btn = document.createElement("button");
        btn.className = "btn"; btn.textContent = "Hiện lại";
        btn.addEventListener("click", function () {
          postForm("/api/settings-hiddenapp", { pkg: a.pkg, hidden: "0" }).then(function (ok) {
            toast(ok ? "Đã hiện lại " + a.label + "." : "Không lưu được, thử lại nhé.", ok);
            if (ok) load();
          });
        });
        row.appendChild(main); row.appendChild(btn);
        list.appendChild(row);
      });
    });
  };
  load();
}

/* Ảnh nền màn hình chính: tải ảnh từ điện thoại lên TV (user 2026-10-31 —
   trước đây chỉ có ở trang cũ /legacy/settings). */
function panelHomeWallpaper(card) {
  panelTitle(card, "Ảnh nền màn hình chính",
    "Chọn dùng 10 ảnh phong cảnh mặc định kèm app, hoặc ảnh của anh tải từ điện thoại lên.");
  /* Nguồn ảnh nền: mặc định <-> ảnh của tôi. */
  lbl(card, "Nguồn ảnh nền");
  var srcRow = document.createElement("div");
  srcRow.className = "row2";
  var srcDef = document.createElement("button");
  srcDef.className = "btn wide";
  srcDef.textContent = "Ảnh mặc định";
  var srcCustom = document.createElement("button");
  srcCustom.className = "btn wide";
  srcCustom.textContent = "Ảnh của tôi";
  srcRow.appendChild(srcDef);
  srcRow.appendChild(srcCustom);
  card.appendChild(srcRow);
  function setSource(s) {
    postForm("/api/settings-wpsource", { source: s }).then(function (ok) {
      toast(ok ? "Đã đổi nguồn ảnh nền." : "Không đổi được, thử lại nhé.", ok);
      if (ok) refreshWpList();
    });
  }
  srcDef.addEventListener("click", function () { setSource("default"); });
  srcCustom.addEventListener("click", function () { setSource("custom"); });
  /* Ảnh mặc định kèm app — xem trước. */
  lbl(card, "Ảnh mặc định kèm app (10 ảnh phong cảnh)");
  var defGrid = document.createElement("div");
  defGrid.style.cssText = "display:flex;flex-wrap:wrap;gap:8px;margin:12px 0";
  card.appendChild(defGrid);
  var stateP = document.createElement("p");
  stateP.className = "muted small";
  card.appendChild(stateP);
  lbl(card, "Ảnh của tôi — chọn ảnh từ điện thoại");
  var fileInput = document.createElement("input");
  fileInput.type = "file";
  fileInput.accept = "image/*";
  card.appendChild(fileInput);
  var addBtn = document.createElement("button");
  addBtn.className = "btn success wide";
  addBtn.textContent = "Thêm ảnh lên TV";
  addBtn.disabled = true;
  card.appendChild(addBtn);
  var stagedData = null;
  fileInput.addEventListener("change", function () {
    var f = fileInput.files[0];
    if (!f) { addBtn.disabled = true; return; }
    var r = new FileReader();
    r.onload = function () { stagedData = r.result; addBtn.disabled = false; };
    r.readAsDataURL(f);
  });
  addBtn.addEventListener("click", function () {
    if (!stagedData) return;
    addBtn.disabled = true;
    addBtn.textContent = "Đang tải...";
    fetch("/api/settings-homebg", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "img=" + encodeURIComponent(stagedData)
    }).then(function (r) {
      return r.json().catch(function () { return { ok: r.ok }; });
    }).then(function (d) {
      if (d && d.ok === false) {
        toast(d.error || "Không tải được ảnh, thử lại nhé.");
        addBtn.disabled = false;
        addBtn.textContent = "Thêm ảnh lên TV";
        return;
      }
      stagedData = null;
      fileInput.value = "";
      addBtn.textContent = "Thêm ảnh lên TV";
      refreshWpList();
    }).catch(function () {
      toast("Không tải được ảnh, thử lại nhé.");
      addBtn.disabled = false;
      addBtn.textContent = "Thêm ảnh lên TV";
    });
  });
  lbl(card, "Tự đổi ảnh mỗi");
  var intRow = document.createElement("div");
  intRow.className = "row2";
  var intSel = document.createElement("select");
  [[0, "Tắt"], [15, "15 phút"], [30, "30 phút"], [60, "60 phút"]].forEach(function (o) {
    var opt = document.createElement("option");
    opt.value = o[0]; opt.textContent = o[1];
    intSel.appendChild(opt);
  });
  var intSave = document.createElement("button");
  intSave.className = "btn success";
  intSave.textContent = "Lưu";
  intRow.appendChild(intSel);
  intRow.appendChild(intSave);
  card.appendChild(intRow);
  intSave.addEventListener("click", function () {
    fetch("/api/settings-wpinterval", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "minutes=" + encodeURIComponent(intSel.value)
    }).then(refreshWpList).catch(function () { toast("Không lưu được, thử lại nhé."); });
  });
  var grid = document.createElement("div");
  grid.style.cssText = "display:flex;flex-wrap:wrap;gap:8px;margin:12px 0";
  card.appendChild(grid);
  var clearBtn = document.createElement("button");
  clearBtn.className = "btn danger wide";
  clearBtn.textContent = "Xóa hết ảnh nền";
  card.appendChild(clearBtn);
  clearBtn.addEventListener("click", function () {
    if (!confirm("Xóa hết ảnh nền màn hình chính?")) return;
    fetch("/api/settings-homebg-clear", { method: "POST" })
      .then(refreshWpList).catch(function () { toast("Không xóa được, thử lại nhé."); });
  });
  function refreshWpList() {
    getJson("/api/homebg-list").then(function (d) {
      if (!d) return;
      var n = (d.wallpapers || []).length;
      intSel.value = String(d.interval || 0);
      /* Trạng thái nguồn đang dùng. */
      var src = d.source || "default";
      srcDef.className = "btn wide" + (src === "default" ? " success" : "");
      srcCustom.className = "btn wide" + (src === "custom" ? " success" : "");
      stateP.textContent = src === "default"
        ? "Đang dùng: 10 ảnh phong cảnh mặc định" + (d.interval > 0 ? " — tự đổi mỗi " + d.interval + " phút." : " — đang tắt tự đổi.")
        : (n === 0 ? "Đang dùng: ảnh của tôi (chưa có ảnh nào)."
          : "Đang dùng: " + n + " ảnh của tôi" + (d.interval > 0 ? " — tự đổi mỗi " + d.interval + " phút." : " — đang tắt tự đổi."));
      /* Lưới ảnh mặc định — chỉ xem trước. */
      defGrid.innerHTML = "";
      (d.defaults || []).forEach(function (name) {
        var wrap = document.createElement("div");
        wrap.style.cssText = "width:30%";
        var img = document.createElement("img");
        img.src = "/api/homebg-default-thumb?name=" + encodeURIComponent(name);
        img.style.cssText = "width:100%;border-radius:8px;display:block";
        img.alt = name;
        img.loading = "lazy";
        wrap.appendChild(img);
        defGrid.appendChild(wrap);
      });
      grid.innerHTML = "";
      (d.wallpapers || []).forEach(function (w) {
        var wrap = document.createElement("div");
        wrap.style.cssText = "position:relative;width:30%";
        var img = document.createElement("img");
        img.src = "/api/homebg-thumb?name=" + encodeURIComponent(w.name);
        img.style.cssText = "width:100%;border-radius:8px;display:block";
        img.alt = w.name;
        var del = document.createElement("button");
        del.className = "btn danger";
        del.textContent = "✕";
        del.style.cssText = "position:absolute;top:4px;right:4px;padding:4px 10px;width:auto";
        del.addEventListener("click", function () {
          fetch("/api/settings-homebg-delete", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: "name=" + encodeURIComponent(w.name)
          }).then(refreshWpList).catch(function () { toast("Không xóa được, thử lại nhé."); });
        });
        wrap.appendChild(img);
        wrap.appendChild(del);
        grid.appendChild(wrap);
      });
    });
  }
  refreshWpList();
}

function panelTitle(card, title, sub) {
  var h = document.createElement("h3");
  h.textContent = title;
  card.appendChild(h);
  if (sub) {
    var p = document.createElement("p");
    p.className = "muted small";
    p.textContent = sub;
    card.appendChild(p);
  }
}
function lbl(card, text) {
  var d = document.createElement("div");
  d.className = "lbl";
  d.textContent = text;
  card.appendChild(d);
  return d;
}

/* Giờ thời sự: sửa giờ bắt đầu/kết thúc + chọn app thời sự. */
function panelNews(card) {
  panelTitle(card, "Giờ thời sự", "Trong khung này TV chỉ mở được app thời sự.");
  lbl(card, "Bắt đầu");
  var start = document.createElement("input");
  start.type = "time"; start.required = true;
  lbl(card, "Kết thúc");
  var end = document.createElement("input");
  end.type = "time"; end.required = true;
  lbl(card, "App thời sự");
  var sel = document.createElement("select");
  card.appendChild(start);
  card.appendChild(end);
  card.appendChild(sel);
  var save = document.createElement("button");
  save.className = "btn success wide";
  save.textContent = "Lưu giờ thời sự";
  card.appendChild(save);
  getJson("/api/settings").then(function (s) {
    if (!s) return;
    settingsData = s;
    start.value = fmtClock(s.newsStartMin);
    end.value = fmtClock(s.newsEndMin);
    getJson("/api/newsapps").then(function (d) {
      var apps = d ? d.apps || [] : [];
      var opt0 = document.createElement("option");
      opt0.value = "";
      opt0.textContent = "— Chưa chọn app thời sự —";
      sel.appendChild(opt0);
      apps.forEach(function (a) {
        var o = document.createElement("option");
        o.value = a.pkg;
        o.textContent = a.label;
        if (a.pkg === s.newsPkg) o.selected = true;
        sel.appendChild(o);
      });
    });
  });
  save.addEventListener("click", function () {
    if (!start.value || !end.value) { toast("Giờ thời sự không hợp lệ.", false); return; }
    postForm("/api/settings-news", { start: start.value, end: end.value, newsPkg: sel.value })
      .then(function (ok) {
        toast(ok ? "Đã lưu giờ thời sự." : "Không lưu được, thử lại nhé.", ok);
        if (ok) { loadSettings(); }
      });
  });
}

/* Giới hạn từng app: danh sách app + quy tắc. */
function panelAppRules(card) {
  panelTitle(card, "Giới hạn từng app", "Chọn quy tắc cho từng app — đổi là lưu ngay.");
  var list = document.createElement("div");
  list.innerHTML = '<p class="muted">Đang tải…</p>';
  card.appendChild(list);
  getJson("/api/apprules").then(function (d) {
    list.innerHTML = "";
    var apps = d ? d.apps || [] : [];
    if (!apps.length) { list.innerHTML = '<p class="muted">Không tìm thấy app nào.</p>'; return; }
    apps.forEach(function (a) {
      var row = document.createElement("div");
      row.className = "rowcard";
      var main = document.createElement("div");
      main.className = "rowmain";
      main.innerHTML = "<b>" + esc(a.label) + "</b>";
      var sub = document.createElement("div");
      sub.className = "rowsub";
      sub.textContent = "Đã xem hôm nay: " + a.todayMin + " phút";
      var modeSel = document.createElement("select");
      modeSel.setAttribute("aria-label", "Quy tắc cho " + a.label);
      APP_RULE_MODES.forEach(function (m) {
        var o = document.createElement("option");
        o.value = m[0];
        o.textContent = m[1];
        if (m[0] === a.mode) o.selected = true;
        modeSel.appendChild(o);
      });
      var minSel = document.createElement("select");
      minSel.setAttribute("aria-label", "Số phút giới hạn cho " + a.label);
      APP_RULE_MINUTES.forEach(function (m) {
        var o = document.createElement("option");
        o.value = m;
        o.textContent = m + " phút";
        if (m === a.minutes) o.selected = true;
        minSel.appendChild(o);
      });
      minSel.style.display = a.mode === "limit" ? "" : "none";
      var apply = function () {
        var mode = modeSel.value;
        minSel.style.display = mode === "limit" ? "" : "none";
        postJson("/api/settings-apprule", {
          pkg: a.pkg, mode: mode, minutes: mode === "limit" ? minSel.value : 0
        }).then(function (res) {
          toast(res.ok ? "Đã lưu quy tắc cho " + a.label + "." : "Không lưu được, thử lại nhé.", !!res.ok);
        });
      };
      modeSel.addEventListener("change", apply);
      minSel.addEventListener("change", apply);
      row.appendChild(main);
      row.appendChild(sub);
      row.appendChild(modeSel);
      row.appendChild(minSel);
      list.appendChild(row);
    });
  });
}

/* Màn hình khóa: GIỮ hành vi 2.1 — xem trước trực quan 2 cột, sửa lời nhắc/
   chọn ảnh chỉ đổi bản xem trước, bấm "Áp dụng lên TV" mới đẩy lên TV. */
function panelLockScreen(card) {
  panelTitle(card, "Màn hình khóa", "Xem trước — màn hình khóa sẽ hiện thế này trên TV (bản xem trước gần đúng).");
  stagedImg = null;
  var preview = document.createElement("div");
  preview.className = "lock-preview";
  var keysHtml = KEY_ROWS.map(function (row) {
    return '<div class="keyrow">' + row.map(function (k) {
      return '<div class="key">' + esc(k) + "</div>";
    }).join("") + "</div>";
  }).join("");
  preview.innerHTML =
    '<div class="pv-left">' +
      '<img id="lockPreviewImg" style="display:none" alt="Ảnh nền màn hình khóa">' +
      '<div id="lockPreviewMsg" class="pv-msg"></div>' +
    "</div>" +
    '<div class="pv-right"><div class="pv-hint">Nhập mã PIN để mở khóa</div>' + keysHtml + "</div>";
  card.appendChild(preview);
  lbl(card, "Lời nhắc (hiện cùng ảnh nền bên trái)");
  var msgInput = document.createElement("input");
  msgInput.type = "text";
  msgInput.maxLength = 140;
  msgInput.id = "lockMsgInput";
  card.appendChild(msgInput);
  lbl(card, "Ảnh nền");
  var bgState = document.createElement("p");
  bgState.className = "muted small";
  card.appendChild(bgState);
  var fileInput = document.createElement("input");
  fileInput.type = "file";
  fileInput.accept = "image/*";
  card.appendChild(fileInput);
  var note = document.createElement("p");
  note.className = "muted small";
  note.textContent = "Sửa lời nhắc / chọn ảnh xong, xem trước ở trên rồi bấm Áp dụng — chỉ lúc đó TV mới đổi.";
  card.appendChild(note);
  var applyBtn = document.createElement("button");
  applyBtn.className = "btn success wide";
  applyBtn.textContent = "Áp dụng lên TV";
  card.appendChild(applyBtn);
  var removeBtn = document.createElement("button");
  removeBtn.className = "btn danger wide";
  removeBtn.textContent = "Xóa ảnh nền riêng";
  card.appendChild(removeBtn);

  var prevMsg = preview.querySelector("#lockPreviewMsg");
  var prevImg = preview.querySelector("#lockPreviewImg");
  msgInput.addEventListener("input", function () { prevMsg.textContent = msgInput.value; });
  fileInput.addEventListener("change", function () {
    var f = fileInput.files[0];
    if (!f) return;
    var r = new FileReader();
    r.onload = function () {
      stagedImg = r.result;
      prevImg.src = r.result;
      prevImg.style.display = "block";
    };
    r.readAsDataURL(f);
  });
  getJson("/api/settings").then(function (s) {
    if (!s) return;
    settingsData = s;
    msgInput.value = s.lockMessage || "";
    prevMsg.textContent = s.lockMessage || "";
    bgState.textContent = s.hasLockBg ? "Đã đặt ảnh nền riêng." : "Chưa có ảnh nền riêng — đang dùng nền mặc định.";
    if (s.hasLockBg) {
      prevImg.src = "/api/lockbg";
      prevImg.style.display = "block";
    }
  });
  applyBtn.addEventListener("click", function () {
    var msg = msgInput.value.trim();
    if (msg.length < 1 || msg.length > 140) { toast("Lời nhắc từ 1–140 ký tự.", false); return; }
    applyBtn.disabled = true;
    applyBtn.textContent = "Đang áp dụng...";
    postForm("/api/settings-lockmsg", { message: msg }).then(function (ok) {
      if (!ok) {
        toast("Không lưu được lời nhắc, thử lại nhé.", false);
        applyBtn.disabled = false;
        applyBtn.textContent = "Áp dụng lên TV";
        return;
      }
      if (!stagedImg) {
        toast("Đã áp dụng lên TV.", true);
        applyBtn.disabled = false;
        applyBtn.textContent = "Áp dụng lên TV";
        return;
      }
      postFormJson("/api/settings-lockbg", { img: stagedImg }).then(function (d) {
        var ok2 = !!(d && d.ok);
        toast(ok2 ? "Đã áp dụng lên TV." : ((d && d.error) || "Không tải được ảnh, thử lại nhé."), ok2);
        if (ok2) stagedImg = null;
        applyBtn.disabled = false;
        applyBtn.textContent = "Áp dụng lên TV";
      });
    });
  });
  removeBtn.addEventListener("click", function () {
    postForm("/api/settings-lockbg-remove", {}).then(function (ok) {
      toast(ok ? "Đã xóa ảnh nền riêng." : "Không xóa được, thử lại nhé.", ok);
      if (ok) {
        prevImg.style.display = "none";
        prevImg.removeAttribute("src");
        bgState.textContent = "Chưa có ảnh nền riêng — đang dùng nền mặc định.";
        loadSettings();
      }
    });
  });
}

/* Mã PIN: đổi PIN mới 4–6 số. */
function panelPin(card) {
  panelTitle(card, "Mã PIN", "Đổi mã PIN mở khóa TV — không cần nhập PIN cũ.");
  lbl(card, "Mã PIN mới (4–6 số)");
  var input = document.createElement("input");
  input.type = "password";
  input.inputMode = "numeric";
  input.maxLength = 6;
  input.autocomplete = "off";
  input.placeholder = "Nhập mã PIN mới (4–6 số)";
  card.appendChild(input);
  var save = document.createElement("button");
  save.className = "btn accent wide";
  save.textContent = "Đổi PIN";
  card.appendChild(save);
  save.addEventListener("click", function () {
    var pin = input.value.trim();
    if (!/^\d{4,6}$/.test(pin)) { toast("Mã PIN mới phải là 4–6 chữ số.", false); return; }
    postForm("/api/change-pin", { newPin: pin }).then(function (ok) {
      toast(ok ? "Đã đổi mã PIN thành công." : "Không đổi được, thử lại nhé.", ok);
      if (ok) input.value = "";
    });
  });
}

/* Mã PIN web (v2.3): đổi mã mới + đăng xuất. Quên mã thì xem/đặt lại
   trên TV (Cài đặt → Mã PIN web) hoặc bằng app Cài TV. */
function panelWebPin(card) {
  panelTitle(card, "Mã PIN web", "Mã mở trang điều khiển trên điện thoại — khác mã PIN khóa TV. Quên mã thì xem/đặt lại trên TV: Cài đặt → Mã PIN web.");
  lbl(card, "Mã PIN web mới (4–6 số)");
  var input = document.createElement("input");
  input.type = "password";
  input.inputMode = "numeric";
  input.maxLength = 6;
  input.autocomplete = "off";
  input.placeholder = "Nhập mã PIN web mới (4–6 số)";
  card.appendChild(input);
  var save = document.createElement("button");
  save.className = "btn accent wide";
  save.textContent = "Đổi mã PIN web";
  card.appendChild(save);
  save.addEventListener("click", function () {
    var pin = input.value.trim();
    if (!/^\d{4,6}$/.test(pin)) { toast("Mã PIN web mới phải là 4–6 chữ số.", false); return; }
    postJson("/api/webpin-change", { newPin: pin }).then(function (res) {
      toast(res && res.ok ? "Đã đổi mã PIN web." : ((res && res.error) || "Không đổi được, thử lại nhé."), !!(res && res.ok));
      if (res && res.ok) input.value = "";
    });
  });
  var out = document.createElement("button");
  out.className = "btn danger wide";
  out.textContent = "Đăng xuất";
  card.appendChild(out);
  out.addEventListener("click", doLogout);
}

/* Phút thưởng: 4 nút + quỹ thưởng hiện tại. */
function panelBonus(card) {
  panelTitle(card, "Phút thưởng", "Cộng vào quỹ thưởng — chỉ trừ khi đã hết giờ ngày, phút chưa dùng hết dồn sang hôm sau.");
  var pool = document.createElement("p");
  pool.className = "rowmain";
  card.appendChild(pool);
  var refreshPool = function () {
    pool.textContent = "Quỹ thưởng còn: " + (statusData ? statusData.bonusMin : 0) + " phút";
  };
  refreshPool();
  var chips = document.createElement("div");
  chips.className = "chips";
  [5, 10, 15, 30].forEach(function (m) {
    var b = document.createElement("button");
    b.className = "btn ghost";
    b.textContent = "+" + m;
    b.addEventListener("click", function () {
      postForm("/api/bonus", { minutes: m }).then(function (ok) {
        toast(ok ? "Đã thưởng thêm " + m + " phút." : "Không thưởng được, thử lại nhé.", ok);
        if (ok) loadStatus();
        setTimeout(refreshPool, 600);
      });
    });
    chips.appendChild(b);
  });
  card.appendChild(chips);
}

/* Trợ năng: trạng thái + bật/tắt chống tắt. */
function panelAccess(card) {
  panelTitle(card, "Trợ năng", "Bật Trợ năng thực hiện trên TV (Cài đặt → Trợ năng).");
  var body = document.createElement("div");
  body.innerHTML = '<p class="muted">Đang tải…</p>';
  card.appendChild(body);
  var toggle = document.createElement("button");
  toggle.className = "btn wide";
  toggle.style.display = "none";
  card.appendChild(toggle);
  var render = function (s) {
    var a11y = s.a11yEnabled ? "ĐANG BẬT" : "ĐANG TẮT";
    var wss = s.hasWss ? "đã cấp — app tự bật lại được"
      : "CHƯA cấp — cài lại Khóa TV bằng app Cài TV 1.5 để cấp quyền";
    body.innerHTML =
      '<div class="info-row"><span class="muted">Trợ năng hiện tại</span><b>' + a11y + "</b></div>" +
      '<div class="info-row"><span class="muted">Quyền ghi cài đặt hệ thống</span><b>' + esc(wss) + "</b></div>" +
      '<p class="muted small">' + (s.antiDisable
        ? "Đang bật: ai tắt Trợ năng trong Cài đặt TV thì app tự bật lại trong 1 giây."
        : "Đang tắt: Trợ năng có thể tắt như bình thường.") + "</p>";
    toggle.style.display = "";
    toggle.textContent = s.antiDisable ? "Tắt chống tắt" : "Bật chống tắt";
    toggle.className = "btn wide " + (s.antiDisable ? "danger" : "success");
    toggle.onclick = function () {
      postForm("/api/settings-antidisable", { enabled: s.antiDisable ? "off" : "on" })
        .then(function (ok) {
          toast(ok ? (s.antiDisable ? "Đã tắt chống tắt Trợ năng." : "Đã bật chống tắt Trợ năng.")
            : "Không đổi được, thử lại nhé.", ok);
          if (ok) {
            getJson("/api/settings").then(function (ns) {
              if (ns) { settingsData = ns; render(ns); }
            });
          }
        });
    };
  };
  getJson("/api/settings").then(function (s) {
    if (s) { settingsData = s; render(s); }
    else body.innerHTML = '<p class="muted">Không tải được trạng thái.</p>';
  });
}

/* --- Khởi tạo --- */
function buildAddChips() {
  var box = $("addChips");
  [5, 10, 15, 20, 30].forEach(function (m) {
    var b = document.createElement("button");
    b.className = "btn ghost";
    b.textContent = "+" + m;
    b.addEventListener("click", function () {
      postForm("/api/add", { minutes: m }).then(function (ok) {
        toast(ok ? "Đã cho thêm " + m + " phút." : "Không cho thêm được, thử lại nhé.", ok);
        if (ok) loadStatus();
      });
    });
    box.appendChild(b);
  });
}
/* --- Mã thưởng cho con (v2.8; v2.9: tab riêng + giới hạn số mã/ngày) --- */
function loadRewardCodes() {
  getJson("/api/reward-codes").then(function (d) {
    if (!d) { if (lastWas401) showLoginScreen(); return; }
    renderRewardCodes(d);
  });
}
function renderRewardCodes(d) {
  var list = d.codes || [];
  var limit = (d.limit == null ? 0 : d.limit);
  var used = (d.usedToday == null ? 0 : d.usedToday);
  var input = $("rewardLimitInput");
  if (input) input.value = limit;
  var st = $("rewardLimitStatus");
  if (st) {
    st.textContent = limit > 0
      ? "Hôm nay đã nhập " + used + " / " + limit + " mã."
      : "Hôm nay đã nhập " + used + " mã (không giới hạn).";
  }
  var el = $("rewardList");
  el.innerHTML = "";
  if (!list.length) {
    el.innerHTML = '<p class="muted small">Chưa có mã thưởng nào đang còn hiệu lực.</p>';
    return;
  }
  list.forEach(function (c) {
    var row = document.createElement("div");
    row.className = "rowcard";
    var main = document.createElement("div");
    main.className = "rowmain";
    main.innerHTML = "<b style=\"font-size:24px;letter-spacing:6px\">" + esc(c.code) + "</b> · +" + c.minutes + " phút";
    var acts = document.createElement("div");
    acts.className = "rowactions";
    var delBtn = document.createElement("button");
    delBtn.className = "btn small danger";
    delBtn.textContent = "Xóa";
    delBtn.addEventListener("click", function () {
      if (!confirm("Thu hồi mã này? Con sẽ không dùng được mã này nữa.")) return;
      postJson("/api/reward-code-delete", { code: c.code }).then(function (res) {
        toast(res && res.ok ? "Đã thu hồi mã." : "Không xóa được, thử lại nhé.", !!(res && res.ok));
        if (res && res.ok) loadRewardCodes();
      });
    });
    acts.appendChild(delBtn);
    row.appendChild(main);
    row.appendChild(acts);
    el.appendChild(row);
  });
}
function generateRewardCode(minutes) {
  postJson("/api/reward-code-generate", { minutes: minutes }).then(function (res) {
    if (res && res.ok) {
      var box = $("newRewardCode");
      box.innerHTML = esc(res.code) + '<small>Mã +' + res.minutes + ' phút — đưa mã này cho con nhập trên màn hình khóa của TV.</small>';
      box.hidden = false;
      toast("Đã tạo mã +" + res.minutes + " phút.", true);
      loadRewardCodes();
      loadStatus();
    } else {
      toast((res && res.error) || "Không tạo được mã, thử lại nhé.", false);
    }
  });
}
function init() {
  buildTabbar();
  buildAddChips();
  buildDayBoxes();
  document.querySelectorAll(".tabbtn").forEach(function (b) {
    b.classList.toggle("active", b.dataset.tab === "Hôm nay");
  });
  $("btnLock").addEventListener("click", function () {
    postForm("/api/lock", {}).then(function (ok) {
      toast(ok ? "Đã khóa TV ngay." : "Không khóa được, thử lại nhé.", ok);
      if (ok) loadStatus();
    });
  });
  $("btnUnlock").addEventListener("click", function () {
    postForm("/api/unlock", {}).then(function (ok) {
      toast(ok ? "Đã mở khóa TV trong 60 phút." : "Không mở được, thử lại nhé.", ok);
      if (ok) loadStatus();
    });
  });
  /* v2.7: gửi tin nhắn lên TV (banner overlay, tự tắt sau 5 giây). */
  $("btnSendMsg").addEventListener("click", function () {
    var t = $("msgText").value.trim();
    if (!t) { toast("Nhập tin nhắn trước đã nhé.", false); return; }
    postJson("/api/message", { text: t }).then(function (res) {
      if (res && res.ok) {
        toast("Đã gửi lên TV.", true);
        $("msgText").value = "";
      } else {
        toast((res && res.error) || "Không gửi được, thử lại nhé.", false);
      }
    });
  });
  $("msgText").addEventListener("keydown", function (e) {
    if (e.key === "Enter") $("btnSendMsg").click();
  });
  /* v2.8: tạo mã thưởng cho con. */
  $("btnReward5").addEventListener("click", function () { generateRewardCode(5); });
  $("btnReward10").addEventListener("click", function () { generateRewardCode(10); });
  /* v2.9: lưu số mã được nhập mỗi ngày (0 = không giới hạn). */
  $("btnRewardLimitSave").addEventListener("click", function () {
    var v = parseInt($("rewardLimitInput").value, 10);
    if (isNaN(v) || v < 0) v = 0;
    postJson("/api/settings-reward-limit", { limit: v }).then(function (res) {
      toast(res && res.ok ? "Đã lưu số mã mỗi ngày: " + (v === 0 ? "không giới hạn." : v + " mã.")
        : "Không lưu được, thử lại nhé.", !!(res && res.ok));
      if (res && res.ok) loadRewardCodes();
    });
  });
  /* v2.9: chụp màn hình TV — ảnh JPEG trả về hiện ngay dưới nút. */
  var doScreenshot = function () {
    var btn = $("btnScreenshot");
    var again = $("btnScreenshotAgain");
    var err = $("screenshotError");
    var img = $("screenshotImg");
    var tm = $("screenshotTime");
    btn.disabled = true;
    btn.textContent = "Đang chụp...";
    err.hidden = true;
    fetch("/api/screenshot").then(function (r) {
      if (r.status === 401) { showLoginScreen(); return null; }
      if (!r.ok) {
        return r.json().catch(function () { return { error: "no_permission", hint: "" }; })
          .then(function (j) { throw j; });
      }
      return r.blob();
    }).then(function (blob) {
      if (!blob) return;
      if (img.src && img.src.indexOf("blob:") === 0) URL.revokeObjectURL(img.src);
      img.src = URL.createObjectURL(blob);
      img.style.display = "block";
      var now = new Date();
      var pad = function (n) { return ("0" + n).slice(-2); };
      tm.textContent = "Chụp lúc " + pad(now.getHours()) + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds());
      tm.hidden = false;
      again.hidden = false;
      toast("Đã chụp màn hình TV.", true);
    }).catch(function (j) {
      var msg = (j && j.hint) ? j.hint : "Chưa cấp quyền chụp màn hình trên TV.";
      err.textContent = msg;
      err.hidden = false;
      toast("Chưa chụp được màn hình TV.", false);
    }).then(function () {
      btn.disabled = false;
      btn.textContent = "Chụp màn hình TV";
    });
  };
  $("btnScreenshot").addEventListener("click", doScreenshot);
  $("btnScreenshotAgain").addEventListener("click", doScreenshot);
  $("schedSave").addEventListener("click", saveSchedule);
  $("schedCancelEdit").addEventListener("click", resetScheduleForm);
  $("btnLogin").addEventListener("click", doLogin);
  $("loginPin").addEventListener("keydown", function (e) { if (e.key === "Enter") doLogin(); });
  $("forgotLink").addEventListener("click", function (e) {
    e.preventDefault();
    $("forgotHelp").hidden = !$("forgotHelp").hidden;
  });
  $("btnLogout").addEventListener("click", doLogout);
  // v2.3: kiểm tra phiên trước — chưa đăng nhập thì hiện màn hình PIN.
  getJson("/api/status").then(function (s) {
    if (!s) { showLoginScreen(); return; }
    hideLoginScreen();
    statusData = s;
    renderHome(s);
    loadSettings();
    loadReport();
    loadAppLabels();
  });
  setInterval(function () { if (authed) loadStatus(); }, 5000);
}
document.addEventListener("DOMContentLoaded", init);
