const ROLE_CONFIG = {
  platform: {
    label: "平台管理",
    shortLabel: "平台",
    product: "场地通运营台",
    pages: [
      ["overview", "概览", "layout-dashboard"],
      ["venues", "场馆管理", "building-2"],
      ["catalog", "分类字典", "list-tree"],
      ["orders", "全局订单", "receipt-text"],
      ["billing", "月账单", "wallet-cards"],
      ["audit", "审计记录", "shield-check"]
    ]
  },
  venue: {
    label: "场馆管理",
    shortLabel: "场馆",
    product: "星河运动中心",
    pages: [
      ["dashboard", "经营看板", "layout-dashboard"],
      ["profile", "场馆资料", "store"],
      ["resources", "场地资源", "panels-top-left"],
      ["schedule", "排期管理", "calendar-days"],
      ["exceptions", "特殊日期", "calendar-cog"],
      ["inventory", "库存日历", "calendar-range"],
      ["blocks", "人工占用", "calendar-x-2"],
      ["orders", "订单管理", "receipt-text"],
      ["employees", "店员管理", "users"],
      ["verification", "核销记录", "badge-check"],
      ["billing", "服务费账单", "wallet-cards"]
    ]
  },
  user: {
    label: "用户小程序",
    shortLabel: "用户",
    product: "场地通",
    pages: [
      ["home", "场馆", "map-pin"],
      ["orders", "订单", "receipt-text"],
      ["profile", "我的", "user-round"]
    ]
  },
  staff: {
    label: "员工核销",
    shortLabel: "核销",
    product: "核销工作台",
    pages: [
      ["verify", "核销", "scan-line"],
      ["history", "记录", "history"],
      ["account", "我的", "circle-user-round"]
    ]
  }
};

const VENUE_IMAGES = {
  basketball: "https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=1000&q=80",
  tennis: "https://images.unsplash.com/photo-1595435934249-5df7ed86e1c0?auto=format&fit=crop&w=1000&q=80",
  badminton: "https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=1000&q=80"
};

const state = {
  role: "platform",
  page: "overview",
  selectedSlot: null,
  orderStatus: "CONFIRMED",
  paymentStatus: "SUCCEEDED",
  refundStatus: "NONE",
  inventoryStatus: "BOOKED",
  verificationStatus: "UNVERIFIED",
  booking: { slot: 0, unit: "A 半场", time: "18:00–19:00", amount: 60 },
  billingEntryCreated: false,
  modal: null,
  toast: "",
  theme: "light",
  venuePaused: false,
  verificationDone: false,
  phoneRevealed: false,
  billPaid: false,
  paymentView: "WAITING",
  userAuthorized: false,
  staffError: "",
  specialDateCreated: false,
  blockCreated: false,
  managementOrderCancelled: false
};

const BOOKING_SLOTS = [
  { time: "18:00–19:00", unit: "A 半场", amount: 60 },
  { time: "18:00–19:00", unit: "B 半场", amount: 60 },
  { time: "19:00–20:00", unit: "全场", amount: 100 },
  { time: "20:00–21:30", unit: "全场", amount: 150 }
];

function demoOrder() {
  return {
    id: "BK202608040019",
    venue: "星河运动中心",
    user: "王明",
    phone: "138****2088",
    ...state.booking
  };
}

function orderDisplay() {
  const labels = {
    PENDING_PAYMENT: ["待支付", "warning"],
    CONFIRMED: ["已确认", "success"],
    COMPLETED: ["已完成", "neutral"],
    CANCELLED: ["已取消", "danger"]
  };
  return labels[state.orderStatus];
}

function paymentDisplay() {
  const labels = {
    CREATED: ["待发起", "warning"],
    PROCESSING: ["处理中", "warning"],
    SUCCEEDED: ["模拟已支付", "info"],
    CLOSED: ["已关闭", "neutral"],
    UNKNOWN: ["结果未知", "warning"]
  };
  return labels[state.paymentStatus];
}

function refundDisplay() {
  const labels = {
    NONE: ["无", "neutral"],
    REFUND_PROCESSING: ["模拟退款处理中", "warning"],
    REFUND_SUCCEEDED: ["模拟退款成功", "success"]
  };
  return labels[state.refundStatus];
}

function bookingIsActive() {
  return !["CANCELLED", "COMPLETED"].includes(state.orderStatus) && state.inventoryStatus !== "AVAILABLE";
}

function slotIsUnavailable(index) {
  return index === 1 || (state.blockCreated && index === 3) || (bookingIsActive() && state.booking.slot === index);
}

function slotPrice(index) {
  return slotIsUnavailable(index) ? "已占用" : `¥${BOOKING_SLOTS[index].amount}`;
}

function inventoryStateForSlot(index) {
  if (bookingIsActive() && state.booking.slot === index) return state.inventoryStatus;
  if (state.blockCreated && index === 3) return "INTERNAL_BLOCKED";
  if (index === 1) return "BOOKED";
  return "AVAILABLE";
}

function inventoryTone(code) {
  return code === "AVAILABLE" ? "success" : code === "BOOKED" ? "info" : code === "INTERNAL_BLOCKED" ? "danger" : "warning";
}

function createDemoBooking() {
  state.booking = { slot: state.selectedSlot, ...BOOKING_SLOTS[state.selectedSlot] };
  state.orderStatus = "PENDING_PAYMENT";
  state.paymentStatus = "PROCESSING";
  state.refundStatus = "NONE";
  state.inventoryStatus = "PAYMENT_LOCKED";
  state.verificationStatus = "UNVERIFIED";
  state.verificationDone = false;
  state.billingEntryCreated = false;
  state.managementOrderCancelled = false;
}

const app = document.querySelector("#app");

function icon(name) {
  return `<i data-lucide="${name}" aria-hidden="true"></i>`;
}

function status(label, tone = "neutral") {
  return `<span class="status ${tone}">${label}</span>`;
}

function button(label, action, kind = "secondary", iconName = "arrow-right", extra = "") {
  return `<button class="button-${kind}" data-action="${action}" ${extra}>${icon(iconName)}<span>${label}</span></button>`;
}

function roleSwitcher() {
  return Object.entries(ROLE_CONFIG).map(([key, config]) => `
    <button class="role-button" data-action="switch-role" data-role="${key}" aria-pressed="${state.role === key}">
      ${config.label}
    </button>
  `).join("");
}

function renderShell(content) {
  const config = ROLE_CONFIG[state.role];
  const nav = config.pages.map(([key, label, iconName]) => `
    <button class="nav-item" data-action="navigate" data-page="${key}" aria-current="${state.page === key ? "page" : "false"}">
      ${icon(iconName)}<span>${label}</span>
    </button>
  `).join("");

  return `
    <div class="desktop-shell">
      <aside class="sidebar" aria-label="${config.label}主导航">
        <div class="sidebar-title">${config.product}</div>
        <nav class="nav-list">${nav}</nav>
        <div class="sidebar-foot">MVP 功能闭环版 · Mock 环境</div>
      </aside>
      <section class="workspace">
        <header class="workspace-header">
          <div>
            <p class="eyebrow">${config.label}</p>
            <h1>${currentPageLabel()}</h1>
          </div>
          <div class="header-actions">
            ${status(state.role === "platform" ? "平台管理员" : "场馆管理员", "info")}
            <button class="icon-button" data-action="logout-desktop" aria-label="退出当前账号" title="退出当前账号">${icon("log-out")}</button>
          </div>
        </header>
        <main class="workspace-content" id="main-content" tabindex="-1">${content}</main>
      </section>
    </div>
  `;
}

function renderPhone(content, title = currentPageLabel(), showBack = false) {
  const config = ROLE_CONFIG[state.role];
  const nav = config.pages.map(([key, label, iconName]) => `
    <button class="nav-item" data-action="navigate" data-page="${key}" aria-current="${state.page === key ? "page" : "false"}">
      ${icon(iconName)}<span>${label}</span>
    </button>
  `).join("");

  return `
    <div class="phone-wrap">
      <section class="phone" aria-label="${config.label}手机原型">
        <header class="mobile-header">
          ${showBack ? `<button class="icon-button" data-action="mobile-back" aria-label="返回" title="返回">${icon("chevron-left")}</button>` : `<span class="brand-mark">${icon(state.role === "staff" ? "scan-line" : "activity")}</span>`}
          <h1 class="mobile-title">${title}</h1>
          <button class="icon-button" aria-label="更多操作" title="更多操作">${icon("ellipsis")}</button>
        </header>
        <main class="mobile-content" id="main-content" tabindex="-1">${content}</main>
        <nav class="mobile-nav" style="--items:${config.pages.length}" aria-label="${config.label}主导航">${nav}</nav>
      </section>
    </div>
  `;
}

function currentPageLabel() {
  const page = ROLE_CONFIG[state.role].pages.find(([key]) => key === state.page);
  const details = { login: "登录", "password-change": "首次改密", "venue-detail": "场馆详情", "order-detail": "订单详情", "bill-detail": "账单详情" };
  return page ? page[1] : details[state.page] || "详情";
}

function metric(label, value, note) {
  return `<article class="metric"><div class="metric-label">${label}</div><div class="metric-value">${value}</div><div class="metric-note">${note}</div></article>`;
}

function desktopAuthPage() {
  const platform = state.role === "platform";
  if (state.page === "password-change") {
    return `<section class="auth-shell" id="main-content"><form class="auth-panel" onsubmit="return false"><span class="brand-mark">${icon("key-round")}</span><div><p class="eyebrow">首次登录</p><h1>修改初始密码</h1><p class="muted">修改后将撤销其他活动会话并进入${platform ? "平台运营台" : "场馆管理端"}。</p></div><div class="field"><label for="current-password">初始密码</label><input id="current-password" type="password" value="Initial-2026"></div><div class="field"><label for="new-password">新密码</label><input id="new-password" type="password" value="Secure-Booking-2026"></div><div class="field"><label for="confirm-password">确认新密码</label><input id="confirm-password" type="password" value="Secure-Booking-2026"></div>${button("确认修改并进入", "confirm-password-change", "primary", "arrow-right")}</form></section>`;
  }
  return `<section class="auth-shell" id="main-content"><form class="auth-panel" onsubmit="return false"><span class="brand-mark">${icon(platform ? "shield-check" : "store")}</span><div><p class="eyebrow">${platform ? "平台管理" : "场馆管理"}</p><h1>${platform ? "场地通运营台" : "星河运动中心"}</h1><p class="muted">使用管理员账号登录</p></div><div class="field"><label for="login-username">用户名</label><input id="login-username" autocomplete="username" value="${platform ? "platform_admin" : "xinghe_admin"}"></div><div class="field"><label for="login-password">密码</label><input id="login-password" type="password" autocomplete="current-password" value="Initial-2026"></div>${platform ? `<div class="field"><label for="login-code">本地二次验证码</label><input id="login-code" inputmode="numeric" value="260804"></div>` : ""}${button("登录", "submit-desktop-login", "primary", "log-in")}<p class="helper">连续失败将临时锁定账号和来源地址。</p></form></section>`;
}

