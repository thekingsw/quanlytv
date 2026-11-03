/* Bắt lỗi JS hiện lên màn hình để debug */
window.addEventListener("error", function (e) {
  var d = document.getElementById("jsError");
  if (!d) {
    d = document.createElement("div");
    d.id = "jsError";
    d.style.cssText = "position:fixed;bottom:0;left:0;right:0;background:#7f1d1d;color:#fff;padding:8px;font-size:12px;z-index:9999;white-space:pre-wrap;";
    document.body.appendChild(d);
  }
  d.textContent = "Lỗi: " + (e.message || e.error) + " @ " + (e.filename || "").split("/").pop() + ":" + e.lineno;
});
"use strict";
/* Khóa TV — web điện thoại v2. 4 tab dưới: Hôm nay / Quy tắc / Màn hình chính / Cài đặt.
   Tab Quy tắc gộp Lịch + Giới hạn cũ, mỗi dòng hiện giá trị hiện tại, bấm vào mở
   chi tiết trong trang (route theo hash #rules/<id>). Không tải lại trang:
   mọi thao tác gọi fetch POST rồi cập nhật tại chỗ + toast. */

/* --- Cấu hình --- */
var TABS = ["Hôm nay", "Quy tắc", "Màn hình chính", "Cài đặt"];
var TAB_IDS = { "Hôm nay": "tab-today", "Quy tắc": "tab-rules", "Màn hình chính": "tab-home", "Cài đặt": "tab-settings" };
/* v2: bỏ emoji ở tab, dùng icon SVG inline đơn giản. */
var TAB_ICONS = {
  "Hôm nay": '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true"><path d="M12 3l9 8h-3v9h-4v-6H10v6H6v-9H3z"/></svg>',
  "Quy tắc": '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true"><path d="M4 6h16v2H4zM4 11h16v2H4zM4 16h16v2H4z"/></svg>',
  "Màn hình chính": '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true"><path d="M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm7 14h6v2H6v-2h5z"/></svg>',
  "Cài đặt": '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true"><path d="M12 8.5A3.5 3.5 0 1 0 12 15.5 3.5 3.5 0 0 0 12 8.5zm9 3.5c0 .4-.03.8-.1 1.2l2 1.6-2 3.4-2.4-1a7 7 0 0 1-2 1.2L16 20H8l-.5-2.6a7 7 0 0 1-2-1.2l-2.4 1-2-3.4 2-1.6a7 7 0 0 1 0-2.4l-2-1.6 2-3.4 2.4 1a7 7 0 0 1 2-1.2L8 4h8l.5 2.6a7 7 0 0 1 2 1.2l2.4-1 2 3.4-2 1.6c.07.4.1.8.1 1.2z"/></svg>'
};

/* 3 nhóm — đồng bộ với nhóm cài đặt trên TV (v2). */
var SETTING_GROUPS = [
  { id: "lock", title: "Khóa TV", desc: "Lịch khóa, giới hạn, mã PIN", items: [
    { id: "schedules",    title: "Lịch khóa",         desc: "Ngày + giờ khóa, khóa qua đêm" },
    { id: "daily_limits", title: "Giới hạn ngày",     desc: "Giới hạn xem từng ngày trong tuần" },
    { id: "news_slot",    title: "Giờ thời sự",       desc: "Khung giờ chỉ xem thời sự" },
    { id: "app_rules",    title: "Giới hạn từng app", desc: "Chặn / giới hạn riêng từng app" },
    { id: "lock_screen",  title: "Màn hình khóa",     desc: "Lời nhắc + ảnh nền" },
    { id: "bonus",        title: "Phút thưởng",       desc: "Quỹ phút thưởng cho con" }
  ]},
  { id: "launcher", title: "Màn hình chính", desc: "Ảnh nền, widget, thông báo", items: [
    { id: "home_wallpaper", title: "Ảnh nền", desc: "Tải ảnh từ điện thoại lên TV" },
    { id: "widgets", title: "Widget", desc: "Bật/tắt các widget (đồng bộ với TV)" },
    { id: "countdown", title: "Đếm ngược", desc: "Mốc đếm ngược trên widget" },
    { id: "push", title: "Thông báo đẩy", desc: "Khung giờ + chế độ popup" },
    { id: "launcher_style", title: "Kiểu icon & thời tiết", desc: "Kiểu icon, thành phố thời tiết" },
    { id: "hidden_apps", title: "Ứng dụng đã ẩn", desc: "Hiện lại app đã ẩn" }
  ]},
  { id: "security", title: "Bảo mật & công cụ", desc: "Mã PIN, trợ năng, chụp màn hình", items: [
    { id: "pin",        title: "Mã PIN TV",          desc: "Đổi mã PIN mở khóa TV" },
    { id: "webpin",     title: "Mã PIN web",         desc: "Đổi mã PIN vào web trên điện thoại" },
    { id: "reward_codes", title: "Mã thưởng",        desc: "Tạo mã thưởng cho con" },
    { id: "access",     title: "Trợ năng & chống tắt", desc: "Trạng thái trợ năng trên TV" },
    { id: "screenshot", title: "Chụp màn hình TV",   desc: "Chụp khung hình TV đang hiện" }
  ]}
];

/* Các mục tab Quy tắc — mỗi dòng hiện giá trị hiện tại bên phải. */
var RULE_DEFS = [
  { id: "schedules",    title: "Lịch khóa",         desc: "Ngày + giờ khóa, khóa qua đêm" },
  { id: "news_slot",    title: "Giờ thời sự",       desc: "Khung giờ chỉ xem thời sự" },
  { id: "daily_limits", title: "Giới hạn ngày",     desc: "Giới hạn xem từng ngày trong tuần" },
  { id: "app_rules",    title: "Giới hạn từng app", desc: "Chặn / giới hạn riêng từng app" },
  { id: "bonus",        title: "Phút thưởng",       desc: "Quỹ phút thưởng cho con" },
  { id: "lock_screen",  title: "Màn hình khóa",     desc: "Lời nhắc + ảnh nền" },
  { id: "rewards",      title: "Mã thưởng",         desc: "Mã 6 số cộng phút cho con" }
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
var reloadTodayRewards = null;

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
/* v2: "1 giờ 12 phút" đầy đủ chữ (giống TV) — dùng cho thẻ trạng thái Hôm nay. */
function fmtLong(min) {
  if (min == null || min < 0) return "Không giới hạn";
  var h = Math.floor(min / 60), r = min % 60;
  if (h <= 0) return min + " phút";
  return r ? h + " giờ " + r + " phút" : h + " giờ";
}

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
  if (name === "Hôm nay") { loadStatus(); loadReport(); if (reloadTodayRewards) reloadTodayRewards(); }
  if (name === "Quy tắc") renderRulesList();
  if (name === "Màn hình chính") renderHomeTab();
  if (name === "Cài đặt") renderSettingsTab();
  window.scrollTo(0, 0);
}

/* --- Trạng thái kết nối TV (v2: thay nhãn "Phòng khách" cứng) --- */
function setConn(ok) {
  $("connStatus").innerHTML = '<span class="dot' + (ok ? " on" : "") + '"></span> ' +
    (ok ? "Đã kết nối TV" : "Đang kết nối…");
}