function detailHeader(title, subtitle, backPage, actions = "") {
  return `<div class="section-title"><div><button class="button-ghost compact" data-action="navigate" data-page="${backPage}">${icon("arrow-left")}返回列表</button><h2>${title}</h2><p>${subtitle}</p></div><div class="header-actions">${actions}</div></div>`;
}

function timeline(items) {
  return `<ol class="timeline">${items.map(([title, meta, tone = "success"]) => `<li class="timeline-item ${tone}"><strong>${title}</strong><span>${meta}</span></li>`).join("")}</ol>`;
}

function platformPage() {
  if (state.page === "venues") return platformVenues();
  if (state.page === "venue-detail") return platformVenueDetail();
  if (state.page === "catalog") return platformCatalog();
  if (state.page === "orders") return ordersTable(true);
  if (state.page === "order-detail") return managementOrderDetail();
  if (state.page === "billing") return billingTable(true);
  if (state.page === "bill-detail") return billDetail();
  if (state.page === "audit") return auditTable();
  return platformOverview();
}

function platformOverview() {
  return `
    <div class="section-title"><div><h2>运营概览</h2><p>试点准备与 Mock 交易运行状态</p></div>${button("新建场馆", "open-create-venue", "primary", "plus")}</div>
    <section class="metric-grid" aria-label="核心指标">
      ${metric("已启用场馆", "8", "目标 10 家")}
      ${metric("今日预约", "42", "较昨日 +8")}
      ${metric("支付异常", "2", "均未超过 15 分钟")}
      ${metric("待收月账单", "¥1,286", "2 家场馆")}
    </section>
    <div class="content-grid">
      <section class="panel"><div class="panel-header"><h3>最近订单</h3>${button("查看全部", "go-orders", "ghost", "arrow-right")}</div>${ordersTable(false)}</section>
      <section class="panel"><div class="panel-header"><h3>运营待办</h3></div><div class="list">
        <div class="list-row"><div><strong>支付状态未知</strong><small>2 笔，最长 8 分钟</small></div>${status("待查询", "warning")}</div>
        <div class="list-row"><div><strong>场馆资料待补充</strong><small>滨江网球中心</small></div>${status("运营", "info")}</div>
        <div class="list-row"><div><strong>账单即将到期</strong><small>2 笔，3 天内到期</small></div>${status("提醒", "warning")}</div>
      </div></section>
    </div>`;
}

function platformVenues() {
  return `
    <div class="section-title"><div><h2>场馆管理</h2><p>经营主体与首期单门店状态</p></div>${button("新建场馆", "open-create-venue", "primary", "plus")}</div>
    <div class="filter-row"><input class="search-input" aria-label="搜索场馆" placeholder="搜索场馆名称" style="max-width:280px"><button class="button-secondary">${icon("search")}查询</button></div>
    <div class="table-wrap" style="margin-top:16px"><table><thead><tr><th>场馆</th><th>管理员</th><th>交易状态</th><th>今日预约</th><th>创建时间</th><th>操作</th></tr></thead><tbody>
      <tr><td><strong>星河运动中心</strong><br><small class="muted">ORG-202608-001</small></td><td>陈管理员</td><td>${state.venuePaused ? status("已暂停", "danger") : status("可交易", "success")}</td><td>18</td><td>2026-07-18</td><td><div class="inline-actions"><button class="button-ghost" data-action="${state.venuePaused ? "open-resume-venue" : "open-pause-venue"}">${state.venuePaused ? "恢复" : "暂停"}</button><button class="button-ghost" data-action="open-platform-venue">详情</button></div></td></tr>
      <tr><td><strong>滨江网球中心</strong><br><small class="muted">ORG-202608-002</small></td><td>李管理员</td><td>${status("可交易", "success")}</td><td>9</td><td>2026-07-21</td><td><button class="button-ghost" data-action="open-platform-venue">详情</button></td></tr>
      <tr><td><strong>青禾羽毛球馆</strong><br><small class="muted">ORG-202608-003</small></td><td>周管理员</td><td>${status("待启用", "warning")}</td><td>0</td><td>2026-08-01</td><td><button class="button-ghost" data-action="open-platform-venue">详情</button></td></tr>
    </tbody></table></div>`;
}

function platformVenueDetail() {
  const gate = state.venuePaused ? status("已暂停新增预约", "danger") : status("正常接单", "success");
  const gateAction = state.venuePaused ? button("恢复交易", "open-resume-venue", "primary", "play") : button("暂停新增预约", "open-pause-venue", "danger", "pause");
  return `${detailHeader("星河运动中心", "ORG-202608-001 · 单门店功能闭环", "venues", gateAction)}
    <section class="detail-grid">
      <article class="detail-block"><div class="panel-header"><h3>经营主体</h3>${gate}</div><dl class="summary-list"><div class="summary-row"><dt>主体名称</dt><dd>上海星河体育有限公司</dd></div><div class="summary-row"><dt>门店</dt><dd>星河运动中心</dd></div><div class="summary-row"><dt>结算策略</dt><dd>场馆直收月账单</dd></div><div class="summary-row"><dt>服务费率</dt><dd>6.00% · 按订单固化</dd></div></dl></article>
      <article class="detail-block"><div class="panel-header"><h3>场馆管理员</h3>${status("启用", "success")}</div><dl class="summary-list"><div class="summary-row"><dt>姓名</dt><dd>陈管理员</dd></div><div class="summary-row"><dt>用户名</dt><dd>xinghe_admin</dd></div><div class="summary-row"><dt>最近登录</dt><dd>今天 09:18</dd></div></dl><button class="button-secondary" data-action="reset-admin" style="margin-top:16px">${icon("key-round")}重置密码</button></article>
      <article class="detail-block full"><div class="panel-header"><h3>交易门禁与 blocker</h3><span class="muted">主体与门店均需开放</span></div><div class="table-wrap"><table><thead><tr><th>范围</th><th>原因</th><th>状态</th><th>创建人</th><th>创建时间</th></tr></thead><tbody>${state.venuePaused ? `<tr><td>门店</td><td>人工暂停 · 设备安全检查</td><td>${status("活动中", "danger")}</td><td>平台管理员</td><td>今天 10:48</td></tr>` : `<tr><td colspan="5"><div class="empty-state compact-empty">当前无活动 blocker，允许新增预约</div></td></tr>`}</tbody></table></div></article>
      <article class="detail-block full"><div class="panel-header"><h3>最近状态记录</h3></div>${timeline([["场馆资料更新", "陈管理员 · 今天 09:32"], ["排期版本 SCH-v3 发布", "陈管理员 · 昨天 18:20"], ["服务费率 6.00% 生效", "平台管理员 · 2026-07-18"]])}</article>
    </section>`;
}

function platformCatalog() {
  return `
    <div class="section-title"><div><h2>分类与出租类型</h2><p>停用只影响新场地选择，不影响历史订单</p></div>${button("新增分类", "open-category", "primary", "plus")}</div>
    <div class="content-grid">
      <section class="panel"><div class="panel-header"><h3>运动分类</h3></div><div class="list">
        <div class="list-row"><div><strong>球类运动</strong><small>一级分类 · 4 个项目</small></div>${status("启用", "success")}</div>
        <div class="list-row"><div><strong>篮球</strong><small>BASKETBALL · 二级分类</small></div>${status("启用", "success")}</div>
        <div class="list-row"><div><strong>羽毛球</strong><small>BADMINTON · 二级分类</small></div>${status("启用", "success")}</div>
        <div class="list-row"><div><strong>网球</strong><small>TENNIS · 二级分类</small></div>${status("启用", "success")}</div>
      </div></section>
      <section class="panel"><div class="panel-header"><h3>出租类型</h3></div><div class="list">
        <div class="list-row"><div><strong>全场</strong><small>篮球</small></div>${status("启用", "success")}</div>
        <div class="list-row"><div><strong>半场</strong><small>篮球</small></div>${status("启用", "success")}</div>
        <div class="list-row"><div><strong>标准场</strong><small>羽毛球、网球</small></div>${status("启用", "success")}</div>
      </div></section>
    </div>`;
}

function ordersTable(full) {
  const venueScope = state.role === "venue";
  const order = demoOrder();
  const [orderLabel, orderTone] = orderDisplay();
  const [paymentLabel, paymentTone] = paymentDisplay();
  const secondHeader = full && !venueScope ? "场馆" : "用户";
  const firstValue = full && !venueScope ? order.venue : `${order.user.slice(0, 1)}** ${order.phone}`;
  const table = `<div class="table-wrap"><table><thead><tr><th>订单号</th><th>${secondHeader}</th><th>场地与时段</th><th>金额</th><th>订单状态</th><th>支付状态</th><th>操作</th></tr></thead><tbody>
    <tr><td>${order.id}</td><td>${firstValue}</td><td>${order.unit}<br><small class="muted">今天 ${order.time}</small></td><td>¥${order.amount.toFixed(2)}</td><td>${status(orderLabel, orderTone)}</td><td>${status(paymentLabel, paymentTone)}</td><td><button class="button-ghost" data-action="open-management-order">详情</button></td></tr>
    <tr><td>BK202608040017</td><td>${full && !venueScope ? "滨江网球中心" : "赵** 156****3201"}</td><td>标准网球场<br><small class="muted">今天 18:30–20:00</small></td><td>¥150.00</td><td>${status("待支付", "warning")}</td><td>${status("处理中", "warning")}</td><td><button class="button-ghost" data-action="open-management-order">详情</button></td></tr>
    <tr><td>BK202608040012</td><td>${full && !venueScope ? "星河运动中心" : "刘** 139****8162"}</td><td>全场<br><small class="muted">今天 16:00–17:00</small></td><td>¥100.00</td><td>${status("已完成", "neutral")}</td><td>${status("模拟已支付", "info")}</td><td><button class="button-ghost" data-action="open-management-order">详情</button></td></tr>
  </tbody></table></div>`;
  if (!full) return table;
  return `<div class="section-title"><div><h2>全局订单</h2><p>联系方式默认脱敏，完整号码查看必须留痕</p></div></div><div class="filter-row"><input class="search-input" aria-label="搜索订单" placeholder="订单号 / 场馆" style="max-width:280px"><select aria-label="订单状态"><option>全部状态</option><option>待支付</option><option>已确认</option><option>已完成</option><option>已取消</option></select></div><div style="margin-top:16px">${table}</div>`;
}

function billingTable(platform) {
  const order = demoOrder();
  const demoFee = state.billingEntryCreated ? order.amount * 0.06 : 0;
  const payable = 950.4 + demoFee;
  const canRunBilling = state.orderStatus === "COMPLETED" && !state.billingEntryCreated;
  return `
    <div class="section-title"><div><h2>${platform ? "月账单" : "服务费与月账单"}</h2><p>历史明细不可改写，退款通过负向调整处理</p></div>${platform ? button("运行 Mock 计费任务", "run-billing", "primary", "file-plus-2", canRunBilling ? "" : "disabled") : ""}</div>
    <section class="metric-grid">
      ${metric("本期服务费", `¥${(1286.4 + demoFee).toFixed(2)}`, demoFee ? `含 ${order.id} 服务费` : "截至 8 月 4 日")}
      ${metric("退款调整", "-¥36.00", "3 笔")}
      ${metric("应付合计", `¥${payable.toFixed(2)}`, "9 月 8 日到期")}
      ${metric("账单状态", "草稿", "9 月 1 日生成")}
    </section>
    <div class="table-wrap"><table><thead><tr><th>账期</th><th>${platform ? "场馆" : "类型"}</th><th>正向费用</th><th>调整</th><th>应付</th><th>状态</th><th>操作</th></tr></thead><tbody>
      <tr><td>2026-07</td><td>${platform ? "星河运动中心" : "月度服务费"}</td><td>¥${(986.4 + demoFee).toFixed(2)}</td><td>-¥36.00</td><td>¥${payable.toFixed(2)}</td><td>${status(state.billPaid ? "已支付" : "待支付", state.billPaid ? "success" : "warning")}</td><td><button class="button-ghost" data-action="open-bill-detail">查看</button></td></tr>
      <tr><td>2026-06</td><td>${platform ? "星河运动中心" : "月度服务费"}</td><td>¥842.00</td><td>¥0.00</td><td>¥842.00</td><td>${status("已支付", "success")}</td><td><button class="button-ghost" data-action="open-bill-detail">查看</button></td></tr>
    </tbody></table></div>`;
}

function managementOrderDetail() {
  const platform = state.role === "platform";
  const order = demoOrder();
  const [orderLabel, orderTone] = orderDisplay();
  const [paymentLabel, paymentTone] = paymentDisplay();
  const [refundLabel, refundTone] = refundDisplay();
  const verified = state.verificationStatus === "VERIFIED";
  const backPage = "orders";
  const cancelAction = platform || ["CANCELLED", "COMPLETED"].includes(state.orderStatus) ? "" : button("场馆取消", "open-venue-cancel", "danger", "x-circle");
  return `${detailHeader(`订单 ${order.id}`, `今天 ${order.time} · 篮球 1 号场 ${order.unit}`, backPage, cancelAction)}
    <section class="detail-grid">
      <article class="detail-block"><div class="panel-header"><h3>订单与用户</h3>${status(orderLabel, orderTone)}</div><dl class="summary-list"><div class="summary-row"><dt>场馆</dt><dd>${order.venue}</dd></div><div class="summary-row"><dt>用户</dt><dd>${order.user}</dd></div><div class="summary-row"><dt>联系电话</dt><dd>${state.phoneRevealed ? "138 0000 2088" : order.phone}</dd></div><div class="summary-row"><dt>实付金额</dt><dd>¥${order.amount.toFixed(2)}</dd></div></dl>${state.phoneRevealed ? `<p class="callout success-callout">完整号码已显示，本次查看已写入审计记录。</p>` : button("查看完整号码", "open-reveal-phone", "secondary", "eye")}${state.managementOrderCancelled ? `<p class="callout warning-callout">场馆取消 · 库存转为 ${state.inventoryStatus} · ${refundLabel}</p>` : ""}</article>
      <article class="detail-block"><div class="panel-header"><h3>库存快照</h3>${status(state.inventoryStatus, state.inventoryStatus === "BOOKED" ? "info" : "warning")}</div><dl class="summary-list"><div class="summary-row"><dt>可售单元版本</dt><dd>篮球 1 号 ${order.unit} v1</dd></div><div class="summary-row"><dt>基础资源</dt><dd>${order.unit === "全场" ? "A 半场 + B 半场" : order.unit}</dd></div><div class="summary-row"><dt>排期版本</dt><dd>SCH-v3</dd></div><div class="summary-row"><dt>价格快照</dt><dd>整段 ¥${order.amount.toFixed(2)}</dd></div></dl></article>
      <article class="detail-block"><div class="panel-header"><h3>支付与退款</h3>${status(paymentLabel, paymentTone)}</div><dl class="summary-list"><div class="summary-row"><dt>支付单</dt><dd>PAY202608040019</dd></div><div class="summary-row"><dt>Provider</dt><dd>MockPaymentProvider</dd></div><div class="summary-row"><dt>支付状态</dt><dd>${paymentLabel}</dd></div><div class="summary-row"><dt>退款</dt><dd>${refundLabel}</dd></div></dl>${state.refundStatus === "REFUND_PROCESSING" ? button("模拟退款到账", "complete-refund", "secondary", "refresh-cw") : ""}</article>
      <article class="detail-block"><div class="panel-header"><h3>核销</h3>${status(verified ? "已核销" : "待核销", verified ? "success" : "warning")}</div><dl class="summary-list"><div class="summary-row"><dt>有效窗口</dt><dd>17:30–${order.time.split("–")[1]}</dd></div><div class="summary-row"><dt>核销凭证</dt><dd>${verified ? "已失效" : "用户端可展示"}</dd></div></dl></article>
      <article class="detail-block full"><div class="panel-header"><h3>状态时间线</h3></div>${timeline([[orderLabel, `订单 ${order.id} · 当前状态`], [paymentLabel, `库存 ${state.inventoryStatus}`], [verified ? "核销成功" : "待核销", verified ? "店员李晓峰 · 17:42" : "等待有效窗口内首次核销"], ["订单已创建", "幂等键 idem-82d1"]])}</article>
    </section>`;
}

function billDetail() {
  const platform = state.role === "platform";
  const order = demoOrder();
  const demoFee = state.billingEntryCreated ? order.amount * 0.06 : 0;
  const action = platform && !state.billPaid ? button("记录线下收款", "open-record-payment", "primary", "badge-dollar-sign") : "";
  return `${detailHeader("2026 年 7 月服务费账单", "BILL-202607-XH001 · 星河运动中心", "billing", action)}
    <section class="metric-grid">${metric("正向费用", `¥${(986.4 + demoFee).toFixed(2)}`, demoFee ? "含演示订单" : "46 条明细")}${metric("退款调整", "-¥36.00", "3 条调整")}${metric("应付金额", `¥${(950.4 + demoFee).toFixed(2)}`, "2026-08-08 到期")}${metric("状态", state.billPaid ? "已支付" : "待支付", state.billPaid ? "今天 11:06 记录" : "逾期只预警")}</section>
    <section class="detail-grid"><article class="detail-block full"><div class="panel-header"><h3>计费与调整明细</h3><span class="muted">历史记录不可编辑</span></div><div class="table-wrap"><table><thead><tr><th>来源</th><th>类型</th><th>计费基数</th><th>费率</th><th>金额</th><th>时间</th></tr></thead><tbody>${state.billingEntryCreated ? `<tr><td>${order.id}</td><td>订单完成</td><td>¥${order.amount.toFixed(2)}</td><td>6.00%</td><td>+¥${demoFee.toFixed(2)}</td><td>Mock 计费任务</td></tr>` : ""}<tr><td>BK202607310086</td><td>订单完成</td><td>¥100.00</td><td>6.00%</td><td>+¥6.00</td><td>08-01 20:30</td></tr><tr><td>BK202607280042</td><td>退款调整</td><td>¥60.00</td><td>6.00%</td><td>-¥3.60</td><td>08-02 09:15</td></tr></tbody></table></div></article>${state.billPaid ? `<article class="detail-block full"><div class="panel-header"><h3>收款记录</h3>${status("已核对", "success")}</div><dl class="summary-list"><div class="summary-row"><dt>收款日期</dt><dd>2026-08-04</dd></div><div class="summary-row"><dt>实收金额</dt><dd>¥${(950.4 + demoFee).toFixed(2)}</dd></div><div class="summary-row"><dt>凭证</dt><dd>银行转账回单 XH-0804</dd></div><div class="summary-row"><dt>记录人</dt><dd>平台管理员</dd></div></dl></article>` : ""}</section>`;
}

function auditTable() {
  return `<div class="section-title"><div><h2>审计记录</h2><p>高风险操作、完整号码查看和状态变更</p></div></div><div class="table-wrap"><table><thead><tr><th>时间</th><th>操作者</th><th>动作</th><th>对象</th><th>原因</th><th>结果</th></tr></thead><tbody>
    <tr><td>10:42:18</td><td>平台管理员</td><td>查看完整手机号</td><td>BK202608040018</td><td>联系用户确认到场</td><td>${status("成功", "success")}</td></tr>
    <tr><td>10:26:03</td><td>陈管理员</td><td>发布排期版本</td><td>篮球 1 号场 v3</td><td>新增周末固定段</td><td>${status("成功", "success")}</td></tr>
    <tr><td>09:55:41</td><td>系统任务</td><td>支付状态查询</td><td>PAY202608040017</td><td>处理状态未知</td><td>${status("待重试", "warning")}</td></tr>
  </tbody></table></div>`;
}

function venuePage() {
  if (state.page === "profile") return venueProfile();
  if (state.page === "resources") return venueResources();
  if (state.page === "schedule") return venueSchedule();
  if (state.page === "exceptions") return venueSpecialDates();
  if (state.page === "inventory") return venueInventory();
  if (state.page === "blocks") return venueBlocks();
  if (state.page === "orders") return ordersTable(true).replace("全局订单", "场馆订单").replace("订单号 / 场馆", "订单号 / 用户");
  if (state.page === "order-detail") return managementOrderDetail();
  if (state.page === "employees") return venueEmployees();
  if (state.page === "verification") return venueVerification();
  if (state.page === "billing") return billingTable(false);
  if (state.page === "bill-detail") return billDetail();
  return venueDashboard();
}