/* --- Đăng nhập mã PIN web (v2.3) --- */
var authed = false;
function showLoginScreen(msg) {
  authed = false;
  $("loginScreen").hidden = false;
  document.querySelector("main").style.display = "none";
  $("tabbar").style.display = "none";
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
  /* v2: "1 giờ 12 phút" đầy đủ chữ, không viết "1g 12p". */
  $("hero").textContent = fmtLong(s.remainingMin);
  $("subline").textContent = "còn lại hôm nay · đã xem " + fmtLong(s.usedTodayMin) +
    " / " + fmtLong(s.dailyLimitMin);
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
    setConn(true);
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
/* v2: báo cáo thu gọn — chỉ dòng tóm tắt, bấm "Xem 7 ngày" mới mở chi tiết. */
function loadReport() {
  getJson("/api/report").then(function (d) {
    if (!d) return;
    renderReport(d);
  });
}
function renderReport(d) {
  var today = d.today || {};
  $("reportSummary").textContent = "Báo cáo hôm nay: " + (today.total || 0) + " phút";
  var el = $("reportBody");
  var html = "";
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
function toggleReport() {
  var el = $("reportBody");
  var btn = $("btnReport7");
  var open = el.hidden;
  el.hidden = !open;
  btn.innerHTML = open ? "‹ Thu gọn" : "Xem 7 ngày ›";
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

/* --- Mã thưởng cho con (v2: thẻ thu gọn trên Hôm nay + bản đầy đủ trong Quy tắc) --- */
/* Dựng UI mã thưởng vào box (không dùng id toàn cục để tránh trùng khi mở
   đồng thời ở Hôm nay và chi tiết Quy tắc). Trả về hàm reload. */
function buildRewardsUI(box, mode) {
  // mode "full": tạo mã + giới hạn/ngày + danh sách (chỉ dùng ở tab Cài đặt).
  // mode "list": chỉ hiện các mã còn lại, không tạo (Hôm nay, Quy tắc).
  var canCreate = (mode === "full");
  box.innerHTML = "";
  var b5 = null, b10 = null, newBox = null;
  if (canCreate) {
    var btnRow = document.createElement("div");
    btnRow.className = "row2";
    b5 = document.createElement("button");
    b5.className = "btn accent"; b5.textContent = "+5′";
    b10 = document.createElement("button");
    b10.className = "btn accent"; b10.textContent = "+10′";
    btnRow.appendChild(b5); btnRow.appendChild(b10);
    box.appendChild(btnRow);
    newBox = document.createElement("div");
    newBox.className = "reward-code"; newBox.hidden = true;
    box.appendChild(newBox);
  }
  var listEl = document.createElement("div");
  box.appendChild(listEl);
  var expanded = false;
  var limInput = null, limStatus = null;
  if (canCreate) {
    var limWrap = document.createElement("div");
    limWrap.innerHTML = '<div class="lbl" style="margin-top:14px">Số mã được nhập mỗi ngày (0 = không giới hạn)</div>';
    var limRow = document.createElement("div");
    limRow.className = "msg-row";
    limInput = document.createElement("input");
    limInput.type = "number"; limInput.min = "0"; limInput.step = "1";
    limInput.inputMode = "numeric"; limInput.value = "0";
    var limSave = document.createElement("button");
    limSave.className = "btn success"; limSave.textContent = "Lưu";
    limRow.appendChild(limInput); limRow.appendChild(limSave);
    limStatus = document.createElement("p");
    limStatus.className = "muted small";
    limWrap.appendChild(limRow); limWrap.appendChild(limStatus);
    box.appendChild(limWrap);
    limSave.addEventListener("click", function () {
      var v = parseInt(limInput.value, 10);
      if (isNaN(v) || v < 0) v = 0;
      postJson("/api/settings-reward-limit", { limit: v }).then(function (res) {
        toast(res && res.ok ? "Đã lưu số mã mỗi ngày: " + (v === 0 ? "không giới hạn." : v + " mã.")
          : "Không lưu được, thử lại nhé.", !!(res && res.ok));
        if (res && res.ok) reload();
      });
    });
  }
  var render = function (d) {
    var list = d.codes || [];
    listEl.innerHTML = "";
    if (!list.length) {
      listEl.innerHTML = '<p class="muted small">Chưa có mã thưởng nào đang còn hiệu lực.</p>';
    } else {
      var show = expanded ? list : list.slice(0, 3);
      show.forEach(function (c) {
        var row = document.createElement("div");
        row.className = "rw-row";
        var left = document.createElement("div");
        left.className = "rw-left";
        left.innerHTML = '<b class="rw-code">' + esc(c.code) + '</b><span class="muted small"> +' +
          c.minutes + " phút</span>";
        var del = document.createElement("button");
        del.className = "btn small danger";
        del.textContent = "Xóa";
        del.addEventListener("click", function () {
          if (!confirm("Thu hồi mã này? Con sẽ không dùng được mã này nữa.")) return;
          postJson("/api/reward-code-delete", { code: c.code }).then(function (res) {
            toast(res && res.ok ? "Đã thu hồi mã." : "Không xóa được, thử lại nhé.", !!(res && res.ok));
            if (res && res.ok) reload();
          });
        });
        row.appendChild(left); row.appendChild(del);
        listEl.appendChild(row);
      });
      if (list.length > 3) {
        var more = document.createElement("button");
        more.className = "btn ghost wide";
        more.textContent = expanded ? "Thu gọn" : "Xem thêm (" + (list.length - 3) + ")";
        more.addEventListener("click", function () { expanded = !expanded; reload(); });
        listEl.appendChild(more);
      }
    }
    if (canCreate && limInput) {
      var limit = (d.limit == null ? 0 : d.limit);
      var used = (d.usedToday == null ? 0 : d.usedToday);
      limInput.value = limit;
      limStatus.textContent = limit > 0
        ? "Hôm nay đã nhập " + used + " / " + limit + " mã."
        : "Hôm nay đã nhập " + used + " mã (không giới hạn).";
    }
  };
  var reload = function () {
    getJson("/api/reward-codes").then(function (d) {
      if (!d) { if (lastWas401) showLoginScreen(); return; }
      render(d);
    });
  };
  var genCode = function (minutes) {
    postJson("/api/reward-code-generate", { minutes: minutes }).then(function (res) {
      if (res && res.ok) {
        newBox.innerHTML = esc(res.code) + '<small>Mã +' + res.minutes +
          ' phút — đưa mã này cho con nhập trên màn hình khóa của TV.</small>';
        newBox.hidden = false;
        toast("Đã tạo mã +" + res.minutes + " phút.", true);
        reload();
        loadStatus();
      } else {
        toast((res && res.error) || "Không tạo được mã, thử lại nhé.", false);
      }
    });
  };
  if (canCreate) {
    b5.addEventListener("click", function () { genCode(5); });
    b10.addEventListener("click", function () { genCode(10); });
  }
  reload();
  return reload;
}

/* --- Cộng thêm giờ (chip +5..+30) --- */
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

/* --- Tab Quy tắc (v2): gộp Lịch + Giới hạn cũ + các mục quy tắc khác --- */
function renderRulesList() {
  var el = $("rulesList");
  el.innerHTML = "";
  RULE_DEFS.forEach(function (r) {
    var row = document.createElement("button");
    row.className = "setting-row rule-row";
    row.innerHTML = "<span><b>" + esc(r.title) + '</b><br><span class="muted small">' +
      esc(r.desc) + '</span></span><span class="rule-value" id="rv-' + r.id +
      '">…</span><span class="chev">›</span>';
    row.addEventListener("click", function () { openRule(r.id); });
    el.appendChild(row);
  });
  el.hidden = false;
  var det = $("rulesDetail");
  det.hidden = true;
  det.innerHTML = "";
  loadRuleValues();
}
function setRuleValue(id, text) {
  var el = $("rv-" + id);
  if (el) el.textContent = text;
}
/* JS getDay(): 0=CN..6=T7; server dùng 0=T2..6=CN. */
function serverDay() { return (new Date().getDay() + 6) % 7; }
function loadRuleValues() {
  getJson("/api/schedules").then(function (d) {
    setRuleValue("schedules", ((d && d.schedules) || []).length + " lịch");
  });
  getJson("/api/settings").then(function (s) {
    if (!s) return;
    settingsData = s;
    setRuleValue("news_slot", fmtClock(s.newsStartMin) + " – " + fmtClock(s.newsEndMin));
    setRuleValue("lock_screen", s.hasLockBg ? "Đã đặt ảnh riêng" : "Mặc định");
  });
  getJson("/api/limits").then(function (d) {
    var lim = d && d.limits ? d.limits[serverDay()] : null;
    setRuleValue("daily_limits", lim == null || lim < 0 ? "Không giới hạn" : fmtLong(lim));
  });
  getJson("/api/apprules").then(function (d) {
    var apps = (d && d.apps) || [];
    var n = apps.filter(function (a) { return a.mode && a.mode !== "normal"; }).length;
    setRuleValue("app_rules", n ? n + " app có quy tắc" : "Chưa đặt");
  });
  getJson("/api/reward-codes").then(function (d) {
    setRuleValue("rewards", ((d && d.codes) || []).length + " mã");
  });
  if (statusData) setRuleValue("bonus", (statusData.bonusMin || 0) + " phút");
  else getJson("/api/status").then(function (s) {
    if (s) { statusData = s; setRuleValue("bonus", (s.bonusMin || 0) + " phút"); }
  });
}
/* Mở chi tiết trong trang, route theo hash #rules/<id>. */
var suppressHash = false;
function openRule(id) {
  suppressHash = true;
  location.hash = "rules/" + id;
  setTimeout(function () { suppressHash = false; }, 0);
  showRuleDetail(id);
}
function showRuleDetail(id) {
  var ok = RULE_DEFS.some(function (r) { return r.id === id; });
  if (!ok) return;
  $("rulesList").hidden = true;
  var det = $("rulesDetail");
  det.hidden = false;
  det.innerHTML = "";
  var back = document.createElement("button");
  back.className = "btn ghost wide";
  back.textContent = "← Quy tắc";
  back.addEventListener("click", closeRuleDetail);
  var card = document.createElement("div");
  card.className = "card";
  det.appendChild(back);
  det.appendChild(card);
  buildRuleDetail(id, card);
  card.scrollIntoView({ block: "start" });
}
function closeRuleDetail() {
  suppressHash = true;
  location.hash = "rules";
  setTimeout(function () { suppressHash = false; }, 0);
  renderRulesList();
}
function buildRuleDetail(id, card) {
  if (id === "schedules") return buildScheduleDetail(card);
  if (id === "daily_limits") return buildLimitsDetail(card);
  if (id === "rewards") { buildRewardsUI(card, "list"); return; }
  dispatchPanel(id, card);
}
window.addEventListener("hashchange", function () {
  if (suppressHash) return;
  var h = location.hash || "";
  var m = h.match(/^#rules\/([a-z_]+)$/);
  if (m) { showTab("Quy tắc"); showRuleDetail(m[1]); }
  else if ((h === "#rules" || h === "") && !$("rulesDetail").hidden) renderRulesList();
});

/* Chi tiết Lịch khóa (chuyển từ tab Lịch cũ sang). */
function buildScheduleDetail(card) {
  panelTitle(card, "Lịch khóa", "Mỗi lịch chọn ngày + giờ khóa từ–đến, gộp được nhiều lịch, khóa qua đêm cũng được.");
  var list = document.createElement("div");
  list.id = "scheduleList";
  card.appendChild(list);
  var form = document.createElement("div");
  form.innerHTML =
    '<h3 id="schedFormTitle">Thêm lịch khóa mới</h3>' +
    '<div class="lbl">Ngày áp dụng</div><div id="schedDays" class="dayboxes"></div>' +
    '<div class="lbl">Bắt đầu khóa</div><input type="time" id="schedStart" value="21:00">' +
    '<div class="lbl">Kết thúc khóa</div><input type="time" id="schedEnd" value="07:00">' +
    '<p class="muted small">Kết thúc sớm hơn bắt đầu = khóa qua đêm. Bắt đầu = kết thúc thì khóa cả ngày.</p>' +
    '<input type="hidden" id="schedId" value="">' +
    '<button id="schedSave" class="btn success wide">Lưu lịch này</button>' +
    '<button id="schedCancelEdit" class="btn ghost wide" hidden>Hủy sửa</button>';
  card.appendChild(form);
  buildDayBoxes();
  loadSchedules();
  $("schedSave").addEventListener("click", saveSchedule);
  $("schedCancelEdit").addEventListener("click", resetScheduleForm);
}
function loadSchedules() {
  getJson("/api/schedules").then(function (d) {
    renderSchedules(d ? d.schedules || [] : []);
  });
}
function renderSchedules(list) {
  var el = $("scheduleList");
  if (!el) return;
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
  if (!box) return;
  box.innerHTML = "";
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

/* Chi tiết Giới hạn ngày (chuyển từ tab Giới hạn cũ sang). */
function buildLimitsDetail(card) {
  panelTitle(card, "Giới hạn xem từng ngày", "Đặt giới hạn xem riêng cho từng ngày trong tuần.");
  var list = document.createElement("div");
  list.id = "limitList";
  card.appendChild(list);
  loadLimits();
}
function loadLimits() {
  getJson("/api/limits").then(function (d) {
    renderLimits(d ? d.limits || {} : {});
  });
}
function renderLimits(limits) {
  var el = $("limitList");
  if (!el) return;
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

/* --- Tab Màn hình chính & Cài đặt (v2): danh sách nhóm + panel chi tiết --- */
function renderSettingGroup(listEl, panelEl, group, backLabel) {
  listEl.innerHTML = "";
  listEl.hidden = false;
  panelEl.hidden = true;
  panelEl.innerHTML = "";
  group.items.forEach(function (item) {
    var row = document.createElement("button");
    row.className = "setting-row";
    row.innerHTML = "<span><b>" + esc(item.title) + '</b><br><span class="muted small">' +
      esc(item.desc) + '</span></span><span class="chev">›</span>';
    row.addEventListener("click", function () { openSettingPanel(listEl, panelEl, item.id, backLabel); });
    listEl.appendChild(row);
  });
}
function openSettingPanel(listEl, panelEl, id, backLabel) {
  listEl.hidden = true;
  panelEl.hidden = false;
  panelEl.innerHTML = "";
  var back = document.createElement("button");
  back.className = "btn ghost wide";
  back.textContent = "← " + backLabel;
  back.addEventListener("click", function () {
    panelEl.hidden = true;
    panelEl.innerHTML = "";
    listEl.hidden = false;
  });
  var card = document.createElement("div");
  card.className = "card";
  panelEl.appendChild(back);
  panelEl.appendChild(card);
  dispatchPanel(id, card);
  card.scrollIntoView({ block: "start" });
}
function dispatchPanel(id, card) {
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
  else if (id === "reward_codes") buildRewardsUI(card, "full");
  else if (id === "access") panelAccess(card);
  else if (id === "screenshot") panelScreenshot(card);
}
function renderHomeTab() {
  renderSettingGroup($("homeSettingsList"), $("homeSettingsPanel"), SETTING_GROUPS[1], "Màn hình chính");
}
function renderSettingsTab() {
  renderSettingGroup($("secSettingsList"), $("secSettingsPanel"), SETTING_GROUPS[2], "Cài đặt");
}

/* Chụp màn hình TV (v2: chuyển từ tab Hôm nay sang Cài đặt → Bảo mật & công cụ). */
function panelScreenshot(card) {
  panelTitle(card, "Chụp màn hình TV",
    "Chụp đúng khung hình TV đang hiện. Cần cấp quyền 1 lần trên TV: mở app Khóa TV → Cài đặt → Chụp màn hình → Cấp quyền chụp màn hình (quyền mất sau mỗi lần khởi động lại TV).");
  var btn = document.createElement("button");
  btn.className = "btn accent wide";
  btn.textContent = "Chụp màn hình TV";
  var err = document.createElement("p");
  err.className = "muted small"; err.hidden = true;
  var img = document.createElement("img");
  img.style.cssText = "display:none;width:100%;border-radius:12px;margin-top:12px";
  img.alt = "Ảnh chụp màn hình TV";
  var tm = document.createElement("p");
  tm.className = "muted small"; tm.hidden = true;
  var again = document.createElement("button");
  again.className = "btn ghost wide"; again.textContent = "Chụp lại"; again.hidden = true;
  card.appendChild(btn); card.appendChild(err); card.appendChild(img);
  card.appendChild(tm); card.appendChild(again);
  var doShot = function () {
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
  btn.addEventListener("click", doShot);
  again.addEventListener("click", doShot);
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
  var heicNote = document.createElement("p");
  heicNote.className = "muted small";
  heicNote.hidden = true;
  heicNote.textContent = "Ảnh HEIC không xem trước được trên điện thoại — cứ bấm Áp dụng, TV vẫn nhận và hiển thị được.";
  card.appendChild(heicNote);
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
  prevImg.onerror = function () {
    prevImg.style.display = "none";
    prevImg.removeAttribute("src");
    heicNote.hidden = false;
  };
  msgInput.addEventListener("input", function () { prevMsg.textContent = msgInput.value; });
  fileInput.addEventListener("change", function () {
    var f = fileInput.files[0];
    if (!f) return;
    heicNote.hidden = true;
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
        heicNote.hidden = true;
        bgState.textContent = "Chưa có ảnh nền riêng — đang dùng nền mặc định.";
        loadSettings();
      }
    });
  });
}

/* Mã PIN: đổi PIN mới 4–6 số. */
function panelPin(card) {
  panelTitle(card, "Mã PIN TV", "Đổi mã PIN mở khóa TV — không cần nhập PIN cũ.");
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

/* Mã PIN web (v2.3): đổi mã mới. Nút Đăng xuất nằm riêng ở tab Cài đặt (v2). */
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
  panelTitle(card, "Trợ năng & chống tắt", "Bật Trợ năng thực hiện trên TV (Cài đặt → Trợ năng).");
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
    [["default", "Mặc định"], ["rounded", "Bo tròn"]].forEach(function (m) {
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

/* Ảnh nền màn hình chính: tải ảnh từ điện thoại lên TV. */
function panelHomeWallpaper(card) {
  panelTitle(card, "Ảnh nền màn hình chính",
    "Chọn dùng 10 ảnh phong cảnh mặc định kèm app, hoặc ảnh của anh tải từ điện thoại lên.");
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
  fileInput.multiple = true;
  card.appendChild(fileInput);
  var multiNote = document.createElement("p");
  multiNote.className = "muted small";
  multiNote.textContent = "Chọn được nhiều ảnh cùng lúc (tối đa 20 ảnh/lần). Ảnh HEIC vẫn tải được — TV tự đổi sang JPG.";
  card.appendChild(multiNote);
  var addBtn = document.createElement("button");
  addBtn.className = "btn success wide";
  addBtn.textContent = "Thêm ảnh lên TV";
  addBtn.disabled = true;
  card.appendChild(addBtn);
  var pickedFiles = [];
  var MAX_BATCH = 20;
  fileInput.addEventListener("change", function () {
    pickedFiles = [];
    var fs = fileInput.files || [];
    for (var i = 0; i < fs.length && pickedFiles.length < MAX_BATCH; i++) pickedFiles.push(fs[i]);
    if (fs.length > MAX_BATCH) toast("Chỉ lấy 20 ảnh đầu trong lần chọn này.", true);
    addBtn.disabled = pickedFiles.length === 0;
    addBtn.textContent = pickedFiles.length > 1 ? "Thêm " + pickedFiles.length + " ảnh lên TV" : "Thêm ảnh lên TV";
  });
  function readAsDataURL(f, cb) {
    var r = new FileReader();
    r.onload = function () { cb(r.result); };
    r.onerror = function () { cb(null); };
    r.readAsDataURL(f);
  }
  function uploadOne(dataUrl, cb) {
    fetch("/api/settings-homebg", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "img=" + encodeURIComponent(dataUrl)
    }).then(function (r) {
      return r.json().catch(function () { return { ok: r.ok }; });
    }).then(function (d) {
      cb(!!(d && d.ok !== false), (d && d.error) || "");
    }).catch(function () { cb(false, ""); });
  }
  addBtn.addEventListener("click", function () {
    if (!pickedFiles.length) return;
    var total = pickedFiles.length, idx = 0, okCount = 0;
    var fails = [];
    addBtn.disabled = true;
    (function next() {
      if (idx >= total) {
        addBtn.disabled = false;
        addBtn.textContent = "Thêm ảnh lên TV";
        fileInput.value = "";
        pickedFiles = [];
        if (!fails.length) toast("Đã thêm " + okCount + "/" + total + " ảnh lên TV.", true);
        else toast("Đã thêm " + okCount + "/" + total + " ảnh. Lỗi: " +
          fails.slice(0, 3).join("; ") + (fails.length > 3 ? "…" : ""), false);
        refreshWpList();
        return;
      }
      var f = pickedFiles[idx];
      addBtn.textContent = "Đang tải " + (idx + 1) + "/" + total + "…";
      readAsDataURL(f, function (dataUrl) {
        if (!dataUrl) { fails.push(f.name + ": không đọc được file"); idx++; next(); return; }
        uploadOne(dataUrl, function (ok, err) {
          if (ok) okCount++;
          else fails.push(f.name + (err ? ": " + err : ": tải lỗi"));
          idx++;
          next();
        });
      });
    })();
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
      var src = d.source || "default";
      srcDef.className = "btn wide" + (src === "default" ? " success" : "");
      srcCustom.className = "btn wide" + (src === "custom" ? " success" : "");
      stateP.textContent = src === "default"
        ? "Đang dùng: 10 ảnh phong cảnh mặc định" + (d.interval > 0 ? " — tự đổi mỗi " + d.interval + " phút." : " — đang tắt tự đổi.")
        : (n === 0 ? "Đang dùng: ảnh của tôi (chưa có ảnh nào)."
          : "Đang dùng: " + n + " ảnh của tôi" + (d.interval > 0 ? " — tự đổi mỗi " + d.interval + " phút." : " — đang tắt tự đổi."));
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

/* --- Khởi tạo --- */
function init() {
  buildTabbar();
  buildAddChips();
  reloadTodayRewards = buildRewardsUI($("rewardCard"), "list");
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
  /* Gửi tin nhắn lên TV (banner overlay, tự tắt sau 5 giây) — v2: một dòng. */
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
  $("btnReport7").addEventListener("click", toggleReport);
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
    setConn(true);
    statusData = s;
    renderHome(s);
    loadSettings();
    loadReport();
    loadAppLabels();
  });
  setInterval(function () { if (authed) loadStatus(); }, 5000);
}
document.addEventListener("DOMContentLoaded", init);