function venueDashboard() {
  const order = demoOrder();
  const verified = state.verificationStatus === "VERIFIED";
  return `
    <div class="section-title"><div><h2>今日经营</h2><p>2026 年 8 月 4 日 · 所有金额均为模拟支付</p></div>${status(state.venuePaused ? "新增预约已暂停" : "正常接单", state.venuePaused ? "danger" : "success")}</div>
    <section class="metric-grid">
      ${metric("今日预约", state.orderStatus === "CANCELLED" ? "18" : "19", "含当前演示订单")}
      ${metric("待核销", state.orderStatus === "CONFIRMED" && !verified ? "6" : "5", `最近一场 ${order.time.slice(0, 5)}`)}
      ${metric("已核销", verified ? "10" : "9", "核销率 64%")}
      ${metric("场地利用率", "72%", "较上周 +6%")}
    </section>
    <div class="content-grid">
      <section class="panel"><div class="panel-header"><h3>未来 7 天预约趋势</h3><span class="muted">笔数</span></div><div class="mini-chart" aria-label="未来七天预约数柱状图"><div class="bar" style="--value:58%"><span>一</span></div><div class="bar" style="--value:72%"><span>二</span></div><div class="bar" style="--value:64%"><span>三</span></div><div class="bar" style="--value:86%"><span>四</span></div><div class="bar" style="--value:76%"><span>五</span></div><div class="bar" style="--value:100%"><span>六</span></div><div class="bar" style="--value:92%"><span>日</span></div></div></section>
      <section class="panel"><div class="panel-header"><h3>当前演示订单</h3></div><div class="list"><div class="list-row"><div><strong>${order.unit} · ${order.time}</strong><small>王** · ${order.phone}</small></div>${status(orderDisplay()[0], orderDisplay()[1])}</div><div class="list-row"><div><strong>核销状态</strong><small>${verified ? "李晓峰 · 17:42" : "等待有效窗口"}</small></div>${status(verified ? "已核销" : "待核销", verified ? "success" : "warning")}</div></div></section>
    </div>`;
}

function venueProfile() {
  return `<div class="section-title"><div><h2>场馆资料</h2><p>保存后立即生效并记录审计</p></div>${button("保存资料", "save-profile", "primary", "save")}</div><form class="form-grid" onsubmit="return false">
    <div class="field"><label for="venue-name">场馆名称</label><input id="venue-name" value="星河运动中心"></div>
    <div class="field"><label for="venue-phone">联系电话</label><input id="venue-phone" type="tel" value="021-5566 8899"></div>
    <div class="field full"><label for="venue-address">详细地址</label><input id="venue-address" value="上海市浦东新区星河路 88 号"></div>
    <div class="field"><label for="venue-lat">纬度</label><input id="venue-lat" value="31.230416"></div>
    <div class="field"><label for="venue-lng">经度</label><input id="venue-lng" value="121.473701"></div>
    <div class="field full"><label for="venue-intro">场馆介绍</label><textarea id="venue-intro">室内木地板篮球场，配备更衣室、淋浴和停车位。</textarea></div>
    <div class="field full"><label for="venue-rule">入场须知</label><textarea id="venue-rule">请提前 15 分钟到场，穿着非标记运动鞋。</textarea></div>
  </form>`;
}

function venueResources() {
  return `<div class="section-title"><div><h2>篮球 1 号场</h2><p>组合关系使用不可变版本，已有订单始终引用原版本</p></div>${button("新建组合版本", "open-version", "primary", "git-branch-plus")}</div>
    <div class="resource-map" aria-label="篮球一号场基础资源"><div class="resource-cell">A 半场<br><small>基础资源 A</small></div><div class="resource-cell">B 半场<br><small>基础资源 B</small></div></div>
    <div class="table-wrap"><table><thead><tr><th>可售单元</th><th>版本</th><th>所需基础资源</th><th>出租类型</th><th>状态</th><th>操作</th></tr></thead><tbody>
      <tr><td>篮球 1 号全场</td><td>v2</td><td>A 半场 + B 半场</td><td>全场</td><td>${status("当前版本", "success")}</td><td><button class="button-ghost">查看</button></td></tr>
      <tr><td>篮球 1 号 A 半场</td><td>v1</td><td>A 半场</td><td>半场</td><td>${status("当前版本", "success")}</td><td><button class="button-ghost">查看</button></td></tr>
      <tr><td>篮球 1 号 B 半场</td><td>v1</td><td>B 半场</td><td>半场</td><td>${status("当前版本", "success")}</td><td><button class="button-ghost">查看</button></td></tr>
    </tbody></table></div>`;
}

function venueSchedule() {
  return `<div class="section-title"><div><h2>周排期模板</h2><p>固定时段整段计价，开始与结束必须按 30 分钟对齐</p></div>${button("新增固定时段", "open-schedule-slot", "primary", "calendar-plus")}</div>
    <div class="filter-row"><button class="button-secondary">上一周</button><strong>8 月 3 日–8 月 9 日</strong><button class="button-secondary">下一周</button></div>
    <div class="table-wrap" style="margin-top:16px"><table><thead><tr><th>星期</th><th>可售单元</th><th>固定时段</th><th>整段总价</th><th>发布版本</th><th>状态</th></tr></thead><tbody>
      <tr><td>周一至周五</td><td>A 半场 / B 半场</td><td>18:00–19:00</td><td>¥60.00</td><td>SCH-v3</td><td>${status("已发布", "success")}</td></tr>
      <tr><td>周一至周五</td><td>全场</td><td>19:00–20:00</td><td>¥100.00</td><td>SCH-v3</td><td>${status("已发布", "success")}</td></tr>
      <tr><td>周六、周日</td><td>全场</td><td>10:00–12:00</td><td>¥220.00</td><td>SCH-v3</td><td>${status("已发布", "success")}</td></tr>
    </tbody></table></div>`;
}

function venueSpecialDates() {
  return `<div class="section-title"><div><h2>特殊日期</h2><p>特殊日期完整覆盖当天周模板，不叠加执行</p></div>${button("新增特殊日期", "open-special-date", "primary", "calendar-plus")}</div>
    <div class="filter-row"><select aria-label="特殊日期类型"><option>全部类型</option><option>特殊营业</option><option>闭馆</option><option>维护</option></select><input class="search-input" type="month" value="2026-08" aria-label="月份" style="max-width:180px"></div>
    <div class="table-wrap" style="margin-top:16px"><table><thead><tr><th>日期</th><th>类型</th><th>覆盖内容</th><th>影响库存</th><th>状态</th><th>操作</th></tr></thead><tbody>
      ${state.specialDateCreated ? `<tr><td>2026-08-18</td><td>设备维护</td><td>闭馆 09:00–12:00</td><td>6 个原子</td><td>${status("待生效", "warning")}</td><td><button class="button-ghost">编辑</button></td></tr>` : ""}
      <tr><td>2026-08-08</td><td>节假日价格</td><td>全场 10:00–12:00 · ¥260</td><td>4 个原子</td><td>${status("已发布", "success")}</td><td><button class="button-ghost">查看</button></td></tr>
      <tr><td>2026-08-15</td><td>闭馆</td><td>全天 · 地板保养</td><td>32 个原子</td><td>${status("已发布", "success")}</td><td><button class="button-ghost">查看</button></td></tr>
    </tbody></table></div>`;
}

function venueInventory() {
  const slots = [
    ["18:00", "A 半场", inventoryStateForSlot(0)], ["18:00", "B 半场", inventoryStateForSlot(1)],
    ["19:00", "全场", inventoryStateForSlot(2)], ["20:00", "全场", inventoryStateForSlot(3)],
    ["21:00", "A 半场", "RECONCILE_PENDING"], ["21:00", "B 半场", "AVAILABLE"]
  ];
  return `<div class="section-title"><div><h2>库存日历</h2><p>2026 年 8 月 4 日 · 当前演示订单会同步占用对应基础资源</p></div>${button("创建人工占用", "open-block", "primary", "calendar-x-2")}</div><div class="legend-row"><span>${status("AVAILABLE", "success")}</span><span>${status("PAYMENT_LOCKED", "warning")}</span><span>${status("BOOKED", "info")}</span><span>${status("INTERNAL_BLOCKED", "danger")}</span><span>${status("RECONCILE_PENDING", "neutral")}</span></div><div class="slot-grid">${slots.map(([time, unit, code]) => `<button class="slot ${code === "AVAILABLE" ? "" : "blocked"}" ${code === "AVAILABLE" ? "" : "disabled"}><strong>${time}</strong><br>${unit}<br><small>${code}</small></button>`).join("")}</div>`;
}

function venueBlocks() {
  return `<div class="section-title"><div><h2>人工占用</h2><p>与线上预约共用基础资源冲突检测</p></div>${button("创建人工占用", "open-block", "primary", "calendar-x-2")}</div>
    <div class="filter-row"><input class="search-input" type="date" value="2026-08-04" aria-label="占用日期" style="max-width:190px"><select aria-label="占用状态"><option>全部状态</option><option>生效中</option><option>未来</option><option>已取消</option></select></div>
    <div class="table-wrap" style="margin-top:16px"><table><thead><tr><th>占用编号</th><th>场地与时段</th><th>基础资源</th><th>原因</th><th>操作人</th><th>状态</th><th>操作</th></tr></thead><tbody>
      ${state.blockCreated ? `<tr><td>BLK-20260804-012</td><td>今天 20:00–21:00 · 全场</td><td>A + B</td><td>设备维护</td><td>陈管理员</td><td>${status("未来", "warning")}</td><td><button class="button-ghost" data-action="cancel-block">取消占用</button></td></tr>` : ""}
      <tr><td>BLK-20260804-008</td><td>今天 14:00–15:30 · A 半场</td><td>A</td><td>校队训练</td><td>陈管理员</td><td>${status("已结束", "neutral")}</td><td><button class="button-ghost">详情</button></td></tr>
      <tr><td>BLK-20260805-003</td><td>明天 09:00–10:00 · 全场</td><td>A + B</td><td>地板检查</td><td>陈管理员</td><td>${status("未来", "warning")}</td><td><button class="button-ghost">取消占用</button></td></tr>
    </tbody></table></div>`;
}

function venueEmployees() {
  return `<div class="section-title"><div><h2>核销店员</h2><p>场馆管理员可管理店员，但不能创建其他管理员</p></div>${button("新增店员", "open-employee", "primary", "user-plus")}</div><div class="table-wrap"><table><thead><tr><th>店员</th><th>微信绑定</th><th>状态</th><th>今日核销</th><th>最近操作</th><th>操作</th></tr></thead><tbody>
    <tr><td>李晓峰<br><small class="muted">EMP-0018</small></td><td>${status("已绑定", "success")}</td><td>${status("启用", "success")}</td><td>6</td><td>10:36</td><td><div class="inline-actions"><button class="button-ghost" data-action="open-binding-code">重新绑定</button><button class="button-ghost" data-action="open-disable-employee">停用</button></div></td></tr>
    <tr><td>张敏<br><small class="muted">EMP-0021</small></td><td>${status("待绑定", "warning")}</td><td>${status("启用", "success")}</td><td>0</td><td>—</td><td><button class="button-ghost" data-action="open-binding-code">生成绑定码</button></td></tr>
  </tbody></table></div>`;
}

function venueVerification() {
  const order = demoOrder();
  const verified = state.verificationStatus === "VERIFIED";
  return `<div class="section-title"><div><h2>核销记录</h2><p>成功与拒绝均保留员工、门店、方式和原因</p></div></div>
    <div class="filter-row"><select aria-label="核销结果"><option>全部结果</option><option>成功</option><option>拒绝</option></select><select aria-label="核销方式"><option>全部方式</option><option>二维码</option><option>短码</option></select><input class="search-input" type="date" value="2026-08-04" aria-label="核销日期" style="max-width:190px"></div>
    <div class="table-wrap" style="margin-top:16px"><table><thead><tr><th>时间</th><th>订单</th><th>用户</th><th>店员</th><th>方式</th><th>结果</th><th>原因</th></tr></thead><tbody>
      ${verified ? `<tr><td>17:42:00</td><td>${order.id}</td><td>王** · ${order.phone}</td><td>李晓峰</td><td>二维码</td><td>${status("成功", "success")}</td><td>首次核销 · 凭证已失效</td></tr>` : ""}
      <tr><td>17:36:12</td><td>BK202608040018</td><td>王** · 138****2088</td><td>李晓峰</td><td>短码</td><td>${status("成功", "success")}</td><td>首次核销</td></tr>
      <tr><td>16:55:08</td><td>BK202608040015</td><td>陈** · 185****4912</td><td>李晓峰</td><td>二维码</td><td>${status("成功", "success")}</td><td>首次核销</td></tr>
      <tr><td>16:31:44</td><td>—</td><td>—</td><td>李晓峰</td><td>短码</td><td>${status("拒绝", "danger")}</td><td>凭证已过期</td></tr>
      <tr><td>15:20:09</td><td>BK202608040009</td><td>赵** · 156****3201</td><td>李晓峰</td><td>二维码</td><td>${status("拒绝", "danger")}</td><td>订单不属于当前门店</td></tr>
    </tbody></table></div>`;
}

function userPage() {
  if (state.page === "venue-detail") return userVenueDetail();
  if (state.page === "checkout") return userCheckout();
  if (state.page === "payment") return userPayment();
  if (state.page === "order-detail") return userOrderDetail();
  if (state.page === "credential") return userCredential();
  if (state.page === "orders") return userOrders();
  if (state.page === "profile") return userProfile();
  return userHome();
}

function userHome() {
  return `<div class="mobile-stack"><div class="field"><label for="venue-search">搜索场馆</label><input id="venue-search" placeholder="场馆名称或运动项目"></div>
    ${venueCard("星河运动中心", "篮球 · 羽毛球", "浦东新区 · 1.8 km", "¥60 起", VENUE_IMAGES.basketball, "open-user-venue")}
    ${venueCard("滨江网球中心", "网球", "徐汇区 · 3.2 km", "¥100 起", VENUE_IMAGES.tennis, "open-user-venue")}
    ${venueCard("青禾羽毛球馆", "羽毛球", "静安区 · 4.6 km", "¥45 起", VENUE_IMAGES.badminton, "open-user-venue")}
  </div>`;
}

function venueCard(name, sports, distance, price, imageUrl, action) {
  return `<article class="venue-card"><button data-action="${action}" aria-label="查看${name}"><img class="venue-image" src="${imageUrl}" alt="${name}场地实景" width="720" height="360" loading="lazy" decoding="async"><div class="venue-body"><h2>${name}</h2><div class="venue-meta"><span>${sports}</span><span>${distance}</span></div><div class="price-row"><span>固定时段预约</span><span class="price">${price}</span></div></div></button></article>`;
}

function userVenueDetail() {
  const slots = BOOKING_SLOTS;
  return `<div class="mobile-hero"><img src="${VENUE_IMAGES.basketball}" alt="星河运动中心篮球场实景" width="720" height="405"></div>
    <section class="mobile-section"><h2>星河运动中心</h2><p class="muted">室内木地板 · 更衣室 · 淋浴 · 停车位</p><div class="venue-meta"><span>${icon("map-pin")} 浦东新区星河路 88 号</span><span>1.8 km</span></div></section>
    <section class="mobile-section"><h3>选择日期</h3><div class="date-strip"><button class="date-button selected">今天<br>8/4</button><button class="date-button">明天<br>8/5</button><button class="date-button">周四<br>8/6</button><button class="date-button">周五<br>8/7</button></div></section>
    <section class="mobile-section"><h3>固定时段</h3><div class="mobile-slots">${slots.map((slot, index) => `<button class="slot ${state.selectedSlot === index ? "selected" : ""} ${slotIsUnavailable(index) ? "blocked" : ""}" data-action="select-user-slot" data-slot="${index}" ${slotIsUnavailable(index) ? "disabled" : ""}><strong>${slot.time}</strong><br>${slot.unit}<br><small>${slotPrice(index)}</small></button>`).join("")}</div></section>
    <div class="sticky-action"><div><small class="muted">已选</small><br><strong>${state.selectedSlot === null ? "请选择时段" : `¥${slots[state.selectedSlot].amount}`}</strong></div>${button("确认预约", "go-checkout", "primary", "arrow-right", state.selectedSlot === null ? "disabled" : "")}</div>`;
}

function userCheckout() {
  const slot = BOOKING_SLOTS[state.selectedSlot ?? 0];
  return `<div class="mobile-stack"><section class="mobile-section"><h2>预约信息</h2><dl class="summary-list"><div class="summary-row"><dt>场馆</dt><dd>星河运动中心</dd></div><div class="summary-row"><dt>场地</dt><dd>${slot.unit}</dd></div><div class="summary-row"><dt>日期</dt><dd>2026-08-04</dd></div><div class="summary-row"><dt>时段</dt><dd>${slot.time}</dd></div></dl></section>
    <section class="mobile-section"><h3>联系人</h3><div class="field"><label for="contact-name">姓名</label><input id="contact-name" value="王明"></div><div class="field" style="margin-top:12px"><label for="contact-phone">手机号</label><input id="contact-phone" type="tel" value="138 0000 2088"></div></section>
    <section class="mobile-section"><h3>取消规则</h3><p class="muted">预约开始前且尚未核销，可申请全额退款；开始后不可自助取消。</p></section>
    <div class="sticky-action"><div><small class="muted">应付</small><br><span class="price">¥${slot.amount}</span></div>${button("提交预约", "submit-booking", "primary", "lock-keyhole")}</div></div>`;
}

function userPayment() {
  const order = demoOrder();
  if (state.paymentView === "UNKNOWN") {
    return `<div class="result-state"><div class="result-icon" style="background:var(--warning-soft);color:var(--warning)">${icon("circle-help")}</div><p class="eyebrow">MockPaymentProvider · UNKNOWN</p><h2>支付结果待确认</h2><p class="muted">订单 ${order.id} 的库存仍被占用，结果确认前不会重新售卖。</p><div class="callout warning-callout"><strong>当前库存：${state.inventoryStatus}</strong><br><span>查询结果前不能直接释放占用。</span></div><div class="action-row">${button("查询支付结果", "query-payment", "primary", "refresh-cw")}${button("模拟查询成功", "resolve-payment", "secondary", "check")}</div><button class="button-ghost" data-action="return-orders">返回订单列表</button></div>`;
  }
  return `<div class="result-state"><div class="result-icon" style="background:var(--info-soft);color:var(--info)">${icon("wallet-cards")}</div><p class="eyebrow">MockPaymentProvider</p><h2>等待模拟支付</h2><p class="muted">订单 ${order.id}<br>库存状态：${state.inventoryStatus}</p><div class="price">¥${order.amount.toFixed(2)}</div><div class="action-row">${button("模拟成功", "pay-success", "primary", "check")}${button("模拟未知", "pay-unknown", "secondary", "circle-help")}</div><button class="button-ghost" data-action="abandon-payment">放弃支付</button></div>`;
}

function userOrders() {
  const order = demoOrder();
  const [label, tone] = orderDisplay();
  return `<div class="mobile-stack"><div class="filter-row"><button class="button-primary">全部</button><button class="button-secondary">待支付</button><button class="button-secondary">待使用</button></div><article class="order-card"><button class="button-ghost" data-action="open-order" style="width:100%;padding:0;text-align:left"><div class="order-head"><h3>${order.venue}</h3>${status(label, tone)}</div><p>篮球 1 号场 · ${order.unit}</p><p>今天 ${order.time}</p><div class="price-row"><span>${order.id}</span><span class="price">¥${order.amount.toFixed(2)}</span></div></button></article><article class="order-card"><div class="order-head"><h3>滨江网球中心</h3>${status("已完成", "neutral")}</div><p>标准场 · 8 月 2 日 19:00–20:30</p></article></div>`;
}

function userOrderDetail() {
  const order = demoOrder();
  const [label, tone] = orderDisplay();
  const [paymentLabel, paymentTone] = paymentDisplay();
  const [refundLabel, refundTone] = refundDisplay();
  const canCancel = state.orderStatus === "CONFIRMED" && state.verificationStatus === "UNVERIFIED";
  const actions = state.orderStatus === "PENDING_PAYMENT" ? button("继续支付", "continue-payment", "primary", "wallet-cards") : canCancel ? `<div class="action-row">${button("核销凭证", "open-credential", "primary", "qr-code")}${button("取消预约", "open-cancel", "secondary", "x-circle")}</div>` : "";
  return `<div class="mobile-stack"><section class="mobile-section"><div class="order-head"><div><p class="eyebrow">订单状态</p><h2>${label}</h2></div>${status(state.orderStatus === "CANCELLED" ? refundLabel : paymentLabel, state.orderStatus === "CANCELLED" ? refundTone : paymentTone)}</div></section><section class="mobile-section"><dl class="summary-list"><div class="summary-row"><dt>场馆</dt><dd>${order.venue}</dd></div><div class="summary-row"><dt>场地</dt><dd>篮球 1 号场 · ${order.unit}</dd></div><div class="summary-row"><dt>时段</dt><dd>今天 ${order.time}</dd></div><div class="summary-row"><dt>联系人</dt><dd>${order.user} ${order.phone}</dd></div><div class="summary-row"><dt>实付</dt><dd>¥${order.amount.toFixed(2)}</dd></div><div class="summary-row"><dt>库存</dt><dd>${state.inventoryStatus}</dd></div><div class="summary-row"><dt>核销</dt><dd>${state.verificationStatus === "VERIFIED" ? "已核销，凭证已失效" : "待核销"}</dd></div><div class="summary-row"><dt>订单号</dt><dd>${order.id}</dd></div></dl></section>${state.refundStatus === "REFUND_PROCESSING" ? `<div class="callout warning-callout">退款义务已创建，库存状态为 ${state.inventoryStatus}。${button("模拟退款到账", "complete-refund", "secondary", "refresh-cw")}</div>` : ""}${state.orderStatus === "CANCELLED" && state.refundStatus === "REFUND_SUCCEEDED" ? `<div class="empty-state">退款已原路返回 Mock 账户<br>库存已按当前排期版本释放</div>` : actions}</div>`;
}

function userCredential() {
  const order = demoOrder();
  if (state.orderStatus !== "CONFIRMED") return `<div class="result-state"><div class="result-icon" style="background:var(--warning-soft);color:var(--warning)">${icon("shield-x")}</div><h2>当前不可展示凭证</h2><p class="muted">订单状态为${orderDisplay()[0]}，核销凭证只对待使用订单开放。</p>${button("返回订单", "open-order", "primary", "receipt-text")}</div>`;
  if (state.verificationStatus === "VERIFIED") return `<div class="result-state"><div class="result-icon">${icon("badge-check")}</div><h2>凭证已核销</h2><p class="muted">${order.id} 已由李晓峰完成首次核销，二维码与短码均已失效。</p>${button("返回订单", "open-order", "primary", "receipt-text")}</div>`;
  return `<div class="mobile-stack"><div class="result-state"><p class="eyebrow">${order.venue}</p><h2>篮球 1 号场 · ${order.unit}</h2><p class="muted">今天 ${order.time}<br>17:30 起可核销</p></div><div class="qr-placeholder" role="img" aria-label="动态核销二维码占位图"></div><div class="code-display">482 719</div><p class="helper" style="text-align:center">短码仅在本场馆有效 · 01:42 后刷新</p>${button("刷新凭证", "refresh-code", "secondary", "refresh-cw")}</div>`;
}

function userProfile() {
  return `<div class="mobile-stack"><div class="result-state"><div class="result-icon" style="background:var(--primary-soft);color:var(--primary)">${icon("user-round")}</div><h2>王明</h2><p class="muted">微信用户 · ${state.userAuthorized ? "138****2088" : "手机号未授权"}</p>${status(state.userAuthorized ? "已授权" : "待完善", state.userAuthorized ? "success" : "warning")}</div><div class="list"><button class="list-row list-button" data-action="toast-contact"><div><strong>常用联系人</strong><small>1 位联系人</small></div>${icon("chevron-right")}</button><button class="list-row list-button" data-action="open-privacy"><div><strong>隐私与授权</strong><small>${state.userAuthorized ? "已记录 2026-08-04 版本" : "查看用途并授权"}</small></div>${icon("chevron-right")}</button></div><button class="button-secondary" disabled>${icon("log-out")}退出登录</button></div>`;
}

function staffPage() {
  if (state.page === "binding") return staffBinding();
  if (state.page === "history") return staffHistory();
  if (state.page === "account") return staffAccount();
  if (state.page === "verify-result") return staffResult();
  if (state.page === "verify-error") return staffError();
  if (state.page === "verify-confirm") return staffConfirm();
  return staffVerify();
}

function staffVerify() {
  return `<div class="mobile-stack"><div class="scan-area"><div class="scan-frame"></div><p>将用户核销二维码放入框内</p></div>${button("模拟扫码识别", "staff-scan", "primary", "scan-line")}<div class="mobile-section"><h3>短码核销</h3><div class="field"><label for="short-code">6 位短码</label><input id="short-code" inputmode="numeric" maxlength="6" value="482719" aria-describedby="short-code-help"><small id="short-code-help">只查询当前所属门店的活动凭证</small></div><button class="button-secondary" data-action="staff-code" style="width:100%;margin-top:12px">${icon("keyboard")}查询订单</button></div></div>`;
}

function staffConfirm() {
  const order = demoOrder();
  return `<div class="mobile-stack"><section class="mobile-section"><div class="order-head"><h2>核销确认</h2>${status("窗口内", "success")}</div></section><dl class="summary-list"><div class="summary-row"><dt>订单</dt><dd>${order.id}</dd></div><div class="summary-row"><dt>用户</dt><dd>王** · ${order.phone}</dd></div><div class="summary-row"><dt>场地</dt><dd>篮球 1 号场 · ${order.unit}</dd></div><div class="summary-row"><dt>预约时段</dt><dd>今天 ${order.time}</dd></div><div class="summary-row"><dt>订单状态</dt><dd>${orderDisplay()[0]}</dd></div><div class="summary-row"><dt>核销方式</dt><dd>动态二维码</dd></div></dl><div class="action-row">${button("确认核销", "staff-confirm", "primary", "badge-check")}${button("返回", "staff-back", "secondary", "arrow-left")}</div></div>`;
}

function staffResult() {
  const order = demoOrder();
  return `<div class="result-state"><div class="result-icon">${icon("check")}</div><h2>核销成功</h2><p class="muted">篮球 1 号场 · ${order.unit}<br>王** · 今天 17:42</p>${status("首次核销，凭证已失效", "success")}<div class="callout"><strong>Mock 系统任务</strong><br><span>预约结束后，系统会把未取消订单标记为已完成。</span></div>${state.orderStatus === "CONFIRMED" ? button("模拟预约结束", "complete-order", "secondary", "clock-3") : ""}${button("返回核销首页", "staff-home", "primary", "scan-line")}</div>`;
}

function staffError() {
  const error = state.staffError || "短码不存在或已失效";
  return `<div class="result-state"><div class="result-icon" style="background:var(--danger-soft);color:var(--danger)">${icon("shield-x")}</div><h2>无法核销</h2><p class="muted">${error}</p><div class="callout warning-callout"><strong>本次尝试已记录</strong><br><span>连续失败会触发短期锁定，请核对门店和凭证有效时间。</span></div>${button("重新输入短码", "staff-home", "primary", "keyboard")}</div>`;
}

function staffBinding() {
  return `<div class="mobile-stack"><div class="result-state"><div class="result-icon" style="background:var(--info-soft);color:var(--info)">${icon("link")}</div><p class="eyebrow">员工首次使用</p><h2>绑定核销身份</h2><p class="muted">绑定码由场馆管理员生成，10 分钟内有效且只能使用一次。</p></div><div class="field"><label for="staff-binding-code">一次性绑定码</label><input id="staff-binding-code" autocomplete="one-time-code" value="XH-7K9P-4Q2M" aria-describedby="binding-help"><small id="binding-help">将绑定李晓峰 · 星河运动中心</small></div><div class="callout"><strong>微信身份校验</strong><br><span>服务端使用 wx.login code 换取身份，不接收客户端传入 openid。</span></div>${button("确认绑定", "confirm-staff-binding", "primary", "link")}</div>`;
}

function staffHistory() {
  const order = demoOrder();
  return `<div class="mobile-stack"><div class="section-title"><div><h2>今日核销</h2><p>李晓峰 · 星河运动中心</p></div>${status(state.verificationDone ? "7 笔" : "6 笔", "info")}</div><div class="list">${state.verificationDone ? `<div class="list-row"><div><strong>${order.id}</strong><small>${order.unit} · 17:42 · 扫码</small></div>${status("成功", "success")}</div>` : ""}<div class="list-row"><div><strong>BK202608040018</strong><small>A 半场 · 17:36 · 短码</small></div>${status("成功", "success")}</div><div class="list-row"><div><strong>BK202608040015</strong><small>全场 · 16:55 · 扫码</small></div>${status("成功", "success")}</div><div class="list-row"><div><strong>482 701</strong><small>16:31 · 短码 · 已过期</small></div>${status("拒绝", "danger")}</div></div></div>`;
}

function staffAccount() {
  return `<div class="mobile-stack"><div class="result-state"><div class="result-icon" style="background:var(--primary-soft);color:var(--primary)">${icon("circle-user-round")}</div><h2>李晓峰</h2><p class="muted">星河运动中心 · 核销店员</p>${status("已绑定", "success")}</div><div class="list"><div class="list-row"><div><strong>员工编号</strong><small>EMP-0018</small></div></div><button class="list-row list-button" data-action="open-staff-binding"><div><strong>微信绑定</strong><small>2026-07-22 完成 · 查看绑定流程</small></div>${icon("chevron-right")}</button></div><button class="button-secondary" disabled>${icon("log-out")}退出登录</button></div>`;
}

function renderModal() {
  if (!state.modal) return "";
  const modals = {
    createVenue: `<h2>新建场馆</h2><p>创建经营主体、唯一门店和首个场馆管理员。</p><div class="form-grid"><div class="field"><label for="new-org">经营主体名称</label><input id="new-org" value="上海新锐体育有限公司"></div><div class="field"><label for="new-venue">门店名称</label><input id="new-venue" value="新锐体育馆"></div><div class="field"><label for="new-admin-name">管理员姓名</label><input id="new-admin-name" value="赵管理员"></div><div class="field"><label for="new-admin">管理员用户名</label><input id="new-admin" value="xinrui_admin"></div><div class="field"><label for="new-rate">初始服务费率</label><input id="new-rate" type="number" value="6.00" step="0.01"><small>按订单创建时固化</small></div></div><div class="action-row" style="margin-top:20px">${button("确认创建", "confirm-create-venue", "primary", "plus")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    cancel: `<h2>取消预约</h2><p>当前尚未到预约开始时间且未核销，可申请全额退款 ¥60.00。</p><div class="field"><label for="cancel-reason">取消原因</label><select id="cancel-reason"><option>行程有变</option><option>预约信息有误</option><option>其他原因</option></select></div><div class="action-row" style="margin-top:20px">${button("确认取消", "confirm-cancel", "danger", "x-circle")}${button("暂不取消", "close-modal", "secondary", "arrow-left")}</div>`,
    block: `<h2>创建人工占用</h2><p>人工占用与线上预约共用基础资源冲突检测。</p><div class="field"><label for="block-time">时段</label><select id="block-time"><option>今天 20:00–21:00 · 全场</option><option>明天 09:00–10:00 · A 半场</option></select></div><div class="field" style="margin-top:12px"><label for="block-reason">原因</label><input id="block-reason" value="设备维护"></div><div class="action-row" style="margin-top:20px">${button("确认占用", "confirm-block", "primary", "calendar-x-2")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    employee: `<h2>新增核销店员</h2><p>店员只拥有所属门店核销权限。</p><div class="field"><label for="employee-name">姓名</label><input id="employee-name" value="赵可"></div><div class="field" style="margin-top:12px"><label for="employee-phone">手机号</label><input id="employee-phone" type="tel" value="139 0000 2681"></div><div class="action-row" style="margin-top:20px">${button("确认新增", "confirm-employee", "primary", "user-plus")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    pauseVenue: `<h2>暂停新增预约</h2><p>暂停后不允许创建新支付占用，已有待支付和已确认订单继续进入安全终态。</p><div class="field"><label for="pause-scope">暂停范围</label><select id="pause-scope"><option>星河运动中心（门店）</option><option>上海星河体育有限公司（主体）</option></select></div><div class="field" style="margin-top:12px"><label for="pause-reason">原因</label><textarea id="pause-reason">设备安全检查</textarea></div><div class="action-row" style="margin-top:20px">${button("确认暂停", "confirm-pause-venue", "danger", "pause")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    resumeVenue: `<h2>恢复交易</h2><p>只解除“人工暂停” blocker。若仍存在余额不足或风控 blocker，交易门禁继续关闭。</p><div class="callout warning-callout">当前仅有 1 个活动 blocker，解除后将恢复新增预约。</div><div class="action-row" style="margin-top:20px">${button("确认恢复", "confirm-resume-venue", "primary", "play")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    revealPhone: `<h2>查看完整手机号</h2><p>完整号码仅用于履约联系，本次查看将记录到审计日志。</p><div class="field"><label for="reveal-reason">查看原因</label><select id="reveal-reason"><option>联系用户确认到场</option><option>处理场馆取消</option><option>其他履约原因</option></select></div><div class="action-row" style="margin-top:20px">${button("确认并查看", "confirm-reveal-phone", "primary", "eye")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    venueCancel: `<h2>场馆取消订单</h2><p>开始前允许场馆取消；即使已经核销，也必须保留核销与取消两条审计事实。</p><div class="field"><label for="venue-cancel-reason">取消原因</label><select id="venue-cancel-reason"><option>设备故障</option><option>临时闭馆</option><option>场地安全问题</option></select></div><div class="field" style="margin-top:12px"><label for="inventory-destination">库存去向</label><select id="inventory-destination"><option>继续内部占用（INTERNAL_BLOCKED）</option><option>按当前排期版本恢复</option></select></div><div class="callout warning-callout">订单取消、库存去向和模拟全额退款义务将在同一事务创建。</div><div class="action-row" style="margin-top:20px">${button("确认取消并退款", "confirm-venue-cancel", "danger", "x-circle")}${button("返回", "close-modal", "secondary", "arrow-left")}</div>`,
    recordPayment: `<h2>记录线下收款</h2><p>记录场馆转账事实并核对账单应付金额。</p><div class="field"><label for="received-date">收款日期</label><input id="received-date" type="date" value="2026-08-04"></div><div class="field" style="margin-top:12px"><label for="received-amount">实收金额</label><input id="received-amount" value="950.40" inputmode="decimal"></div><div class="field" style="margin-top:12px"><label for="payment-proof">凭证编号</label><input id="payment-proof" value="银行转账回单 XH-0804"></div><div class="action-row" style="margin-top:20px">${button("确认并标记已支付", "confirm-record-payment", "primary", "badge-check")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    specialDate: `<h2>新增特殊日期</h2><p>保存后完整覆盖当天周模板。</p><div class="field"><label for="special-date">日期</label><input id="special-date" type="date" value="2026-08-18"></div><div class="field" style="margin-top:12px"><label for="special-type">类型</label><select id="special-type"><option>设备维护</option><option>特殊营业</option><option>节假日价格</option><option>全天闭馆</option></select></div><div class="field" style="margin-top:12px"><label for="special-range">覆盖时段</label><input id="special-range" value="09:00–12:00"></div><div class="action-row" style="margin-top:20px">${button("保存覆盖", "confirm-special-date", "primary", "calendar-check")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    bindingCode: `<h2>一次性微信绑定码</h2><p>绑定张敏 · 星河运动中心。绑定码只显示本次，10 分钟后过期。</p><div class="code-display">XH-7K9P-4Q2M</div><div class="callout warning-callout">重新绑定会撤销旧微信绑定和全部店员会话。</div><div class="action-row" style="margin-top:20px">${button("完成", "close-modal", "primary", "check")}</div>`,
    disableEmployee: `<h2>停用核销店员</h2><p>停用李晓峰后，其全部活动会话立即失效，历史核销记录保留。</p><div class="field"><label for="disable-reason">停用原因</label><input id="disable-reason" value="岗位调整"></div><div class="action-row" style="margin-top:20px">${button("确认停用", "confirm-disable-employee", "danger", "user-x")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    category: `<h2>新增运动分类</h2><p>首期界面维护两级分类，历史订单保存名称和编码快照。</p><div class="field"><label for="category-parent">上级分类</label><select id="category-parent"><option>球类运动</option><option>新建一级分类</option></select></div><div class="field" style="margin-top:12px"><label for="category-code">分类编码</label><input id="category-code" value="PADEL"></div><div class="field" style="margin-top:12px"><label for="category-name">分类名称</label><input id="category-name" value="板式网球"></div><div class="field" style="margin-top:12px"><label for="category-sort">排序</label><input id="category-sort" type="number" value="40"></div><div class="action-row" style="margin-top:20px">${button("保存分类", "confirm-category", "primary", "save")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    version: `<h2>新建组合版本</h2><p>资源组合不可原地修改，新排期将引用新版本。</p><div class="field"><label for="version-name">可售单元</label><input id="version-name" value="篮球 1 号全场"></div><fieldset class="field"><legend>所需基础资源</legend><label><input type="checkbox" checked> A 半场</label><label><input type="checkbox" checked> B 半场</label></fieldset><div class="callout warning-callout">旧版本 v2 继续服务已有排期、占用和订单，不能删除。</div><div class="action-row" style="margin-top:20px">${button("创建 v3", "confirm-version", "primary", "git-branch-plus")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    scheduleSlot: `<h2>新增固定时段</h2><p>时段按 30 分钟对齐，价格为整段总价。</p><div class="field"><label for="slot-days">适用星期</label><select id="slot-days"><option>周一至周五</option><option>周六、周日</option></select></div><div class="field" style="margin-top:12px"><label for="slot-unit">可售单元版本</label><select id="slot-unit"><option>篮球 1 号全场 v2</option><option>A 半场 v1</option><option>B 半场 v1</option></select></div><div class="form-grid" style="margin-top:12px"><div class="field"><label for="slot-start">开始</label><input id="slot-start" type="time" value="21:00" step="1800"></div><div class="field"><label for="slot-end">结束</label><input id="slot-end" type="time" value="22:00" step="1800"></div></div><div class="field" style="margin-top:12px"><label for="slot-price">整段总价（元）</label><input id="slot-price" inputmode="decimal" value="100.00"></div><div class="action-row" style="margin-top:20px">${button("保存并检查冲突", "confirm-schedule-slot", "primary", "calendar-check")}${button("取消", "close-modal", "secondary", "x")}</div>`,
    phoneAuth: `<h2>授权手机号</h2><p>手机号用于预约联系人和场馆履约联系。平台与场馆管理员默认仅查看脱敏号码。</p><div class="callout"><strong>授权记录</strong><br><span>将记录隐私说明版本、授权时间、微信身份和手机号来源。</span></div><div class="action-row" style="margin-top:20px">${button("同意并继续", "confirm-phone-auth", "primary", "shield-check")}${button("暂不授权", "close-modal", "secondary", "x")}</div>`,
    privacy: `<h2>隐私与授权</h2><p>${state.userAuthorized ? "手机号已授权，可用于当前预约履约。" : "当前尚未授权手机号。"}</p><dl class="summary-list"><div class="summary-row"><dt>隐私说明版本</dt><dd>MVP-2026-08</dd></div><div class="summary-row"><dt>手机号状态</dt><dd>${state.userAuthorized ? "已授权 · 138****2088" : "未授权"}</dd></div><div class="summary-row"><dt>完整号码访问</dt><dd>需授权并记录审计</dd></div></dl><div class="action-row" style="margin-top:20px">${state.userAuthorized ? button("关闭", "close-modal", "primary", "check") : button("授权手机号", "confirm-profile-auth", "primary", "shield-check")}</div>`
  };
  const modalContent = modals[state.modal];
  if (!modalContent) return "";
  return `<div class="modal-backdrop" data-action="close-modal" role="presentation"><section class="modal" role="dialog" aria-modal="true" aria-label="操作对话框">${modalContent}</section></div>`;
}

function render() {
  const mobile = state.role === "user" || state.role === "staff";
  const desktopAuth = !mobile && ["login", "password-change"].includes(state.page);
  let content;
  if (desktopAuth) content = desktopAuthPage();
  else if (state.role === "platform") content = renderShell(platformPage());
  else if (state.role === "venue") content = renderShell(venuePage());
  else if (state.role === "user") content = renderPhone(userPage(), userTitle(), ["venue-detail", "checkout", "payment", "order-detail", "credential"].includes(state.page));
  else content = renderPhone(staffPage(), staffTitle(), ["binding", "verify-confirm", "verify-result", "verify-error"].includes(state.page));

  app.innerHTML = `
    <header class="prototype-bar">
      <div class="prototype-brand"><span class="brand-mark">${icon("activity")}</span><span>场地通 MVP</span></div>
      <div class="role-switcher" aria-label="切换原型角色">${roleSwitcher()}</div>
      <button class="icon-button" data-action="toggle-theme" aria-label="切换明暗主题" title="切换明暗主题">${icon(state.theme === "light" ? "moon" : "sun")}</button>
    </header>
    <div class="prototype-stage">${content}</div>
    ${renderModal()}
    ${state.toast ? `<div class="toast" role="status" aria-live="polite">${state.toast}</div>` : ""}
  `;

  document.documentElement.dataset.theme = state.theme;
  document.body.dataset.role = state.role;
  if (window.lucide) window.lucide.createIcons();
  if (!mobile) document.title = `${currentPageLabel()} · 场地通 MVP`;
}

function userTitle() {
  const titles = { home: "发现场馆", "venue-detail": "场馆详情", checkout: "确认预约", payment: "模拟支付", orders: "我的订单", "order-detail": "订单详情", credential: "核销凭证", profile: "我的" };
  return titles[state.page] || "场地通";
}

function staffTitle() {
  const titles = { verify: "核销", binding: "身份绑定", "verify-confirm": "核销确认", "verify-result": "核销结果", "verify-error": "核销结果", history: "本人记录", account: "我的" };
  return titles[state.page] || "核销工作台";
}

function navigate(page) {
  state.page = page;
  history.replaceState(null, "", `#${state.role}/${page}`);
  render();
}

function showToast(message, nextPage = "") {
  if (nextPage) {
    state.page = nextPage;
    history.replaceState(null, "", `#${state.role}/${nextPage}`);
  }
  state.toast = message;
  render();
  window.setTimeout(() => {
    if (state.toast === message) {
      state.toast = "";
      render();
    }
  }, 2800);
}

function defaultPage(role) {
  return ROLE_CONFIG[role].pages[0][0];
}

app.addEventListener("click", (event) => {
  const target = event.target.closest("[data-action]");
  if (!target || target.disabled) return;
  const action = target.dataset.action;
  if (action === "close-modal" && target.classList.contains("modal-backdrop") && event.target !== target) return;

  if (action === "switch-role") {
    state.role = target.dataset.role;
    state.page = defaultPage(state.role);
    state.modal = null;
    history.replaceState(null, "", `#${state.role}/${state.page}`);
    render();
  } else if (action === "navigate") navigate(target.dataset.page);
  else if (action === "toggle-theme") { state.theme = state.theme === "light" ? "dark" : "light"; render(); }
  else if (action === "logout-desktop") navigate("login");
  else if (action === "submit-desktop-login") navigate("password-change");
  else if (action === "confirm-password-change") showToast("密码已修改，其他活动会话已撤销", defaultPage(state.role));
  else if (action === "go-orders") navigate("orders");
  else if (action === "open-create-venue") { state.modal = "createVenue"; render(); }
  else if (action === "open-platform-venue") navigate("venue-detail");
  else if (action === "open-pause-venue") { state.modal = "pauseVenue"; render(); }
  else if (action === "open-resume-venue") { state.modal = "resumeVenue"; render(); }
  else if (action === "confirm-pause-venue") { state.venuePaused = true; state.modal = null; showToast("人工暂停 blocker 已创建，新增预约已关闭"); }
  else if (action === "confirm-resume-venue") { state.venuePaused = false; state.modal = null; showToast("人工暂停 blocker 已解除，当前无其他活动原因"); }
  else if (action === "reset-admin") showToast("管理员密码已重置，首次登录必须修改密码");
  else if (action === "open-block") { state.modal = "block"; render(); }
  else if (action === "open-special-date") { state.modal = "specialDate"; render(); }
  else if (action === "open-employee") { state.modal = "employee"; render(); }
  else if (action === "open-binding-code") { state.modal = "bindingCode"; render(); }
  else if (action === "open-disable-employee") { state.modal = "disableEmployee"; render(); }
  else if (action === "open-category") { state.modal = "category"; render(); }
  else if (action === "open-version") { state.modal = "version"; render(); }
  else if (action === "open-schedule-slot") { state.modal = "scheduleSlot"; render(); }
  else if (action === "open-cancel") { state.modal = "cancel"; render(); }
  else if (action === "open-venue-cancel") { state.modal = "venueCancel"; render(); }
  else if (action === "open-reveal-phone") { state.modal = "revealPhone"; render(); }
  else if (action === "open-record-payment") { state.modal = "recordPayment"; render(); }
  else if (action === "open-privacy") { state.modal = "privacy"; render(); }
  else if (action === "close-modal") { state.modal = null; render(); }
  else if (action === "confirm-create-venue") { state.modal = null; showToast("场馆已创建，初始密码将通过线下安全渠道交付"); }
  else if (action === "confirm-block") { state.blockCreated = true; state.modal = null; showToast("人工占用已创建，20:00 全场在用户端同步变为不可约", "blocks"); }
  else if (action === "cancel-block") { state.blockCreated = false; showToast("人工占用已取消，库存将按当前排期版本恢复"); }
  else if (action === "confirm-special-date") { state.specialDateCreated = true; state.modal = null; showToast("特殊日期覆盖已保存，不再执行当天周模板"); }
  else if (action === "confirm-employee") { state.modal = null; showToast("店员已新增，可生成一次性微信绑定码"); }
  else if (action === "confirm-disable-employee") { state.modal = null; showToast("店员已停用，活动会话已撤销，历史核销保留"); }
  else if (action === "confirm-category") { state.modal = null; showToast("运动分类已新增，可用于新场地"); }
  else if (action === "confirm-version") { state.modal = null; showToast("组合版本 v3 已创建，旧版本保持只读"); }
  else if (action === "confirm-schedule-slot") { state.modal = null; showToast("固定时段已保存，未发现库存冲突"); }
  else if (action === "open-management-order") { state.phoneRevealed = false; navigate("order-detail"); }
  else if (action === "confirm-reveal-phone") { state.phoneRevealed = true; state.modal = null; showToast("完整号码查看已写入审计记录"); }
  else if (action === "confirm-venue-cancel") { state.orderStatus = "CANCELLED"; state.refundStatus = "REFUND_PROCESSING"; state.inventoryStatus = "INTERNAL_BLOCKED"; state.managementOrderCancelled = true; state.modal = null; showToast("订单取消、退款义务与 INTERNAL_BLOCKED 已同步写入"); }
  else if (action === "open-bill-detail") navigate("bill-detail");
  else if (action === "confirm-record-payment") { state.billPaid = true; state.modal = null; showToast("线下收款已记录，账单状态变为已支付"); }
  else if (action === "run-billing") { state.billingEntryCreated = true; showToast(`已为 ${demoOrder().id} 写入 6% 服务费明细`, "billing"); }
  else if (action === "save-profile") showToast("场馆资料已保存并记录审计");
  else if (action.startsWith("toast-")) showToast("原型状态已记录，详细表单将在接口契约阶段冻结");
  else if (action === "open-user-venue") navigate("venue-detail");
  else if (action === "select-user-slot") { state.selectedSlot = Number(target.dataset.slot); render(); }
  else if (action === "go-checkout") navigate("checkout");
  else if (action === "submit-booking") { if (state.userAuthorized) { createDemoBooking(); state.paymentView = "WAITING"; navigate("payment"); } else { state.modal = "phoneAuth"; render(); } }
  else if (action === "confirm-phone-auth") { state.userAuthorized = true; createDemoBooking(); state.paymentView = "WAITING"; state.modal = null; navigate("payment"); }
  else if (action === "confirm-profile-auth") { state.userAuthorized = true; state.modal = null; showToast("手机号授权已记录"); }
  else if (action === "pay-success" || action === "resolve-payment") { state.orderStatus = "CONFIRMED"; state.paymentStatus = "SUCCEEDED"; state.inventoryStatus = "BOOKED"; state.paymentView = "WAITING"; showToast("模拟支付成功，库存已 BOOKED，预约已确认", "order-detail"); }
  else if (action === "pay-unknown") { state.paymentStatus = "UNKNOWN"; state.inventoryStatus = "UNKNOWN"; state.paymentView = "UNKNOWN"; render(); }
  else if (action === "query-payment") showToast("主动查询已提交，结果仍为 UNKNOWN，库存继续占用");
  else if (action === "return-orders") navigate("orders");
  else if (action === "abandon-payment") { state.orderStatus = "CANCELLED"; state.paymentStatus = "CLOSED"; state.inventoryStatus = "AVAILABLE"; navigate("orders"); }
  else if (action === "continue-payment") navigate("payment");
  else if (action === "open-order") navigate("order-detail");
  else if (action === "open-credential") navigate("credential");
  else if (action === "confirm-cancel") { state.orderStatus = "CANCELLED"; state.refundStatus = "REFUND_PROCESSING"; state.inventoryStatus = "AVAILABLE"; state.modal = null; showToast("预约已取消，退款义务已创建，库存已释放"); }
  else if (action === "complete-refund") { state.refundStatus = "REFUND_SUCCEEDED"; showToast("模拟退款成功，订单取消事实保持不变"); }
  else if (action === "refresh-code") showToast("核销凭证已刷新，旧凭证进入宽限期");
  else if (action === "open-staff-binding") navigate("binding");
  else if (action === "confirm-staff-binding") showToast("微信身份绑定成功，旧绑定和会话已撤销", "account");
  else if (action === "staff-scan") { if (state.orderStatus === "CONFIRMED" && state.verificationStatus === "UNVERIFIED") navigate("verify-confirm"); else { state.staffError = state.verificationStatus === "VERIFIED" ? "该凭证已核销，已返回首次核销事实" : `订单当前为${orderDisplay()[0]}，不可核销`; navigate("verify-error"); } }
  else if (action === "staff-code") { const code = document.querySelector("#short-code")?.value.replace(/\s/g, ""); if (code !== "482719") { state.staffError = code === "482701" ? "短码已过期，请用户刷新核销凭证" : "短码不存在或不属于当前门店"; navigate("verify-error"); } else if (state.orderStatus === "CONFIRMED" && state.verificationStatus === "UNVERIFIED") navigate("verify-confirm"); else { state.staffError = state.verificationStatus === "VERIFIED" ? "短码已失效，订单已完成首次核销" : `订单当前为${orderDisplay()[0]}，不可核销`; navigate("verify-error"); } }
  else if (action === "staff-confirm") { state.verificationStatus = "VERIFIED"; state.verificationDone = true; navigate("verify-result"); }
  else if (action === "complete-order") { state.orderStatus = "COMPLETED"; showToast("Mock 系统任务已将订单标记为已完成，可进入账单计费", "history"); }
  else if (action === "staff-back" || action === "staff-home") navigate("verify");
  else if (action === "mobile-back") mobileBack();
});

function mobileBack() {
  const parent = {
    "venue-detail": "home",
    checkout: "venue-detail",
    payment: "checkout",
    "order-detail": "orders",
    credential: "order-detail",
    binding: "account",
    "verify-confirm": "verify",
    "verify-result": "verify",
    "verify-error": "verify"
  };
  navigate(parent[state.page] || defaultPage(state.role));
}

function restoreHash() {
  const [role, page] = location.hash.slice(1).split("/");
  if (ROLE_CONFIG[role]) {
    state.role = role;
    state.page = page || defaultPage(role);
  }
}

restoreHash();
render();
window.addEventListener("hashchange", () => {
  restoreHash();
  render();
});
