const state = {
  apiBase: localStorage.getItem('sportsBookingApi') || 'http://127.0.0.1:8080/api',
  csrf: sessionStorage.getItem('sportsBookingCsrf') || '',
  session: null,
  view: 'overview',
  pages: {},
  pageSizes: {},
  filters: {},
  orderNo: '',
  orderStatus: '',
  inventoryDate: '',
  inventoryStatus: '',
  blockDate: '',
  blockStatus: '',
  selectedId: null,
  expandedCategoryIds: null,
};

const app = document.querySelector('#app');
const toast = document.querySelector('#toast');

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));
}

function icon(name) { return `<i data-lucide="${name}" aria-hidden="true"></i>`; }
function refreshIcons() { window.lucide?.createIcons({ attrs: { 'stroke-width': 1.8 } }); }
function idempotencyKey() { return `${Date.now()}-${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`; }
function money(fen) { return fen == null ? '-' : `¥${(Number(fen) / 100).toFixed(2)}`; }
function shortDate(value) { return value ? value.replace(' 00:00:00', '') : '-'; }
function businessDate() { return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' }); }
function apiEndpointLabel() { try { const url = new URL(state.apiBase); return `${url.hostname}:${url.port || (url.protocol === 'https:' ? '443' : '80')}`; } catch (_) { return '自定义地址'; } }
const labels = {
  ACTIVE: '正常', DISABLED: '已停用', PAUSED: '已暂停', OPEN: '开放预约', BLOCKED: '暂停预约',
  DRAFT: '草稿', PUBLISHED: '已发布', ISSUED: '已签发', PAID: '已结清', OVERDUE: '已逾期',
  PENDING_PAYMENT: '待支付', PAYMENT_PROCESSING: '支付处理中', PAYMENT_UNKNOWN: '支付待确认',
  CONFIRMED: '待核销', COMPLETED: '已完成', CANCELLED: '已取消', SUCCEEDED: '成功', FAILED: '失败',
  PROCESSING: '处理中', UNKNOWN: '待确认', REFUND_SUCCEEDED: '退款完成', REFUND_UNKNOWN: '退款待确认',
  VERIFIED: '已核销', UNVERIFIED: '未核销', RECONCILE_PENDING: '待对账', CREATED: '已创建',
  PLATFORM_ADMIN: '平台管理员', VENUE_ADMIN: '场馆管理员', ACCOUNT: '账号', BOOKING_ORDER: '预约订单',
  PAYMENT_ORDER: '支付单', REFUND_ORDER: '退款单', VENUE_EMPLOYEE: '场馆店员', ORGANIZATION: '经营主体',
  STORE: '门店', SCHEDULE_VERSION: '排期版本', INVENTORY_SLOT: '库存时段', MONTHLY_BILL: '月账单',
  FEE_LEDGER: '服务费分录', ADMIN_LOGIN_SUCCEEDED: '管理员登录成功', ADMIN_LOGIN_REJECTED: '管理员登录失败',
  ADMIN_LOGOUT: '管理员退出登录', PLATFORM_ADMIN_BOOTSTRAPPED: '平台管理员初始化', ORGANIZATION_CREATED: '创建经营主体',
  BOOKING_CREATED: '创建预约', BOOKING_CANCELLED: '取消预约', PAYMENT_CREATED: '创建支付单',
  PAYMENT_SUCCEEDED: '支付成功', PAYMENT_FAILED: '支付失败', STAFF_WECHAT_BOUND: '店员微信绑定',
  EMPLOYEE_CREATED: '创建店员', EMPLOYEE_BINDING_CODE_ISSUED: '签发店员绑定码',
  EMPLOYEE_ACTIVE: '启用店员', EMPLOYEE_DISABLED: '停用店员', BOOKING_CONTACT_PHONE_REVEALED: '查看预约手机号',
  STORE_STATUS_CHANGED: '变更门店状态', ORGANIZATION_STATUS_CHANGED: '变更主体状态', VENUE_ADMIN_PASSWORD_RESET: '重置管理员密码',
  TRANSACTION_BLOCKER_ACTIVATED: '新增交易暂停原因', TRANSACTION_BLOCKER_RELEASED: '解除交易暂停原因',
  PHYSICAL_COURT_CREATED: '创建物理场地', PHYSICAL_COURT_STATUS_CHANGED: '变更物理场地状态', SELLABLE_UNIT_STATUS_CHANGED: '变更可售单元状态', SELLABLE_UNIT_VERSION_CREATED: '创建可售单元版本',
  QR: '二维码', SHORT_CODE: '短码', DUPLICATE: '重复核销', REJECTED: '已拒绝', BOUND: '已绑定', UNBOUND: '未绑定',
  SCHEDULE_DRAFT_CREATED: '创建排期草稿', SCHEDULE_VERSION_PUBLISHED: '发布排期版本',
  AVAILABLE: '可预约', HELD: '锁定中', BOOKED: '已占用', OFFLINE_RESERVATION: '线下预约', MAINTENANCE: '维护', EVENT: '活动', SELF_USE: '自用', OVERRIDE: '特殊营业', CLOSED: '闭馆',
};
function label(value) { return labels[value] || value || '-'; }
function pageFor(key) { return state.pages[key] || 1; }
function pageSizeFor(key) { return state.pageSizes[key] || 10; }
function pageQuery(key) { return `page=${pageFor(key)}&pageSize=${pageSizeFor(key)}`; }
function filterValue(key, name) { return state.filters[key]?.[name] || ''; }
function filteredPageQuery(key) {
  const query = new URLSearchParams({ page: String(pageFor(key)), pageSize: String(pageSizeFor(key)) });
  Object.entries(state.filters[key] || {}).forEach(([name, value]) => { if (value) query.set(name, value); });
  return query.toString();
}
function bindFilters(key, render) {
  document.querySelector(`[data-filter-key="${key}"]`)?.addEventListener('submit', event => {
    event.preventDefault();
    state.filters[key] = Object.fromEntries(new FormData(event.currentTarget).entries());
    state.pages[key] = 1;
    render();
  });
}
function pager(data, key) {
  const current = Number(data.current || 1); const size = Number(data.size || pageSizeFor(key)); const total = Number(data.total || 0); const pages = Math.max(1, Math.ceil(total / size));
  const sizes = [10, 20, 50].map(value => `<option value="${value}" ${size === value ? 'selected' : ''}>${value} 条/页</option>`).join('');
  return `<nav class="pagination" aria-label="分页"><div class="pagination-summary"><strong>${total}</strong><span>条记录</span><i></i><span>${current} / ${pages} 页</span></div><div class="pagination-controls"><label><span>每页</span><select data-page-size-key="${key}">${sizes}</select></label><button class="btn btn-secondary btn-icon table-action" type="button" aria-label="上一页" title="上一页" data-page-key="${key}" data-page="${current - 1}" ${current === 1 ? 'disabled' : ''}>${icon('chevron-left')}</button><button class="btn btn-secondary btn-icon table-action" type="button" aria-label="下一页" title="下一页" data-page-key="${key}" data-page="${current + 1}" ${current === pages ? 'disabled' : ''}>${icon('chevron-right')}</button></div></nav>`;
}
function bindPager(key, render) {
  document.querySelectorAll(`[data-page-key="${key}"]`).forEach(button => button.addEventListener('click', () => { state.pages[key] = Number(button.dataset.page); render(); }));
  document.querySelector(`[data-page-size-key="${key}"]`)?.addEventListener('change', event => { state.pageSizes[key] = Number(event.target.value); state.pages[key] = 1; render(); });
  refreshIcons();
}
function stateClass(value) {
  if (['ACTIVE', 'OPEN', 'CONFIRMED', 'COMPLETED', 'PAID', 'REFUND_SUCCEEDED', 'VERIFIED', 'SUCCEEDED'].includes(value)) return 'status-success';
  if (['PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PROCESSING', 'ISSUED', 'DRAFT'].includes(value)) return 'status-info';
  if (['UNKNOWN', 'PAYMENT_UNKNOWN', 'OVERDUE', 'RECONCILE_PENDING'].includes(value)) return 'status-warn';
  return 'status-danger';
}
function status(value) { return `<span class="status ${stateClass(value)}">${escapeHtml(label(value))}</span>`; }

function messageOf(reason) {
  const message = String(reason?.message ?? reason ?? '').trim();
  if (!message || /failed to fetch|networkerror|network request failed|load failed|connection refused|err_connection_refused/i.test(message)) return '无法连接后端服务，请确认后端已启动且地址正确。';
  if (/unexpected token|invalid json|json/i.test(message)) return '服务响应格式异常，请稍后重试。';
  if (/internal server error|service unavailable|timeout|timed out/i.test(message)) return '服务暂时不可用，请稍后重试。';
  return /[a-z]/i.test(message) ? '请求处理失败，请稍后重试。' : message;
}
function localizeNativeValidity() {
  document.addEventListener('invalid', event => {
    const field = event.target;
    if (!field?.setCustomValidity) return;
    const { validity } = field;
    field.setCustomValidity(validity.valueMissing ? '请填写此项' : validity.tooShort ? `请至少输入 ${field.minLength} 个字符` : validity.rangeUnderflow ? `请输入不小于 ${field.min} 的数值` : validity.rangeOverflow ? `请输入不大于 ${field.max} 的数值` : validity.patternMismatch || validity.typeMismatch ? '请输入正确的格式' : '请输入有效内容');
  }, true);
  document.addEventListener('input', event => event.target?.setCustomValidity?.(''));
  document.addEventListener('change', event => event.target?.setCustomValidity?.(''));
}
localizeNativeValidity();

function notify(message, isError = false) {
  toast.textContent = messageOf(message);
  toast.setAttribute('role', isError ? 'alert' : 'status');
  toast.className = `toast show${isError ? ' error' : ''}`;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => { toast.className = 'toast'; }, 4200);
}

async function api(path, options = {}) {
  const { method = 'GET', body, mutating = false } = options;
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (mutating) {
    headers['X-CSRF-Token'] = state.csrf;
    headers['Idempotency-Key'] = idempotencyKey();
  }
  let response;
  try {
    response = await fetch(`${state.apiBase}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'include',
    });
  } catch (error) {
    throw new Error(messageOf(error));
  }
  const payload = await response.json().catch(() => ({ code: 0, msg: '服务返回了不可读取的响应' }));
  if (!response.ok || payload.code !== 1) throw new Error(messageOf(payload.msg || `请求失败（${response.status}）`));
  return payload.data;
}

async function login(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  state.apiBase = String(form.get('apiBase')).replace(/\/$/, '');
  localStorage.setItem('sportsBookingApi', state.apiBase);
  const button = event.currentTarget.querySelector('button[type="submit"]');
  button.disabled = true;
  button.textContent = '验证中...';
  try {
    const response = await fetch(`${state.apiBase}/admin/auth/login`, {
      method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: form.get('username'), password: form.get('password'), localMfaCode: form.get('localMfaCode') }),
    });
    const body = await response.json();
    if (!response.ok || body.code !== 1) throw new Error(messageOf(body.msg || '登录失败'));
    state.csrf = response.headers.get('X-CSRF-Token') || '';
    sessionStorage.setItem('sportsBookingCsrf', state.csrf);
    await bootstrap();
  } catch (errorValue) {
    notify(errorValue, true);
    button.disabled = false;
    button.textContent = '进入管理台';
  }
}

async function bootstrap() {
  try {
    state.session = await api('/admin/auth/session');
    if (state.session.forcePasswordChange) {
      renderForcedPasswordChange();
      return;
    }
    syncRouteFromLocation();
    renderShell();
  } catch (_) {
    renderLogin();
  }
}

function syncRouteFromLocation() {
  const hashView = location.hash.split('/').pop()?.split('?')[0];
  const allowed = new Set([...navItems().map(item => item[0]), 'new-venue', 'venue-detail', 'order-detail', 'bill-detail']);
  state.view = allowed.has(hashView) ? hashView : (state.session.role === 'PLATFORM_ADMIN' ? 'overview' : 'venue-overview');
  state.selectedId = new URLSearchParams(location.hash.split('?')[1] || '').get('id');
}

function renderLogin() {
  app.innerHTML = `
    <section class="login-shell" aria-labelledby="login-title">
      <div class="login-backdrop" role="img" aria-label="室内篮球场"></div>
      <div class="login-layout"><div class="login-panel">
        <div class="brand login-brand"><span class="brand-mark">${icon('activity')}</span><span class="brand-copy"><strong>场务台</strong><small>SPORTS OPERATIONS</small></span></div>
        <form class="login-form" id="login-form" novalidate>
          <div class="login-panel-head"><span class="login-kicker">管理端登录</span><h1 id="login-title">欢迎回来</h1><p>使用平台管理员或场馆管理员账号继续。</p></div>
          <div class="field"><label for="username">账号</label><input id="username" name="username" required autocomplete="username"></div>
          <div class="field"><label for="password">密码</label><input id="password" name="password" type="password" required autocomplete="current-password"></div>
          <div class="field"><label for="local-mfa-code">平台二次验证码</label><input id="local-mfa-code" name="localMfaCode" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="one-time-code"><small>场馆管理员留空；本地开发默认 246810。</small></div>
          <button class="btn btn-primary btn-wide" type="submit">进入管理台</button>
          <details class="connection-settings">
            <summary>${icon('settings-2')}<span>连接设置</span><small>${escapeHtml(apiEndpointLabel())}</small></summary>
            <div class="connection-body"><div class="field"><label for="api-base">后端地址</label><input id="api-base" name="apiBase" value="${escapeHtml(state.apiBase)}" required autocomplete="url"><small>仅在后端服务地址变更时调整。</small></div></div>
          </details>
        </form>
        <div class="login-meta"><i></i>开发测试环境 · 模拟支付已启用</div>
      </div></div>
    </section>`;
  document.querySelector('#login-form').addEventListener('submit', login);
  refreshIcons();
}

function renderForcedPasswordChange() {
  app.innerHTML = `
    <section class="login-shell" aria-labelledby="password-change-title">
      <div class="login-backdrop" role="img" aria-label="室内篮球场"></div>
      <div class="login-layout"><div class="login-panel">
        <div class="brand login-brand"><span class="brand-mark">${icon('key-round')}</span><span class="brand-copy"><strong>场务台</strong><small>ACCOUNT SECURITY</small></span></div>
        <form class="login-form" id="forced-password-form" novalidate>
          <div class="login-panel-head"><span class="login-kicker">首次登录</span><h1 id="password-change-title">更新登录密码</h1><p>${escapeHtml(state.session.displayName)}，请验证初始密码并设置新密码后继续。</p></div>
          <div class="field"><label for="current-password">当前密码</label><input id="current-password" name="currentPassword" type="password" required autocomplete="current-password"></div>
          <div class="field"><label for="new-password">新密码</label><input id="new-password" name="newPassword" type="password" minlength="8" required autocomplete="new-password"><small>至少 8 个字符。</small></div>
          <div class="field"><label for="confirm-password">确认新密码</label><input id="confirm-password" name="confirmPassword" type="password" minlength="8" required autocomplete="new-password"></div>
          <button class="btn btn-primary btn-wide" type="submit">更新密码并进入工作台</button>
        </form>
        <div class="login-meta"><i></i>账号安全 · 必须完成此步骤</div>
      </div></div>
    </section>`;
  document.querySelector('#forced-password-form').addEventListener('submit', async event => {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const button = formElement.querySelector('button[type="submit"]');
    const body = Object.fromEntries(form.entries());
    if (body.newPassword !== body.confirmPassword) {
      notify('两次输入的新密码不一致', true);
      return;
    }
    button.disabled = true;
    button.textContent = '更新中...';
    try {
      await api('/admin/auth/password/change', { method: 'POST', mutating: true, body });
      state.session = await api('/admin/auth/session');
      if (state.session.forcePasswordChange) throw new Error('密码状态尚未更新，请重新登录后再试');
      state.view = state.session.role === 'PLATFORM_ADMIN' ? 'overview' : 'venue-overview';
      renderShell();
    } catch (errorValue) {
      notify(errorValue, true);
      button.disabled = false;
      button.textContent = '更新密码并进入工作台';
    }
  });
  refreshIcons();
}

function navItems() {
  const platform = state.session.role === 'PLATFORM_ADMIN';
  return platform
    ? [['overview', 'layout-dashboard', '运营概览', '总览'], ['venues', 'building-2', '场馆管理', '业务管理'], ['sport-categories', 'layers-3', '运动分类', '业务管理'], ['rental-types', 'tags', '出租类型', '业务管理'], ['orders', 'receipt-text', '全局订单', '交易中心'], ['bills', 'wallet-cards', '月账单', '交易中心'], ['ledgers', 'list-tree', '服务费分录', '交易中心'], ['audit', 'shield-check', '审计记录', '系统管理']]
    : [['venue-overview', 'layout-dashboard', '经营看板', '总览'], ['profile', 'building-2', '场馆资料', '场馆配置'], ['courts', 'goal', '物理场地', '场馆配置'], ['sellable-units', 'boxes', '可售单元', '场馆配置'], ['weekly-schedules', 'calendar-range', '周排期', '场馆配置'], ['special-dates', 'calendar-clock', '特殊日期', '场馆配置'], ['inventory', 'calendar-days', '库存日历', '场馆配置'], ['blocks', 'ban', '人工占用', '场馆配置'], ['orders', 'receipt-text', '订单管理', '履约与结算'], ['employees', 'badge-check', '店员管理', '履约与结算'], ['verifications', 'scan-line', '核销记录', '履约与结算'], ['bills', 'wallet-cards', '月账单', '履约与结算'], ['ledgers', 'list-tree', '服务费分录', '履约与结算'], ['gate', 'shield-alert', '交易门禁', '系统管理'], ['audit', 'shield-check', '操作记录', '系统管理']];
}

function navigate(view, id = null) {
  state.view = view;
  state.selectedId = id;
  const role = state.session.role === 'PLATFORM_ADMIN' ? 'platform' : 'venue';
  history.pushState(null, '', `#${role}/${view}${id ? `?id=${encodeURIComponent(id)}` : ''}`);
  renderShell();
}

window.addEventListener('popstate', () => {
  if (!state.session) return;
  syncRouteFromLocation();
  renderShell();
});

function routeId() {
  return state.selectedId || new URLSearchParams(location.hash.split('?')[1] || '').get('id');
}

function renderShell() {
  if (state.session?.forcePasswordChange) {
    renderForcedPasswordChange();
    return;
  }
  const nav = navItems().map(([key, glyph, label, group], index, items) => `${index === 0 || group !== items[index - 1][3] ? `<p class="nav-group">${group}</p>` : ''}<button data-view="${key}" ${state.view === key ? 'aria-current="page"' : ''}>${icon(glyph)}<span>${label}</span></button>`).join('');
  app.innerHTML = `<div class="app-shell">
    <aside class="sidebar"><div class="sidebar-brand"><div class="brand"><span class="brand-mark">${icon('activity')}</span><span class="brand-copy"><strong>场务台</strong><small>SPORTS OPS</small></span></div></div>
      <nav class="nav" aria-label="主导航">${nav}</nav>
      <div class="sidebar-footer"><span class="role-chip">${escapeHtml(label(state.session.role))}</span><div class="sidebar-user"><span>当前账号</span><strong>${escapeHtml(state.session.username)}</strong></div><button class="btn btn-secondary" id="logout">${icon('log-out')}<span>退出登录</span></button></div>
    </aside><section class="workbench"><header class="topbar"><div class="topbar-context"><strong>${state.session.role === 'PLATFORM_ADMIN' ? '平台运营中心' : '场馆运营中心'}</strong><span>预约、履约与结算控制台</span></div><div class="topbar-actions"><span class="topbar-meta"><i></i>会话有效</span><span class="topbar-avatar" aria-hidden="true">${escapeHtml(state.session.username).slice(0, 1).toUpperCase()}</span><span class="topbar-account">${escapeHtml(state.session.username)}</span></div></header><main id="main" class="content" tabindex="-1"></main></section>
  </div>`;
  document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => navigate(button.dataset.view)));
  document.querySelector('#logout').addEventListener('click', async () => {
    try { await api('/admin/auth/logout', { method: 'POST', mutating: true }); } catch (_) { /* Session may already be expired. */ }
    state.session = null; state.csrf = ''; sessionStorage.removeItem('sportsBookingCsrf'); renderLogin();
  });
  renderPage(); refreshIcons();
}

function pageHeading(title, description, action = '') { return `<header class="page-heading"><div><span class="page-kicker">OPERATIONS</span><h2>${title}</h2><p>${description}</p></div><div class="page-actions">${action}</div></header>`; }
function loading() { document.querySelector('#main').innerHTML = `<div class="stack"><div class="skeleton"></div><div class="skeleton"></div></div>`; }
function empty(message) { return `<div class="empty">${escapeHtml(message)}</div>`; }
function tableText(value) { return String(value).replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/\s+/g, ' ').trim(); }
function tableRow(row, headers) {
  let cellIndex = 0;
  return row.replace(/(<td\b[^>]*>)([\s\S]*?)(<\/td>)/g, (_, start, content, end) => {
    const cellLabel = tableText(headers[cellIndex++] || '');
    const labelledStart = start.replace(/>$/, ` data-label="${escapeHtml(cellLabel)}">`);
    if (/<(?:button|input|select|textarea)\b/i.test(content)) return `${labelledStart}<div class="table-actions">${content.replace(/<br\s*\/?>/gi, '')}</div>${end}`;
    const title = tableText(content);
    return `${labelledStart}<div class="table-cell"${title ? ` title="${escapeHtml(title)}"` : ''}>${content}</div>${end}`;
  });
}
function table(headers, rows, emptyText = '暂无数据') { return `<div class="table-wrap" role="region" aria-label="数据表格" tabindex="0"><table style="--table-columns:${headers.length}"><thead><tr>${headers.map(item => `<th scope="col">${item}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.map(row => tableRow(row, headers)).join('') : `<tr><td class="table-empty" colspan="${headers.length}">${empty(emptyText)}</td></tr>`}</tbody></table></div>`; }

function categoryTree(roots, statusFilter) {
  const matches = item => !statusFilter || item.status === statusFilter;
  const visibleRoots = roots.map(root => ({ ...root, children: root.children.filter(matches) })).filter(root => matches(root) || root.children.length);
  const expandableIds = visibleRoots.filter(root => root.children.length).map(root => root.id);
  if (state.expandedCategoryIds === null) state.expandedCategoryIds = new Set();
  const allExpanded = expandableIds.length > 0 && expandableIds.every(id => state.expandedCategoryIds.has(id));
  const nodeActions = item => `<div class="category-tree-actions"><button class="btn btn-secondary table-action" data-edit-category="${item.id}">编辑</button><button class="btn btn-danger table-action" data-category-status="${item.id}" data-next-status="${item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'}">${item.status === 'ACTIVE' ? '停用' : '启用'}</button></div>`;
  const childNode = item => `<div class="category-tree-row category-tree-child" role="treeitem" aria-level="2"><div class="category-tree-name"><span class="category-tree-line" aria-hidden="true"></span><span class="category-tree-label"><strong>${escapeHtml(item.name)}</strong><small>二级分类</small></span></div><span class="mono">${escapeHtml(item.code)}</span><span class="mono">${item.sortOrder}</span>${status(item.status)}${nodeActions(item)}</div>`;
  const branches = visibleRoots.map(root => {
    const expanded = state.expandedCategoryIds.has(root.id);
    const toggle = root.children.length ? `<button class="category-tree-toggle" type="button" data-category-toggle="${root.id}" aria-label="${expanded ? '收起' : '展开'} ${escapeHtml(root.name)}" title="${expanded ? '收起' : '展开'}">${icon(expanded ? 'chevron-down' : 'chevron-right')}</button>` : '<span class="category-tree-toggle-spacer" aria-hidden="true"></span>';
    return `<div class="category-branch" role="treeitem" aria-level="1"${root.children.length ? ` aria-expanded="${expanded}"` : ''}><div class="category-tree-row category-tree-parent"><div class="category-tree-name">${toggle}<span class="category-tree-label"><strong>${escapeHtml(root.name)}</strong><small>一级分类 · ${root.children.length} 个子分类</small></span></div><span class="mono">${escapeHtml(root.code)}</span><span class="mono">${root.sortOrder}</span>${status(root.status)}${nodeActions(root)}</div>${root.children.length ? `<div class="category-tree-group" role="group" ${expanded ? '' : 'hidden'}>${root.children.map(childNode).join('')}</div>` : ''}</div>`;
  }).join('');
  const matchingCount = roots.flatMap(root => [root, ...root.children]).filter(matches).length;
  const emptyState = `<div class="category-tree-empty" role="status">${icon('folder-tree')}<div><strong>${statusFilter ? '没有符合条件的分类' : '还没有运动分类'}</strong><p>${statusFilter ? '清除状态筛选后可查看完整分类树。' : '新建一级分类后，可以继续添加具体运动项目。'}</p></div><button class="btn btn-secondary" type="button" ${statusFilter ? 'id="clear-category-filter"' : 'data-add-category-empty'}>${statusFilter ? '清除筛选' : '新建运动分类'}</button></div>`;
  return { html: branches || emptyState, matchingCount, rootCount: visibleRoots.length, expandableIds, allExpanded };
}

async function renderPage() {
  loading();
  try {
    if (state.view === 'overview' || state.view === 'venue-overview') return await renderOverview();
    if (state.view === 'orders') return await renderOrders();
    if (state.view === 'bills') return await renderBills();
    if (state.view === 'ledgers') return await renderLedgers();
    if (state.view === 'audit') return await renderAudit();
    if (state.view === 'employees') return await renderEmployees();
    if (state.view === 'verifications') return await renderVerifications();
    if (state.view === 'profile') return await renderProfile();
    if (state.view === 'new-venue') return await renderNewVenue();
    if (state.view === 'venue-detail') return await renderVenueDetail();
    if (state.view === 'order-detail') return await renderOrderDetail();
    if (state.view === 'bill-detail') return await renderBillDetail();
    if (state.view === 'venues') return await renderVenues();
    if (state.view === 'sport-categories' || state.view === 'rental-types') return await renderCatalog();
    if (state.view === 'courts') return await renderCourts();
    if (state.view === 'sellable-units') return await renderSellableUnits();
    if (state.view === 'weekly-schedules') return await renderSchedules('weekly');
    if (state.view === 'special-dates') return await renderSchedules('special');
    if (state.view === 'inventory') return await renderInventory();
    if (state.view === 'blocks') return await renderBlocks();
    if (state.view === 'gate') return await renderGate();
  } catch (error) {
    document.querySelector('#main').innerHTML = `${pageHeading('无法加载此页面', '请检查后端地址、登录状态和网络连接。')}<div class="panel"><div class="empty">${escapeHtml(messageOf(error))}<br><button class="btn btn-secondary retry-button" id="retry">重新加载</button></div></div>`;
    document.querySelector('#retry').addEventListener('click', renderPage);
  }
}

async function renderOverview() {
  const platform = state.session.role === 'PLATFORM_ADMIN';
  const root = platform ? '/admin' : '/venue';
  const [summary, orders, bills] = await Promise.all([api(`${root}/dashboard/summary`), api(`${root}/orders?page=1&pageSize=5`), api(`${root}/billing/monthly-bills?page=1&pageSize=5`)]);
  const metrics = platform
    ? [['building-2', '场馆总数', summary.venueCount, `${summary.activeVenueCount} 家正常营业`], ['receipt-text', '订单总数', summary.orderCount, '覆盖全部历史订单'], ['clock-3', '当前待处理', summary.pendingOrderCount, `${summary.paymentExceptionCount} 笔支付待确认`], ['circle-alert', '待结算账单', summary.unpaidBillCount, '已签发或已逾期']]
    : [['calendar-check-2', '今日订单', summary.todayOrderCount, '按预约日期统计'], ['scan-line', '待核销', summary.pendingVerificationCount, `${summary.verifiedCount} 笔今日已核销`], ['gauge', '今日利用率', `${summary.utilizationRate}%`, `${summary.availableSlotCount} / ${summary.inventorySlotCount} 时段可用`], ['circle-check-big', '今日已完成', summary.completedCount, '已完成履约订单']];
  document.querySelector('#main').innerHTML = `${pageHeading(platform ? '平台运营概览' : '场馆经营看板', platform ? '跨场馆订单、结算与履约情况一览。' : '聚合今日履约、库存和核销信息。')}
    <section class="overview-brief"><div><span class="overview-label">数据快照</span><strong>${platform ? `${summary.pendingOrderCount} 项订单待处理` : `${summary.pendingVerificationCount} 笔订单待核销`}</strong><p>更新时间 ${escapeHtml(summary.generatedAt)}</p></div><button class="btn btn-secondary table-action" data-goto="orders">查看订单</button></section>
    <div class="metrics">${metrics.map(([glyph, name, value, note], index) => `<article class="metric"><span class="metric-icon ${index === 1 ? 'warning' : index === 2 ? 'blue' : index === 3 ? 'danger' : ''}">${icon(glyph)}</span><div><span>${name}</span><strong>${value}</strong><em>${note}</em></div></article>`).join('')}</div>
    <div class="content-grid"><section class="panel"><div class="panel-head"><h3>最近订单</h3><button class="btn btn-secondary table-action" data-goto="orders">查看全部</button></div>${table(['订单','场馆与时段','状态','金额'], orders.records.map(item => `<tr><td class="mono">${escapeHtml(item.orderNo)}</td><td>${escapeHtml(item.storeName)} / ${escapeHtml(item.sellableUnitName)}<br><span class="mono">${item.date} ${item.startTime}-${item.endTime}</span></td><td>${status(item.orderStatus)}</td><td class="mono">${money(item.priceFen)}</td></tr>`), '暂无订单')}</section><section class="panel"><div class="panel-head"><h3>最近账单</h3><button class="btn btn-secondary table-action" data-goto="bills">查看全部</button></div>${table(['账单','门店','状态','应付'], bills.records.map(item => `<tr><td class="mono">${escapeHtml(item.billNo)}</td><td>${escapeHtml(item.storeName)}</td><td>${status(item.billStatus)}</td><td class="mono">${money(item.payableFeeFen)}</td></tr>`), '暂无账单')}</section></div>`;
  document.querySelectorAll('[data-goto]').forEach(button => button.addEventListener('click', () => navigate(button.dataset.goto)));
  refreshIcons();
}

async function renderVenues() {
  const data = await api(`/admin/organizations?${filteredPageQuery('venues')}`);
  const gateResults = await Promise.all(data.records.map(item => api(`/admin/stores/${item.storeId}/transaction-gate`).catch(() => null)));
  const rows = data.records.map((item, index) => {
    const gate = gateResults[index];
    const active = gate?.storeGate?.blockers?.filter(blocker => blocker.blockerStatus === 'ACTIVE') || [];
    return `<tr><td><strong>${escapeHtml(item.storeName)}</strong><br><span class="mono">${escapeHtml(item.storeNo)}</span></td><td>${escapeHtml(item.organizationName)}<br><span class="mono">${escapeHtml(item.organizationNo)}</span></td><td>${escapeHtml(item.adminDisplayName || '-')}<br><span class="mono">${escapeHtml(item.adminUsername || '-')}</span></td><td>${status(item.organizationStatus)} ${status(item.storeStatus)}</td><td>${gate ? status(gate.allowsNewTransaction ? 'OPEN' : 'BLOCKED') : '-'}<br><span class="mono">${active.length} 个活动原因</span></td><td class="mono">${shortDate(item.createTime)}</td><td><button class="btn btn-secondary table-action" data-venue-detail="${item.organizationId}">详情</button> <button class="btn btn-secondary table-action" data-venue-gate="${item.storeId}" data-venue-name="${escapeHtml(item.storeName)}">门店门禁</button> <button class="btn btn-secondary table-action" data-org-gate="${item.organizationId}" data-venue-name="${escapeHtml(item.organizationName)}">主体门禁</button> <button class="btn btn-secondary table-action" data-reset-password="${item.organizationId}" data-venue-name="${escapeHtml(item.storeName)}">重置密码</button><br><button class="btn btn-danger table-action" data-store-status="${item.storeId}" data-next-status="${item.storeStatus === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'}">${item.storeStatus === 'ACTIVE' ? '暂停门店' : '恢复门店'}</button> <button class="btn btn-danger table-action" data-org-status="${item.organizationId}" data-next-status="${item.organizationStatus === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'}">${item.organizationStatus === 'ACTIVE' ? '暂停主体' : '恢复主体'}</button></td></tr>`;
  });
  document.querySelector('#main').innerHTML = `${pageHeading('场馆管理', '主体和门店状态与交易门禁共同决定是否允许新增预约。', `<button class="btn btn-primary" data-goto="new-venue">${icon('plus-square')}新建场馆</button>`)}
    <section class="panel"><form class="panel-body filters" data-filter-key="venues"><div class="field"><label for="venue-keyword">名称或编号</label><input id="venue-keyword" name="keyword" value="${escapeHtml(filterValue('venues', 'keyword'))}" placeholder="场馆、主体或编号"></div><div class="field"><label for="venue-status">经营状态</label><select id="venue-status" name="status"><option value="">全部状态</option>${['ACTIVE', 'PAUSED'].map(value => `<option value="${value}" ${filterValue('venues', 'status') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>经营主体与门店</h3><span class="mono">${data.total} 家</span></div>${table(['场馆','经营主体','管理员','主体 / 门店','交易门禁','创建时间','操作'], rows, '暂无场馆，请先新建场馆。')}${pager(data, 'venues')}</section>`;
  document.querySelector('[data-goto]')?.addEventListener('click', () => navigate('new-venue'));
  document.querySelectorAll('[data-venue-detail]').forEach(button => button.addEventListener('click', () => navigate('venue-detail', button.dataset.venueDetail)));
  document.querySelectorAll('[data-venue-gate]').forEach(button => button.addEventListener('click', () => openGateModal(`/admin/stores/${button.dataset.venueGate}`, button.dataset.venueName, 'storeGate')));
  document.querySelectorAll('[data-org-gate]').forEach(button => button.addEventListener('click', () => openGateModal(`/admin/organizations/${button.dataset.orgGate}`, button.dataset.venueName, 'organizationGate')));
  document.querySelectorAll('[data-reset-password]').forEach(button => button.addEventListener('click', () => openResetPasswordModal(button.dataset.resetPassword, button.dataset.venueName)));
  document.querySelectorAll('[data-store-status]').forEach(button => button.addEventListener('click', () => updateVenueStatus(`/admin/stores/${button.dataset.storeStatus}/status`, button.dataset.nextStatus, '门店', renderVenues)));
  document.querySelectorAll('[data-org-status]').forEach(button => button.addEventListener('click', () => updateVenueStatus(`/admin/organizations/${button.dataset.orgStatus}/status`, button.dataset.nextStatus, '经营主体', renderVenues)));
  bindFilters('venues', renderVenues);
  bindPager('venues', renderVenues);
  refreshIcons();
}

async function renderVenueDetail() {
  const data = await api(`/admin/organizations/${routeId()}`);
  const gate = data.transactionGate;
  const activeBlockers = [...(gate.organizationGate.blockers || []), ...(gate.storeGate.blockers || [])].filter(item => item.blockerStatus === 'ACTIVE');
  document.querySelector('#main').innerHTML = `${pageHeading(data.storeName, '经营主体、门店管理员与交易状态详情。', `<button class="btn btn-secondary" id="back-venues">${icon('arrow-left')}返回场馆列表</button>`)}
    <div class="detail-grid"><section class="panel"><div class="panel-head"><h3>基础信息</h3>${status(data.storeStatus)}</div><dl class="detail-list"><dt>场馆编号</dt><dd class="mono">${escapeHtml(data.storeNo)}</dd><dt>经营主体</dt><dd>${escapeHtml(data.organizationName)}<br><span class="mono">${escapeHtml(data.organizationNo)}</span></dd><dt>创建时间</dt><dd class="mono">${shortDate(data.createTime)}</dd><dt>服务费率</dt><dd class="mono">${data.serviceRateBasisPoints} bp</dd></dl></section>
    <section class="panel"><div class="panel-head"><h3>管理员</h3>${status(data.adminStatus)}</div><dl class="detail-list"><dt>姓名</dt><dd>${escapeHtml(data.adminDisplayName || '-')}</dd><dt>账号</dt><dd class="mono">${escapeHtml(data.adminUsername || '-')}</dd><dt>首次改密</dt><dd>${data.forcePasswordChange ? '待完成' : '已完成'}</dd></dl></section></div>
    <section class="panel"><div class="panel-head"><h3>交易门禁</h3>${status(gate.allowsNewTransaction ? 'OPEN' : 'BLOCKED')}</div>${table(['范围','状态','版本','活动原因'], [gate.organizationGate, gate.storeGate].map(item => `<tr><td>${item.scopeType === 'ORGANIZATION' ? '经营主体' : '门店'}</td><td>${status(item.gateStatus)}</td><td class="mono">v${item.gateVersion}</td><td>${item.blockers.filter(blocker => blocker.blockerStatus === 'ACTIVE').map(blocker => escapeHtml(blocker.reason)).join('<br>') || '-'}</td></tr>`))}<div class="panel-body"><button class="btn btn-secondary" id="detail-gate">${icon('shield-alert')}管理门店门禁</button> <span class="mono">${activeBlockers.length} 个活动原因</span></div></section>`;
  document.querySelector('#back-venues').addEventListener('click', () => navigate('venues'));
  document.querySelector('#detail-gate').addEventListener('click', () => openGateModal(`/admin/stores/${data.storeId}`, data.storeName, 'storeGate'));
  refreshIcons();
}

function updateVenueStatus(path, nextStatus, label, refresh) {
  openModal(`${nextStatus === 'PAUSED' ? '暂停' : '恢复'}${label}`, `<p>${nextStatus === 'PAUSED' ? '暂停后只阻止新增预约，存量订单仍可履约。' : '恢复不会自动解除其他交易暂停原因。'}</p>${nextStatus === 'PAUSED' ? '<div class="field"><label for="status-reason">暂停原因</label><textarea id="status-reason" maxlength="255" required></textarea></div>' : ''}<div class="field-error" role="alert"></div>`, async node => {
    const reason = node.querySelector('#status-reason')?.value.trim() || null; if (nextStatus === 'PAUSED' && !reason) throw new Error('请填写暂停原因');
    await api(path, { method: 'PATCH', mutating: true, body: { status: nextStatus, reason } }); notify(`${label}已${nextStatus === 'PAUSED' ? '暂停' : '恢复'}`); refresh();
  }, '确认');
}

function openResetPasswordModal(organizationId, venueName) {
  openModal(`重置 ${venueName} 管理员密码`, `<div class="field"><label for="reset-password">新临时密码</label><input id="reset-password" type="password" minlength="8" autocomplete="new-password" required><small>重置后将撤销该管理员的所有既有会话，并要求下次登录修改密码。</small></div><div class="field-error" role="alert"></div>`, async node => {
    const newPassword = node.querySelector('#reset-password').value; if (!newPassword) throw new Error('请填写新临时密码');
    await api(`/admin/organizations/${organizationId}/admin-password/reset`, { method: 'POST', mutating: true, body: { newPassword } }); notify('管理员密码已重置');
  }, '重置');
}

async function openGateModal(scopePath, venueName, gateKey) {
  const gate = await api(`${scopePath}/transaction-gate`);
  const selectedGate = gate[gateKey];
  const blockers = selectedGate.blockers || [];
  const node = openModal(`${venueName} 交易门禁`, `<div class="stack"><div>${status(selectedGate.gateStatus)} <span class="mono">v${selectedGate.gateVersion}</span></div><div class="field"><label for="blocker-reason">新增暂停原因</label><textarea id="blocker-reason" maxlength="255" required></textarea></div>${table(['原因', '状态', '创建时间', '操作'], blockers.map(blocker => `<tr><td>${escapeHtml(blocker.reason || blocker.causeType)}</td><td>${status(blocker.blockerStatus)}</td><td class="mono">${shortDate(blocker.createTime)}</td><td>${blocker.blockerStatus === 'ACTIVE' ? `<button class="btn btn-secondary table-action" data-release="${blocker.id}">解除</button>` : '-'}</td></tr>`), '暂无暂停原因')}<div class="field-error" role="alert"></div></div>`, async modal => {
    const reason = modal.querySelector('#blocker-reason').value.trim(); if (!reason) throw new Error('请填写暂停原因');
    await api(`${scopePath}/transaction-blockers`, { method: 'POST', mutating: true, body: { reason } }); notify('交易暂停原因已新增'); renderPage();
  }, '新增暂停');
  node.querySelectorAll('[data-release]').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try { await api(`${scopePath}/transaction-blockers/${button.dataset.release}/release`, { method: 'POST', mutating: true }); node.close(); notify('交易暂停原因已解除'); renderPage(); } catch (error) { button.disabled = false; notify(error, true); }
  }));
}

async function renderCatalog() {
  const showingCategories = state.view === 'sport-categories';
  const key = showingCategories ? 'catalogCategories' : 'catalogRentals';
  const endpoint = showingCategories ? 'sport-categories' : 'rental-types';
  const data = showingCategories ? await api('/admin/catalog/sport-categories/tree') : await api(`/admin/catalog/${endpoint}?${filteredPageQuery(key)}`);
  const allCategories = showingCategories ? data.flatMap(root => [root, ...root.children]) : [];
  const tree = showingCategories ? categoryTree(data, filterValue(key, 'status')) : null;
  const title = showingCategories ? '运动分类' : '出租类型';
  const addId = showingCategories ? 'add-category' : 'add-rental';
  document.querySelector('#main').innerHTML = `${pageHeading(title, `停用后的${title}只保留历史引用，不再允许新场地使用。`)}
    <section class="panel table-panel"><div class="panel-head"><div><h3>${showingCategories ? '分类结构' : `${title}列表`}</h3><span class="panel-count">${showingCategories ? `${tree.matchingCount} 条${filterValue(key, 'status') ? '匹配' : '分类'} · ${tree.rootCount} 个一级分类` : `${data.total} 条记录`}</span></div><div class="category-tree-head-actions">${showingCategories && tree.expandableIds.length ? `<button class="btn btn-secondary" id="toggle-all-categories">${icon(tree.allExpanded ? 'chevrons-up' : 'chevrons-down')}${tree.allExpanded ? '全部收起' : '全部展开'}</button>` : ''}<button class="btn btn-primary" id="${addId}">${icon('plus')}新建${title}</button></div></div>
      <form class="panel-body filters table-toolbar" data-filter-key="${key}"><div class="field"><label for="catalog-status">状态</label><select id="catalog-status" name="status"><option value="">全部状态</option>${['ACTIVE', 'DISABLED'].map(value => `<option value="${value}" ${filterValue(key, 'status') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form>
      ${showingCategories
        ? `<div class="category-tree" role="tree" aria-label="运动分类树"><div class="category-tree-header" aria-hidden="true"><span>分类结构</span><span>编码</span><span>排序</span><span>状态</span><span>操作</span></div>${tree.html}</div>`
        : table(['名称','运动分类','状态','操作'], data.records.map(item => `<tr><td><strong>${escapeHtml(item.name)}</strong></td><td>${escapeHtml(item.sportCategoryName)}</td><td>${status(item.status)}</td><td><button class="btn btn-secondary table-action" data-edit-rental="${item.id}">编辑</button> <button class="btn btn-danger table-action" data-rental-status="${item.id}" data-next-status="${item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'}">${item.status === 'ACTIVE' ? '停用' : '启用'}</button></td></tr>`))}
      ${showingCategories ? '' : pager(data, key)}</section>`;
  const categoryOptions = async () => (await api('/admin/catalog/sport-categories/tree')).flatMap(root => [root, ...root.children]);
  document.querySelector('#add-category')?.addEventListener('click', async () => openCategoryModal(await categoryOptions()));
  document.querySelector('[data-add-category-empty]')?.addEventListener('click', async () => openCategoryModal(await categoryOptions()));
  document.querySelector('#clear-category-filter')?.addEventListener('click', () => { state.filters[key] = {}; renderCatalog(); });
  document.querySelectorAll('[data-edit-category]').forEach(button => button.addEventListener('click', async () => { const options = await categoryOptions(); openCategoryModal(options, allCategories.find(item => String(item.id) === button.dataset.editCategory)); }));
  document.querySelector('#add-rental')?.addEventListener('click', async () => openRentalModal(await categoryOptions()));
  document.querySelectorAll('[data-edit-rental]').forEach(button => button.addEventListener('click', async () => openRentalModal(await categoryOptions(), data.records.find(item => String(item.id) === button.dataset.editRental))));
  document.querySelectorAll('[data-category-toggle]').forEach(button => button.addEventListener('click', () => { const id = button.dataset.categoryToggle; state.expandedCategoryIds.has(id) ? state.expandedCategoryIds.delete(id) : state.expandedCategoryIds.add(id); renderCatalog(); }));
  document.querySelector('#toggle-all-categories')?.addEventListener('click', () => { state.expandedCategoryIds = tree.allExpanded ? new Set() : new Set(tree.expandableIds); renderCatalog(); });
  document.querySelectorAll('[data-category-status]').forEach(button => button.addEventListener('click', () => updateCatalogStatus(`/admin/catalog/sport-categories/${button.dataset.categoryStatus}/status`, button.dataset.nextStatus)));
  document.querySelectorAll('[data-rental-status]').forEach(button => button.addEventListener('click', () => updateCatalogStatus(`/admin/catalog/rental-types/${button.dataset.rentalStatus}/status`, button.dataset.nextStatus)));
  bindFilters(key, renderCatalog);
  if (!showingCategories) bindPager(key, renderCatalog);
  refreshIcons();
}

function updateCatalogStatus(path, statusValue) {
  openModal(`${statusValue === 'ACTIVE' ? '启用' : '停用'}字典项`, '<p>历史订单和资源引用不会受影响。</p><div class="field-error" role="alert"></div>', async () => {
    await api(path, { method: 'PATCH', mutating: true, body: { status: statusValue } }); notify('字典状态已更新'); renderCatalog();
  });
}

function openCategoryModal(categories, current) {
  const options = ['<option value="">一级分类</option>', ...categories.filter(item => item.level === 1 && item.id !== current?.id).map(item => `<option value="${item.id}" ${current?.parentId === item.id ? 'selected' : ''}>${escapeHtml(item.name)}</option>`)].join('');
  openModal(current ? '编辑运动分类' : '新建运动分类', `<div class="field"><label for="category-parent">上级分类</label><select id="category-parent">${options}</select></div><div class="field"><label for="category-code">编码</label><input id="category-code" maxlength="32" value="${escapeHtml(current?.code || '')}" required></div><div class="field"><label for="category-name">名称</label><input id="category-name" maxlength="64" value="${escapeHtml(current?.name || '')}" required></div><div class="field"><label for="category-sort">排序</label><input id="category-sort" type="number" min="0" value="${current?.sortOrder ?? 0}" required></div><div class="field-error" role="alert"></div>`, async node => {
    const body = { parentId: node.querySelector('#category-parent').value || null, code: node.querySelector('#category-code').value.trim(), name: node.querySelector('#category-name').value.trim(), icon: null, sortOrder: Number(node.querySelector('#category-sort').value) };
    if (!body.code || !body.name) throw new Error('请填写编码和名称');
    await api(current ? `/admin/catalog/sport-categories/${current.id}` : '/admin/catalog/sport-categories', { method: current ? 'PUT' : 'POST', mutating: true, body }); notify('运动分类已保存'); renderCatalog();
  }, '保存');
}

function openRentalModal(categories, current) {
  const options = categories.filter(item => item.level === 2 && item.status === 'ACTIVE').map(item => `<option value="${item.id}" ${current?.sportCategoryId === item.id ? 'selected' : ''}>${escapeHtml(item.name)}</option>`).join('');
  openModal(current ? '编辑出租类型' : '新建出租类型', `<div class="field"><label for="rental-category">运动分类</label><select id="rental-category" required><option value="">请选择</option>${options}</select></div><div class="field"><label for="rental-name">名称</label><input id="rental-name" maxlength="64" value="${escapeHtml(current?.name || '')}" required></div><div class="field-error" role="alert"></div>`, async node => {
    const body = { sportCategoryId: node.querySelector('#rental-category').value, name: node.querySelector('#rental-name').value.trim() }; if (!body.sportCategoryId || !body.name) throw new Error('请完成出租类型信息');
    await api(current ? `/admin/catalog/rental-types/${current.id}` : '/admin/catalog/rental-types', { method: current ? 'PUT' : 'POST', mutating: true, body }); notify('出租类型已保存'); renderCatalog();
  }, '保存');
}

async function renderOrders() {
  const root = state.session.role === 'PLATFORM_ADMIN' ? '/admin' : '/venue';
  const data = await api(`${root}/orders?${filteredPageQuery('orders')}`);
  document.querySelector('#main').innerHTML = `${pageHeading(state.session.role === 'PLATFORM_ADMIN' ? '全局订单' : '订单管理', '默认展示脱敏联系方式；查看完整号码会留下审计记录。')}
    <section class="panel"><form class="panel-body filters" data-filter-key="orders"><div class="field"><label for="order-no">订单号</label><input id="order-no" name="orderNo" value="${escapeHtml(filterValue('orders', 'orderNo'))}" placeholder="支持模糊匹配"></div>${state.session.role === 'PLATFORM_ADMIN' ? `<div class="field"><label for="order-keyword">场馆</label><input id="order-keyword" name="keyword" value="${escapeHtml(filterValue('orders', 'keyword'))}" placeholder="场馆名称"></div>` : ''}<div class="field"><label for="order-status">订单状态</label><select id="order-status" name="orderStatus"><option value="">全部状态</option>${['PENDING_PAYMENT','PAYMENT_PROCESSING','PAYMENT_UNKNOWN','CONFIRMED','COMPLETED','CANCELLED'].map(value => `<option value="${value}" ${filterValue('orders', 'orderStatus') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><div class="field"><label for="payment-status">支付状态</label><select id="payment-status" name="paymentStatus"><option value="">全部状态</option>${['CREATED','PROCESSING','SUCCEEDED','FAILED','UNKNOWN','CLOSED'].map(value => `<option value="${value}" ${filterValue('orders', 'paymentStatus') === value ? 'selected' : ''}>${value === 'CLOSED' ? '已关闭' : label(value)}</option>`).join('')}</select></div><div class="field"><label for="date-from">预约日期从</label><input id="date-from" name="dateFrom" type="date" value="${escapeHtml(filterValue('orders', 'dateFrom'))}"></div><div class="field"><label for="date-to">至</label><input id="date-to" name="dateTo" type="date" value="${escapeHtml(filterValue('orders', 'dateTo'))}"></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>订单列表</h3><span class="mono">${data.total} 条</span></div>${table(['订单','用户','场地时段','支付 / 退款','核销','订单状态','操作'], data.records.map(item => `<tr><td class="mono">${escapeHtml(item.orderNo)}</td><td>${escapeHtml(item.contactName)}<br><span class="mono">${escapeHtml(item.contactPhone)}</span></td><td>${escapeHtml(item.storeName)} / ${escapeHtml(item.sellableUnitName)}<br><span class="mono">${item.date} ${item.startTime}-${item.endTime}</span></td><td>${status(item.paymentStatus)} ${item.refundStatus ? `<br>${status(item.refundStatus)}` : ''}</td><td>${status(item.verificationStatus)}</td><td>${status(item.orderStatus)}</td><td><button class="btn btn-secondary table-action" data-order-detail="${item.id}">详情</button> <button class="btn btn-secondary table-action" data-phone="${item.id}" data-order="${escapeHtml(item.orderNo)}">查看号码</button>${state.session.role === 'VENUE_ADMIN' && ['PENDING_PAYMENT', 'CONFIRMED'].includes(item.orderStatus) ? ` <button class="btn btn-danger table-action" data-cancel-order="${item.id}" data-order="${escapeHtml(item.orderNo)}">取消</button>` : ''}</td></tr>`))}${pager(data, 'orders')}</section>`;
  bindFilters('orders', renderOrders);
  document.querySelectorAll('[data-phone]').forEach(button => button.addEventListener('click', () => openPhoneModal(root, button.dataset.phone, button.dataset.order)));
  document.querySelectorAll('[data-order-detail]').forEach(button => button.addEventListener('click', () => navigate('order-detail', button.dataset.orderDetail)));
  document.querySelectorAll('[data-cancel-order]').forEach(button => button.addEventListener('click', () => openVenueCancelOrderModal(button.dataset.cancelOrder, button.dataset.order)));
  bindPager('orders', renderOrders);
}

async function renderOrderDetail() {
  const root = state.session.role === 'PLATFORM_ADMIN' ? '/admin' : '/venue';
  const data = await api(`${root}/orders/${routeId()}`);
  document.querySelector('#main').innerHTML = `${pageHeading(`订单 ${escapeHtml(data.orderNo)}`, '订单、资金、核销与取消事实。', `<button class="btn btn-secondary" id="back-orders">${icon('arrow-left')}返回订单列表</button>`)}
    <div class="detail-grid"><section class="panel"><div class="panel-head"><h3>预约信息</h3>${status(data.orderStatus)}</div><dl class="detail-list"><dt>场馆</dt><dd>${escapeHtml(data.storeName)}</dd><dt>场地</dt><dd>${escapeHtml(data.sellableUnitName)}</dd><dt>预约时段</dt><dd class="mono">${data.date} ${data.startTime}-${data.endTime}</dd><dt>订单金额</dt><dd class="mono">${money(data.priceFen)}</dd><dt>联系人</dt><dd>${escapeHtml(data.contactName)} · <span class="mono">${escapeHtml(data.contactPhone)}</span></dd></dl></section>
    <section class="panel"><div class="panel-head"><h3>资金与履约</h3></div><dl class="detail-list"><dt>支付</dt><dd>${status(data.paymentStatus)} ${data.paymentId ? `<span class="mono">#${data.paymentId}</span>` : ''}</dd><dt>退款</dt><dd>${data.refundStatus ? status(data.refundStatus) : '-'}</dd><dt>核销</dt><dd>${status(data.verificationStatus)}${data.verifiedEmployeeName ? ` · ${escapeHtml(data.verifiedEmployeeName)}` : ''}</dd><dt>取消原因</dt><dd>${escapeHtml(data.cancellation?.reason || '-')}</dd></dl></section></div>
    <section class="panel"><div class="panel-head"><h3>状态时间线</h3><span class="mono">${data.events.length} 个事件</span></div><ol class="timeline">${data.events.map(item => `<li><i></i><div><strong>${escapeHtml(label(item.type))}</strong><span class="mono">${escapeHtml(item.time)}</span></div></li>`).join('')}</ol></section>`;
  document.querySelector('#back-orders').addEventListener('click', () => navigate('orders'));
  refreshIcons();
}

function openVenueCancelOrderModal(orderId, orderNo) {
  openModal(`取消 ${orderNo}`, '<div class="field"><label for="venue-cancel-reason">取消原因</label><textarea id="venue-cancel-reason" maxlength="255" required></textarea></div><div class="field-error" role="alert"></div>', async node => {
    const reason = node.querySelector('#venue-cancel-reason').value.trim(); if (!reason) throw new Error('请填写取消原因');
    await api(`/venue/orders/${orderId}/cancel`, { method: 'POST', mutating: true, body: { reason } }); notify('订单已取消，退款将由模拟支付渠道继续处理'); renderOrders();
  }, '确认取消');
}

async function renderBills() {
  const root = state.session.role === 'PLATFORM_ADMIN' ? '/admin' : '/venue';
  const bills = await api(`${root}/billing/monthly-bills?${filteredPageQuery('bills')}`);
  const generate = state.session.role === 'PLATFORM_ADMIN' ? `<button class="btn btn-primary" id="generate-bill">${icon('file-plus-2')}生成当期账单</button>` : '';
  document.querySelector('#main').innerHTML = `${pageHeading('月账单', '账单收款以线下记录为准。', generate)}
    <section class="panel"><form class="panel-body filters" data-filter-key="bills"><div class="field"><label for="bill-period">账期</label><input id="bill-period" name="billingPeriod" type="month" value="${escapeHtml(filterValue('bills', 'billingPeriod'))}"></div><div class="field"><label for="bill-status">账单状态</label><select id="bill-status" name="billStatus"><option value="">全部状态</option>${['DRAFT','ISSUED','PAID','OVERDUE'].map(value => `<option value="${value}" ${filterValue('bills', 'billStatus') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选账单</button></form></section>
    <section class="panel"><div class="panel-head"><h3>月账单列表</h3><span class="mono">${bills.total} 条</span></div>${table(['账单','门店','账期','正向 / 调整','应付','状态','时间','操作'], bills.records.map(item => `<tr><td class="mono">${escapeHtml(item.billNo)}</td><td>${escapeHtml(item.storeName)}</td><td class="mono">${item.billingPeriod}</td><td class="mono">${money(item.accrualFeeFen)} / ${money(item.adjustmentFeeFen)}</td><td class="mono">${money(item.payableFeeFen)}</td><td>${status(item.billStatus)}</td><td>${shortDate(item.paidAt || item.issuedAt || item.createTime)}</td><td><button class="btn btn-secondary table-action" data-bill-detail="${item.id}">详情</button> ${state.session.role === 'PLATFORM_ADMIN' && item.billStatus === 'DRAFT' ? `<button class="btn btn-secondary table-action" data-issue-bill="${item.id}">签发</button>` : ''}${state.session.role === 'PLATFORM_ADMIN' && ['ISSUED', 'OVERDUE'].includes(item.billStatus) ? ` <button class="btn btn-primary table-action" data-pay-bill="${item.id}" data-payable="${item.payableFeeFen}">登记收款</button>` : ''}</td></tr>`))}${pager(bills, 'bills')}</section>`;
  document.querySelector('#generate-bill')?.addEventListener('click', () => openGenerateBillModal()); refreshIcons();
  document.querySelectorAll('[data-issue-bill]').forEach(button => button.addEventListener('click', () => openIssueBillModal(button.dataset.issueBill)));
  document.querySelectorAll('[data-pay-bill]').forEach(button => button.addEventListener('click', () => openRecordBillPaymentModal(button.dataset.payBill, button.dataset.payable)));
  document.querySelectorAll('[data-bill-detail]').forEach(button => button.addEventListener('click', () => navigate('bill-detail', button.dataset.billDetail)));
  bindFilters('bills', renderBills); bindPager('bills', renderBills);
}

async function renderBillDetail() {
  const root = state.session.role === 'PLATFORM_ADMIN' ? '/admin' : '/venue';
  const data = await api(`${root}/billing/monthly-bills/${routeId()}`);
  document.querySelector('#main').innerHTML = `${pageHeading(`账单 ${escapeHtml(data.billNo)}`, `${escapeHtml(data.storeName)} · ${data.billingPeriod}`, `<button class="btn btn-secondary" id="back-bills">${icon('arrow-left')}返回账单列表</button>`)}
    <div class="metrics metrics-three"><article class="metric"><div><span>正向服务费</span><strong>${money(data.accrualFeeFen)}</strong></div></article><article class="metric"><div><span>退款调整</span><strong>${money(data.adjustmentFeeFen)}</strong></div></article><article class="metric"><div><span>应付金额</span><strong>${money(data.payableFeeFen)}</strong><em>${label(data.billStatus)}</em></div></article></div>
    <section class="panel"><div class="panel-head"><h3>服务费分录</h3><span class="mono">${data.ledgerTotal} 条</span></div>${table(['分录','订单','类型','计费基数','费率','服务费'], data.ledgers.map(item => `<tr><td class="mono">${escapeHtml(item.entryNo)}</td><td class="mono">${escapeHtml(item.orderNo)}</td><td>${status(item.entryType)}</td><td class="mono">${money(item.baseAmountFen)}</td><td class="mono">${item.serviceRateBasisPoints} bp</td><td class="mono">${money(item.feeAmountFen)}</td></tr>`), '暂无分录')}</section>
    <section class="panel"><div class="panel-head"><h3>收款记录</h3></div>${table(['收款编号','方式','金额','时间','备注'], data.payments.map(item => `<tr><td class="mono">${escapeHtml(item.paymentNo)}</td><td>${escapeHtml(item.paymentMethod)}</td><td class="mono">${money(item.amountFen)}</td><td class="mono">${shortDate(item.paidAt)}</td><td>${escapeHtml(item.remark || '-')}</td></tr>`), '暂无收款记录')}</section>`;
  document.querySelector('#back-bills').addEventListener('click', () => navigate('bills'));
  refreshIcons();
}

async function renderLedgers() {
  const root = state.session.role === 'PLATFORM_ADMIN' ? '/admin' : '/venue';
  const ledgers = await api(`${root}/billing/fee-ledgers?${filteredPageQuery('ledgers')}`);
  document.querySelector('#main').innerHTML = `${pageHeading('服务费分录', '服务费分录由订单结算自动生成，不允许手工编辑。')}
    <section class="panel"><form class="panel-body filters" data-filter-key="ledgers"><div class="field"><label for="ledger-period">账期</label><input id="ledger-period" name="billingPeriod" type="month" value="${escapeHtml(filterValue('ledgers', 'billingPeriod'))}"></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选分录</button></form></section>
    <section class="panel"><div class="panel-head"><h3>服务费分录列表</h3><span class="mono">${ledgers.total} 条</span></div>${table(['分录','订单','类型','计费基数','费率','服务费'], ledgers.records.map(item => `<tr><td class="mono">${escapeHtml(item.entryNo)}</td><td class="mono">${escapeHtml(item.orderNo)}</td><td>${status(item.entryType)}</td><td class="mono">${money(item.baseAmountFen)}</td><td class="mono">${item.serviceRateBasisPoints} 个基点</td><td class="mono">${money(item.feeAmountFen)}</td></tr>`))}${pager(ledgers, 'ledgers')}</section>`;
  bindFilters('ledgers', renderLedgers); bindPager('ledgers', renderLedgers);
}

function openIssueBillModal(billId) {
  const dueDate = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
  openModal('签发月账单', `<div class="field"><label for="bill-due-date">到期日</label><input id="bill-due-date" type="date" value="${dueDate}" required></div><div class="field-error" role="alert"></div>`, async node => {
    await api(`/admin/billing/monthly-bills/${billId}/issue`, { method: 'POST', mutating: true, body: { dueDate: node.querySelector('#bill-due-date').value } }); notify('账单已签发'); renderBills();
  }, '签发');
}

function openRecordBillPaymentModal(billId, payableFen) {
  openModal('登记线下收款', `<div class="field"><label for="bill-amount">实收金额（分）</label><input id="bill-amount" type="number" min="1" value="${escapeHtml(payableFen)}" required><small>应付 ${money(payableFen)}</small></div><div class="field"><label for="bill-remark">收款凭证或备注</label><textarea id="bill-remark" maxlength="255" required></textarea></div><div class="field-error" role="alert"></div>`, async node => {
    const amountFen = Number(node.querySelector('#bill-amount').value); const remark = node.querySelector('#bill-remark').value.trim(); if (!amountFen || !remark) throw new Error('请填写收款金额和凭证备注');
    await api(`/admin/billing/monthly-bills/${billId}/payments`, { method: 'POST', mutating: true, body: { amountFen, remark } }); notify('线下收款已记录'); renderBills();
  }, '确认收款');
}

async function renderAudit() {
  const root = state.session.role === 'PLATFORM_ADMIN' ? '/admin' : '/venue';
  const data = await api(`${root}/audit-logs?${filteredPageQuery('audit')}`);
  document.querySelector('#main').innerHTML = `${pageHeading('审计记录', '记录关键状态迁移、敏感数据授权和后台任务结果。')}
    <section class="panel"><form class="panel-body filters" data-filter-key="audit"><div class="field"><label for="audit-action">操作</label><input id="audit-action" name="action" value="${escapeHtml(filterValue('audit', 'action'))}" placeholder="输入操作编码关键字"></div><div class="field"><label for="audit-result">结果</label><select id="audit-result" name="result"><option value="">全部结果</option>${['SUCCEEDED','FAILED','REJECTED'].map(value => `<option value="${value}" ${filterValue('audit', 'result') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><div class="field"><label for="audit-target">对象类型</label><select id="audit-target" name="targetType"><option value="">全部对象</option>${['ACCOUNT','ORGANIZATION','STORE','BOOKING_ORDER','MONTHLY_BILL','VENUE_EMPLOYEE'].map(value => `<option value="${value}" ${filterValue('audit', 'targetType') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><div class="field"><label for="audit-from">日期从</label><input id="audit-from" name="dateFrom" type="date" value="${escapeHtml(filterValue('audit', 'dateFrom'))}"></div><div class="field"><label for="audit-to">至</label><input id="audit-to" name="dateTo" type="date" value="${escapeHtml(filterValue('audit', 'dateTo'))}"></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>最近操作</h3><span class="mono">${data.total} 条</span></div>${table(['时间','操作者','动作','对象','结果','原因','请求标识'], data.records.map(item => `<tr><td class="mono">${shortDate(item.createTime)}</td><td>${escapeHtml(item.actorName || '系统')}</td><td>${escapeHtml(label(item.action))}</td><td>${escapeHtml(label(item.targetType))} ${item.targetId || ''}</td><td>${status(item.result)}</td><td>${escapeHtml(item.reason || '-')}</td><td class="mono">${escapeHtml(item.requestId || '-')}</td></tr>`))}${pager(data, 'audit')}</section>`;
  bindFilters('audit', renderAudit); bindPager('audit', renderAudit);
}

async function renderEmployees() {
  const data = await api(`/venue/employees?${pageQuery('employees')}`);
  document.querySelector('#main').innerHTML = `${pageHeading('店员管理', '绑定码仅显示一次，停用会立即撤销对应店员会话。', `<button class="btn btn-primary" id="add-employee">${icon('user-plus')}新增店员</button>`)}
    <section class="panel"><div class="panel-head"><h3>店员</h3><span class="mono">${data.total} 人</span></div>${table(['姓名 / 工号','绑定状态','今日核销','最近核销','状态','操作'], data.records.map(item => `<tr><td><strong>${escapeHtml(item.employeeName)}</strong><br><span class="mono">${escapeHtml(item.employeeNo)}</span></td><td>${status(item.bindingStatus)}</td><td class="mono">${item.todayVerifiedCount}</td><td class="mono">${shortDate(item.lastVerifiedAt)}</td><td>${status(item.employeeStatus)}</td><td><button class="btn btn-secondary table-action" data-code="${item.id}" ${item.employeeStatus !== 'ACTIVE' ? 'disabled' : ''}>绑定码</button> <button class="btn ${item.employeeStatus === 'ACTIVE' ? 'btn-danger' : 'btn-secondary'} table-action" data-employee-status="${item.id}" data-next-status="${item.employeeStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'}">${item.employeeStatus === 'ACTIVE' ? '停用' : '重新启用'}</button></td></tr>`))}${pager(data, 'employees')}</section>`;
  document.querySelector('#add-employee').addEventListener('click', openEmployeeModal);
  document.querySelectorAll('[data-code]').forEach(button => button.addEventListener('click', () => issueBindingCode(button.dataset.code)));
  document.querySelectorAll('[data-employee-status]').forEach(button => button.addEventListener('click', () => updateEmployeeStatus(button.dataset.employeeStatus, button.dataset.nextStatus)));
  bindPager('employees', renderEmployees);
  refreshIcons();
}

async function renderVerifications() {
  const data = await api(`/venue/employees/verifications?${filteredPageQuery('verifications')}`);
  const employees = await api('/venue/employees?page=1&pageSize=100');
  document.querySelector('#main').innerHTML = `${pageHeading('核销记录', '查看全店成功、重复和被拒绝的核销尝试。')}
    <section class="panel"><form class="panel-body filters" data-filter-key="verifications"><div class="field"><label for="verification-date">日期</label><input id="verification-date" name="date" type="date" value="${escapeHtml(filterValue('verifications', 'date'))}"></div><div class="field"><label for="verification-employee">店员</label><select id="verification-employee" name="employeeId"><option value="">全部店员</option>${employees.records.map(item => `<option value="${item.id}" ${filterValue('verifications', 'employeeId') === item.id ? 'selected' : ''}>${escapeHtml(item.employeeName)}</option>`).join('')}</select></div><div class="field"><label for="verification-result">结果</label><select id="verification-result" name="result"><option value="">全部结果</option>${['SUCCEEDED','DUPLICATE','REJECTED'].map(value => `<option value="${value}" ${filterValue('verifications', 'result') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>核销尝试</h3><span class="mono">${data.total} 条</span></div>${table(['时间','店员','订单','方式','结果','失败原因'], data.records.map(item => `<tr><td class="mono">${shortDate(item.createTime)}</td><td>${escapeHtml(item.employeeName)}</td><td class="mono">${escapeHtml(item.orderNo || '-')}</td><td>${status(item.verificationMethod)}</td><td>${status(item.result)}</td><td>${escapeHtml(label(item.failureReason))}</td></tr>`), '暂无核销记录')}${pager(data, 'verifications')}</section>`;
  bindFilters('verifications', renderVerifications); bindPager('verifications', renderVerifications);
}

async function renderProfile() {
  const data = await api('/venue/profile');
  document.querySelector('#main').innerHTML = `${pageHeading('场馆资料', '资料由场馆管理员维护，公开场馆详情会直接读取已生效内容。', `<button class="btn btn-primary" id="edit-profile">${icon('pencil')}编辑资料</button>`)}
    <div class="detail-grid"><section class="panel"><div class="panel-head"><h3>${escapeHtml(data.storeName || '当前场馆')}</h3>${status(data.status || 'ACTIVE')}</div><dl class="detail-list"><dt>场馆介绍</dt><dd>${escapeHtml(data.introduction || '尚未填写')}</dd><dt>地址</dt><dd>${escapeHtml(data.address || '尚未填写')}</dd><dt>联系电话</dt><dd class="mono">${escapeHtml(data.contactPhone || '尚未填写')}</dd><dt>坐标</dt><dd class="mono">${escapeHtml(data.longitude || '-')} / ${escapeHtml(data.latitude || '-')}</dd><dt>服务费率</dt><dd class="mono">${data.serviceRateBasisPoints ?? '-'} bp</dd><dt>设施</dt><dd>${(data.facilities || []).map(escapeHtml).join('、') || '尚未填写'}</dd><dt>图片地址</dt><dd>${(data.imageUrls || []).map(value => `<span class="mono">${escapeHtml(value)}</span>`).join('<br>') || '尚未填写'}</dd></dl></section><section class="panel"><div class="panel-head"><h3>到店与使用说明</h3></div><dl class="detail-list"><dt>交通说明</dt><dd>${escapeHtml(data.trafficNote || '尚未填写')}</dd><dt>入场说明</dt><dd>${escapeHtml(data.entryNotice || '尚未填写')}</dd><dt>器材说明</dt><dd>${escapeHtml(data.equipmentNote || '尚未填写')}</dd><dt>停车说明</dt><dd>${escapeHtml(data.parkingNote || '尚未填写')}</dd><dt>责任说明</dt><dd>${escapeHtml(data.liabilityNote || '尚未填写')}</dd></dl></section></div>`;
  document.querySelector('#edit-profile').addEventListener('click', () => openProfileModal(data)); refreshIcons();
}

function openProfileModal(current) {
  openModal('编辑场馆资料', `<div class="split"><div class="field"><label for="profile-name">场馆名称</label><input id="profile-name" maxlength="128" value="${escapeHtml(current.storeName || '')}" required></div><div class="field"><label for="profile-phone">联系电话</label><input id="profile-phone" maxlength="32" value="${escapeHtml(current.contactPhone || '')}"></div><div class="field"><label for="profile-address">地址</label><input id="profile-address" maxlength="255" value="${escapeHtml(current.address || '')}"></div><div class="field"><label for="profile-coordinates">经度 / 纬度</label><div class="inline-fields"><input id="profile-longitude" inputmode="decimal" value="${escapeHtml(current.longitude || '')}" placeholder="经度"><input id="profile-latitude" inputmode="decimal" value="${escapeHtml(current.latitude || '')}" placeholder="纬度"></div></div></div><div class="field"><label for="profile-introduction">场馆介绍</label><textarea id="profile-introduction" maxlength="2000">${escapeHtml(current.introduction || '')}</textarea></div><div class="field"><label for="profile-facilities">设施（用逗号分隔）</label><input id="profile-facilities" value="${escapeHtml((current.facilities || []).join('、'))}"></div><div class="field"><label for="profile-images">图片地址（用逗号分隔）</label><textarea id="profile-images">${escapeHtml((current.imageUrls || []).join('\n'))}</textarea></div><div class="split"><div class="field"><label for="profile-traffic">交通说明</label><textarea id="profile-traffic" maxlength="2000">${escapeHtml(current.trafficNote || '')}</textarea></div><div class="field"><label for="profile-entry">入场说明</label><textarea id="profile-entry" maxlength="2000">${escapeHtml(current.entryNotice || '')}</textarea></div><div class="field"><label for="profile-equipment">器材说明</label><textarea id="profile-equipment" maxlength="2000">${escapeHtml(current.equipmentNote || '')}</textarea></div><div class="field"><label for="profile-parking">停车说明</label><textarea id="profile-parking" maxlength="2000">${escapeHtml(current.parkingNote || '')}</textarea></div></div><div class="field"><label for="profile-liability">责任说明</label><textarea id="profile-liability" maxlength="2000">${escapeHtml(current.liabilityNote || '')}</textarea></div><div class="field-error" role="alert"></div>`, async node => {
    const storeName = node.querySelector('#profile-name').value.trim(); if (!storeName) throw new Error('请填写场馆名称');
    const list = value => value.split(/[\n、,，]/).map(item => item.trim()).filter(Boolean);
    const body = { ...current, storeName, introduction: node.querySelector('#profile-introduction').value.trim(), address: node.querySelector('#profile-address').value.trim(), contactPhone: node.querySelector('#profile-phone').value.trim(), longitude: node.querySelector('#profile-longitude').value.trim() || null, latitude: node.querySelector('#profile-latitude').value.trim() || null, facilities: list(node.querySelector('#profile-facilities').value), imageUrls: list(node.querySelector('#profile-images').value), trafficNote: node.querySelector('#profile-traffic').value.trim(), entryNotice: node.querySelector('#profile-entry').value.trim(), equipmentNote: node.querySelector('#profile-equipment').value.trim(), parkingNote: node.querySelector('#profile-parking').value.trim(), liabilityNote: node.querySelector('#profile-liability').value.trim() };
    await api('/venue/profile', { method: 'PUT', mutating: true, body }); notify('场馆资料已保存'); renderProfile();
  }, '保存');
}

async function loadCourtDetails(paged = false) {
  const data = await api(`/venue/resources/courts?${paged ? filteredPageQuery('courts') : 'page=1&pageSize=100'}`);
  const records = await Promise.all(data.records.map(court => api(`/venue/resources/courts/${court.id}`)));
  return paged ? { data, records } : records;
}

async function renderCourts() {
  const result = await loadCourtDetails(true);
  document.querySelector('#main').innerHTML = `${pageHeading('物理场地', '管理真实场地及其 A/B 基础资源。可售方式在“可售单元”中单独管理。', `<button class="btn btn-primary" id="add-court">${icon('plus-square')}新建场地</button>`)}
    <section class="panel"><form class="panel-body filters" data-filter-key="courts"><div class="field"><label for="court-status">场地状态</label><select id="court-status" name="status"><option value="">全部状态</option>${['ACTIVE', 'DISABLED'].map(value => `<option value="${value}" ${filterValue('courts', 'status') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>物理场地列表</h3><span class="mono">${result.data.total} 个</span></div>${table(['场地','运动项目','基础资源','可售单元数','状态','创建时间','操作'], result.records.map(court => `<tr><td><strong>${escapeHtml(court.name)}</strong></td><td>${escapeHtml(court.sportCategoryName)}</td><td>${court.baseResources.map(item => escapeHtml(item.name)).join(' + ')}</td><td class="mono">${court.sellableUnits.length}</td><td>${status(court.status)}</td><td class="mono">${shortDate(court.createTime)}</td><td><button class="btn ${court.status === 'ACTIVE' ? 'btn-danger' : 'btn-secondary'} table-action" data-court-status="${court.id}" data-next-status="${court.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'}">${court.status === 'ACTIVE' ? '停用' : '启用'}</button></td></tr>`), '暂无物理场地。')}${pager(result.data, 'courts')}</section>`;
  document.querySelector('#add-court').addEventListener('click', openCourtModal);
  document.querySelectorAll('[data-court-status]').forEach(button => button.addEventListener('click', () => updateResourceStatus(`/venue/resources/courts/${button.dataset.courtStatus}/status`, button.dataset.nextStatus, '物理场地', renderCourts)));
  bindFilters('courts', renderCourts); bindPager('courts', renderCourts);
  refreshIcons();
}

async function renderSellableUnits() {
  const data = await api(`/venue/resources/sellable-units?${filteredPageQuery('sellable-units')}`);
  document.querySelector('#main').innerHTML = `${pageHeading('可售单元', '独立管理全场、半场等可售方式及其资源版本。')}
    <section class="panel"><form class="panel-body filters" data-filter-key="sellable-units"><div class="field"><label for="unit-status">销售状态</label><select id="unit-status" name="status"><option value="">全部状态</option>${['ACTIVE','DISABLED'].map(value => `<option value="${value}" ${filterValue('sellable-units', 'status') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>可售单元列表</h3><span class="mono">${data.total} 个</span></div>${table(['可售单元','物理场地','出租类型','当前版本','基础资源组合','状态','操作'], data.records.map(unit => `<tr><td><strong>${escapeHtml(unit.name)}</strong></td><td>${escapeHtml(unit.physicalCourtName)}<br><span class="mono">${escapeHtml(unit.sportCategoryName)}</span></td><td>${escapeHtml(unit.rentalTypeName)}</td><td class="mono">v${unit.currentVersion.versionNo}</td><td>${unit.currentVersion.baseResources.map(item => escapeHtml(item.name)).join(' + ')}</td><td>${status(unit.status)}</td><td><button class="btn btn-secondary table-action" data-new-version="${unit.id}" data-court="${unit.physicalCourtId}">新版本</button> <button class="btn ${unit.status === 'ACTIVE' ? 'btn-danger' : 'btn-secondary'} table-action" data-unit-status="${unit.id}" data-next-status="${unit.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'}">${unit.status === 'ACTIVE' ? '下架' : '上架'}</button></td></tr>`), '暂无可售单元，请先创建物理场地。')}${pager(data, 'sellable-units')}</section>`;
  document.querySelectorAll('[data-unit-status]').forEach(button => button.addEventListener('click', () => updateResourceStatus(`/venue/resources/sellable-units/${button.dataset.unitStatus}/status`, button.dataset.nextStatus, '可售单元', renderSellableUnits)));
  document.querySelectorAll('[data-new-version]').forEach(button => button.addEventListener('click', async () => {
    const court = await api(`/venue/resources/courts/${button.dataset.court}`); const unit = court.sellableUnits.find(item => item.id === button.dataset.newVersion); if (unit) openVersionModal(court, unit);
  }));
  bindFilters('sellable-units', renderSellableUnits); bindPager('sellable-units', renderSellableUnits); refreshIcons();
}

function updateResourceStatus(path, statusValue, resourceLabel, refresh) {
  openModal(`${statusValue === 'ACTIVE' ? '启用' : '停用'}${resourceLabel}`, '<p>状态变更不会删除历史排期、库存或订单。</p><div class="field-error" role="alert"></div>', async () => {
    await api(path, { method: 'PATCH', mutating: true, body: { status: statusValue } }); notify(`${resourceLabel}状态已更新`); refresh();
  });
}

async function openCourtModal() {
  const [categories, rentalTypes] = await Promise.all([api('/venue/catalog/sport-categories?page=1&pageSize=100'), api('/venue/catalog/rental-types?page=1&pageSize=100')]);
  const categoryOptions = categories.records.filter(item => item.level === 2 && item.status === 'ACTIVE').map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join('');
  const rentalOptions = rentalTypes.records.filter(item => item.status === 'ACTIVE').map(item => `<option value="${item.id}">${escapeHtml(item.name)} / ${escapeHtml(item.sportCategoryName)}</option>`).join('');
  openModal('新建物理场地', `<div class="field"><label for="court-name">场地名称</label><input id="court-name" maxlength="128" required></div><div class="field"><label for="court-category">运动分类</label><select id="court-category" required><option value="">请选择</option>${categoryOptions}</select></div><div class="field"><label for="court-full-type">全场出租类型</label><select id="court-full-type" required><option value="">请选择</option>${rentalOptions}</select></div><div class="field"><label for="court-half-type">半场出租类型</label><select id="court-half-type" required><option value="">请选择</option>${rentalOptions}</select></div><div class="field"><label for="court-sort">排序</label><input id="court-sort" type="number" min="0" value="0" required></div><div class="field-error" role="alert"></div>`, async node => {
    const body = { name: node.querySelector('#court-name').value.trim(), sportCategoryId: node.querySelector('#court-category').value, fullRentalTypeId: node.querySelector('#court-full-type').value, halfRentalTypeId: node.querySelector('#court-half-type').value, sortOrder: Number(node.querySelector('#court-sort').value) };
    if (!body.name || !body.sportCategoryId || !body.fullRentalTypeId || !body.halfRentalTypeId) throw new Error('请完成场地资源信息');
    await api('/venue/resources/courts', { method: 'POST', mutating: true, body }); notify('物理场地与默认全场、A/B 半场已创建'); renderCourts();
  }, '创建');
}

function openVersionModal(court, unit) {
  const options = court.baseResources.map(resource => `<option value="${resource.id}" ${unit.currentVersion.baseResources.some(current => current.id === resource.id) ? 'selected' : ''}>${escapeHtml(resource.name)}</option>`).join('');
  openModal(`${unit.name} 创建资源版本`, `<div class="field"><label for="version-resources">基础资源组合</label><select id="version-resources" multiple size="${court.baseResources.length}">${options}</select><small>选择新的组合后，旧版本会保留给已有排期、库存和订单。</small></div><div class="field-error" role="alert"></div>`, async node => {
    const baseResourceIds = [...node.querySelector('#version-resources').selectedOptions].map(option => option.value); if (!baseResourceIds.length) throw new Error('至少选择一个基础资源');
    await api(`/venue/resources/sellable-units/${unit.id}/versions`, { method: 'POST', mutating: true, body: { baseResourceIds } }); notify('可售单元新版本已创建'); renderSellableUnits();
  }, '创建版本');
}

function scheduleSlotRow(unitOptions, slot = {}, weekly = true) {
  const weekdays = ['<option value="1">周一</option>', '<option value="2">周二</option>', '<option value="3">周三</option>', '<option value="4">周四</option>', '<option value="5">周五</option>', '<option value="6">周六</option>', '<option value="7">周日</option>'].map(option => option.replace(`value="${slot.weekDay}"`, `value="${slot.weekDay}" selected`)).join('');
  return `<div class="schedule-row" data-schedule-slot>${weekly ? `<select data-weekday>${weekdays}</select>` : ''}<select data-unit-version required>${unitOptions.replace(`value="${slot.sellableUnitVersionId}"`, `value="${slot.sellableUnitVersionId}" selected`)}</select><input data-start-time type="time" step="1800" value="${escapeHtml(slot.startTime || '20:00')}" required><input data-end-time type="time" step="1800" value="${escapeHtml(slot.endTime || '21:00')}" required><input data-price-fen type="number" min="1" value="${escapeHtml(slot.priceFen || '10000')}" required><button class="btn btn-secondary btn-icon" type="button" data-remove-slot aria-label="移除此时段">${icon('x')}</button></div>`;
}

function specialDateRow(unitOptions, special = {}) {
  const slots = special.slots || [];
  return `<div class="schedule-special" data-special-date><div class="schedule-special-head"><input data-special-date-value type="date" value="${escapeHtml(special.date || '')}" required><select data-special-type><option value="OVERRIDE" ${special.specialType === 'OVERRIDE' ? 'selected' : ''}>特殊营业</option><option value="CLOSED" ${special.specialType === 'CLOSED' ? 'selected' : ''}>闭馆</option></select><button class="btn btn-secondary btn-icon" type="button" data-remove-special aria-label="移除特殊日期">${icon('x')}</button></div><div data-special-slots>${slots.map(slot => scheduleSlotRow(unitOptions, slot, false)).join('')}</div><button class="btn btn-secondary table-action" type="button" data-add-special-slot>${icon('plus')}新增覆盖时段</button></div>`;
}

async function renderSchedules(mode = 'weekly') {
  const key = mode === 'weekly' ? 'weekly-schedules' : 'special-dates';
  const data = await api(`/venue/schedules/versions?${filteredPageQuery(key)}`);
  const details = await Promise.all(data.records.map(item => api(`/venue/schedules/versions/${item.id}`)));
  const title = mode === 'weekly' ? '周排期' : '特殊日期';
  const description = mode === 'weekly' ? '配置每周重复销售的固定时段和整段价格。' : '仅配置节假日闭馆或临时营业覆盖，不混入周模板。';
  document.querySelector('#main').innerHTML = `${pageHeading(title, description, `<button class="btn btn-primary" id="add-schedule">${icon('file-plus-2')}新建${mode === 'weekly' ? '周排期' : '特殊日期草稿'}</button>`)}
    <section class="panel"><form class="panel-body filters" data-filter-key="${key}"><div class="field"><label for="schedule-status">版本状态</label><select id="schedule-status" name="status"><option value="">全部状态</option>${['DRAFT','PUBLISHED'].map(value => `<option value="${value}" ${filterValue(key, 'status') === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>${title}版本</h3><span class="mono">${data.total} 个版本</span></div>${mode === 'weekly' ? table(['版本','状态','生效区间','周时段数','发布时间','操作'], details.map(item => `<tr><td class="mono">v${item.versionNo}</td><td>${status(item.versionStatus)}</td><td class="mono">${item.effectiveFrom} 至 ${item.effectiveTo || '持续'}</td><td class="mono">${item.weeklySlots.length}</td><td class="mono">${shortDate(item.publishedAt)}</td><td>${scheduleActions(item, mode)}</td></tr>`), '暂无周排期。') : table(['版本','状态','特殊日期','覆盖摘要','操作'], details.map(item => `<tr><td class="mono">v${item.versionNo}</td><td>${status(item.versionStatus)}</td><td>${item.specialDates.map(value => `<span class="mono">${value.date}</span>`).join('<br>') || '-'}</td><td>${item.specialDates.map(value => `${label(value.specialType)}${value.slots.length ? ` · ${value.slots.length} 个时段` : ''}`).join('<br>') || '无特殊日期'}</td><td>${scheduleActions(item, mode)}</td></tr>`), '暂无特殊日期配置。')}${pager(data, key)}</section>`;
  document.querySelector('#add-schedule').addEventListener('click', () => openScheduleModal(null, mode));
  document.querySelectorAll('[data-edit-schedule]').forEach(button => button.addEventListener('click', () => openScheduleModal(button.dataset.editSchedule, mode)));
  document.querySelectorAll('[data-view-schedule]').forEach(button => button.addEventListener('click', () => openScheduleReadModal(button.dataset.viewSchedule, mode)));
  document.querySelectorAll('[data-publish-schedule]').forEach(button => button.addEventListener('click', () => openPublishScheduleModal(button.dataset.publishSchedule, mode)));
  bindFilters(key, () => renderSchedules(mode)); bindPager(key, () => renderSchedules(mode));
  refreshIcons();
}

function scheduleActions(item, mode) {
  return item.versionStatus === 'DRAFT' ? `<button class="btn btn-secondary table-action" data-edit-schedule="${item.id}">编辑</button> <button class="btn btn-primary table-action" data-publish-schedule="${item.id}">发布</button>` : `<button class="btn btn-secondary table-action" data-view-schedule="${item.id}">查看</button>`;
}

async function openScheduleModal(scheduleId, mode = 'weekly') {
  const courts = await loadCourtDetails();
  const unitOptions = ['<option value="">选择可售单元版本</option>', ...courts.flatMap(court => court.sellableUnits.filter(unit => unit.status === 'ACTIVE').map(unit => `<option value="${unit.currentVersion.id}">${escapeHtml(court.name)} / ${escapeHtml(unit.name)} v${unit.currentVersion.versionNo}</option>`))].join('');
  let current = scheduleId ? await api(`/venue/schedules/versions/${scheduleId}`) : { effectiveFrom: businessDate(), weeklySlots: [{}], specialDates: [] };
  if (!scheduleId && mode === 'special') {
    const versions = await api('/venue/schedules/versions?page=1&pageSize=1&status=PUBLISHED');
    if (!versions.records.length) { notify('请先发布一个周排期，再创建特殊日期草稿', true); return; }
    current = await api(`/venue/schedules/versions/${versions.records[0].id}`);
    const nextPublishedDate = new Date(`${current.effectiveFrom}T00:00:00+08:00`);
    nextPublishedDate.setDate(nextPublishedDate.getDate() + 1);
    const nextEffectiveFrom = nextPublishedDate.toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' });
    current = { ...current, effectiveFrom: nextEffectiveFrom > businessDate() ? nextEffectiveFrom : businessDate(), specialDates: [] };
  }
  if (scheduleId && current.versionStatus !== 'DRAFT') { notify('已发布排期不能编辑', true); return; }
  const editor = mode === 'weekly' ? `<div class="schedule-section"><div class="schedule-section-head"><strong>周模板固定时段</strong><button class="btn btn-secondary table-action" type="button" data-add-weekly-slot>${icon('plus')}新增时段</button></div><div id="weekly-slots">${(current.weeklySlots || [{}]).map(slot => scheduleSlotRow(unitOptions, slot, true)).join('')}</div></div>` : `<div class="schedule-section"><div class="schedule-section-head"><strong>特殊日期覆盖</strong><button class="btn btn-secondary table-action" type="button" data-add-special-date>${icon('plus')}新增日期</button></div><div id="special-dates">${(current.specialDates || []).map(special => specialDateRow(unitOptions, special)).join('')}</div></div>`;
  const node = openModal(scheduleId ? `编辑${mode === 'weekly' ? '周排期' : '特殊日期'} v${current.versionNo}` : `新建${mode === 'weekly' ? '周排期' : '特殊日期'}草稿`, `<div class="field"><label for="schedule-effective-from">生效日期</label><input id="schedule-effective-from" type="date" value="${escapeHtml(current.effectiveFrom)}" required></div>${editor}<div class="field-error" role="alert"></div>`, async modal => {
    const weeklySlots = mode === 'weekly' ? collectScheduleSlots(modal.querySelector('#weekly-slots'), true) : current.weeklySlots;
    const specialDates = mode === 'special' ? [...modal.querySelector('#special-dates').children].map(element => ({ date: element.querySelector('[data-special-date-value]').value, specialType: element.querySelector('[data-special-type]').value, slots: element.querySelector('[data-special-type]').value === 'CLOSED' ? [] : collectScheduleSlots(element.querySelector('[data-special-slots]'), false) })) : current.specialDates;
    if (!weeklySlots.length) throw new Error('至少配置一个周模板固定时段');
    if (specialDates.some(item => !item.date || (item.specialType === 'OVERRIDE' && !item.slots.length))) throw new Error('特殊日期覆盖内容不完整');
    const body = { effectiveFrom: modal.querySelector('#schedule-effective-from').value, weeklySlots, specialDates };
    await api(scheduleId ? `/venue/schedules/versions/${scheduleId}` : '/venue/schedules/versions', { method: scheduleId ? 'PUT' : 'POST', mutating: true, body }); notify('排期草稿已保存'); renderSchedules(mode);
  }, '保存草稿');
  node.addEventListener('click', event => {
    const target = event.target.closest('button'); if (!target) return;
    if (target.matches('[data-add-weekly-slot]')) node.querySelector('#weekly-slots').insertAdjacentHTML('beforeend', scheduleSlotRow(unitOptions, {}, true));
    if (target.matches('[data-add-special-date]')) node.querySelector('#special-dates').insertAdjacentHTML('beforeend', specialDateRow(unitOptions, {}));
    if (target.matches('[data-add-special-slot]')) target.closest('[data-special-date]').querySelector('[data-special-slots]').insertAdjacentHTML('beforeend', scheduleSlotRow(unitOptions, {}, false));
    if (target.matches('[data-remove-slot]')) target.closest('[data-schedule-slot]').remove();
    if (target.matches('[data-remove-special]')) target.closest('[data-special-date]').remove();
    refreshIcons();
  });
}

function collectScheduleSlots(container, weekly) {
  return [...container.children].filter(element => element.matches('[data-schedule-slot]')).map(element => ({ weekDay: weekly ? Number(element.querySelector('[data-weekday]').value) : null, sellableUnitVersionId: element.querySelector('[data-unit-version]').value, startTime: element.querySelector('[data-start-time]').value, endTime: element.querySelector('[data-end-time]').value, priceFen: Number(element.querySelector('[data-price-fen]').value) })).filter(item => item.sellableUnitVersionId && item.startTime && item.endTime && item.priceFen > 0);
}

async function openScheduleReadModal(scheduleId, mode = 'weekly') {
  const data = await api(`/venue/schedules/versions/${scheduleId}`);
  const content = mode === 'weekly' ? table(['星期', '可售单元', '时段', '总价'], (data.weeklySlots || []).map(slot => `<tr><td>周${slot.weekDay}</td><td>${escapeHtml(slot.sellableUnitName)} v${slot.sellableUnitVersionNo}</td><td class="mono">${slot.startTime}-${slot.endTime}</td><td class="mono">${money(slot.priceFen)}</td></tr>`), '无周模板时段') : table(['特殊日期', '类型', '覆盖时段'], (data.specialDates || []).map(item => `<tr><td class="mono">${item.date}</td><td>${status(item.specialType)}</td><td>${(item.slots || []).map(slot => `${escapeHtml(slot.sellableUnitName)} ${slot.startTime}-${slot.endTime}`).join('<br>') || '-'}</td></tr>`), '无特殊日期覆盖');
  openModal(`${mode === 'weekly' ? '周排期' : '特殊日期'} v${data.versionNo}`, `<div class="stack"><div>${status(data.versionStatus)} <span class="mono">${data.effectiveFrom} 至 ${data.effectiveTo || '持续'}</span></div>${content}</div>`, async () => {}, '关闭');
}

function openPublishScheduleModal(scheduleId, mode = 'weekly') {
  openModal('发布排期版本', '<p>发布后该草稿不可再编辑，系统会在当前预约窗口内重建未占用的未来库存。</p><div class="field-error" role="alert"></div>', async () => {
    await api(`/venue/schedules/versions/${scheduleId}/publish`, { method: 'POST', mutating: true }); notify('排期已发布，库存正在按版本生效'); renderSchedules(mode);
  }, '发布');
}

async function renderInventory() {
  const date = state.inventoryDate || businessDate();
  const query = new URLSearchParams({ date, page: String(pageFor('inventory')), pageSize: String(pageSizeFor('inventory')) });
  if (state.inventoryStatus) query.set('status', state.inventoryStatus);
  const data = await api(`/venue/schedules/inventory?${query}`);
  document.querySelector('#main').innerHTML = `${pageHeading('库存日历', '库存状态由基础资源原子聚合得出；仅可预约时段可创建人工占用。')}
    <section class="panel"><form class="panel-body filters" id="inventory-filter"><div class="field"><label for="inventory-date">日期</label><input id="inventory-date" type="date" value="${date}" required></div><div class="field"><label for="inventory-status">库存状态</label><select id="inventory-status"><option value="">全部状态</option>${['AVAILABLE','HELD','BOOKED','OFFLINE_RESERVATION','MAINTENANCE'].map(value => `<option value="${value}" ${state.inventoryStatus === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}查询</button></form></section>
    <section class="panel"><div class="panel-head"><h3>${date} 固定时段</h3><span class="mono">${data.total} 条</span></div>${table(['可售单元','时段','总价','库存状态','原子明细','操作'], data.records.map(item => `<tr><td>${escapeHtml(item.sellableUnitName)}<br><span class="mono">v${item.sellableUnitVersionNo}</span></td><td class="mono">${item.startTime}-${item.endTime}</td><td class="mono">${money(item.priceFen)}</td><td>${status(item.inventoryStatus)}</td><td class="mono">${item.atoms.map(atom => `${atom.baseResourceId}:${label(atom.inventoryStatus)}`).join('<br>')}</td><td>${item.inventoryStatus === 'AVAILABLE' ? `<button class="btn btn-secondary table-action" data-block-slot="${item.id}" data-slot-label="${escapeHtml(item.sellableUnitName)} ${item.startTime}-${item.endTime}">人工占用</button>` : '-'}</td></tr>`), '当天暂无已发布库存。')}${pager(data, 'inventory')}</section>`;
  document.querySelector('#inventory-filter').addEventListener('submit', event => { event.preventDefault(); state.inventoryDate = document.querySelector('#inventory-date').value; state.inventoryStatus = document.querySelector('#inventory-status').value; state.pages.inventory = 1; renderInventory(); });
  document.querySelectorAll('[data-block-slot]').forEach(button => button.addEventListener('click', () => openManualBlockModal(button.dataset.blockSlot, button.dataset.slotLabel)));
  bindPager('inventory', renderInventory);
}

function openManualBlockModal(inventorySlotId, slotLabel) {
  openModal(`人工占用 ${slotLabel}`, '<div class="field"><label for="block-type">占用类型</label><select id="block-type"><option value="OFFLINE_RESERVATION">线下预约</option><option value="MAINTENANCE">维护</option><option value="EVENT">活动</option><option value="SELF_USE">自用</option></select></div><div class="field"><label for="block-contact-name">联系人</label><input id="block-contact-name" maxlength="64" required></div><div class="field"><label for="block-contact-phone">联系电话</label><input id="block-contact-phone" maxlength="32" required></div><div class="field"><label for="block-remark">备注</label><textarea id="block-remark" maxlength="255" required></textarea></div><div class="field-error" role="alert"></div>', async node => {
    const body = { inventorySlotId, blockType: node.querySelector('#block-type').value, contactName: node.querySelector('#block-contact-name').value.trim(), contactPhone: node.querySelector('#block-contact-phone').value.trim(), remark: node.querySelector('#block-remark').value.trim() }; if (!body.contactName || !body.contactPhone || !body.remark) throw new Error('请填写人工占用信息');
    await api('/venue/blocks', { method: 'POST', mutating: true, body }); notify('人工占用已创建'); renderInventory();
  }, '创建占用');
}

async function renderBlocks() {
  const query = new URLSearchParams({ page: String(pageFor('blocks')), pageSize: String(pageSizeFor('blocks')) });
  if (state.blockDate) query.set('date', state.blockDate);
  if (state.blockStatus) query.set('status', state.blockStatus);
  const data = await api(`/venue/blocks?${query}`);
  document.querySelector('#main').innerHTML = `${pageHeading('人工占用', '人工占用与线上预约使用同一组基础资源原子，取消后按当前排期版本恢复库存。')}
    <section class="panel"><form class="panel-body filters" id="block-filters"><div class="field"><label for="block-date">占用日期</label><input id="block-date" type="date" value="${escapeHtml(state.blockDate)}"></div><div class="field"><label for="block-status">占用状态</label><select id="block-status"><option value="">全部状态</option>${['ACTIVE','CANCELLED'].map(value => `<option value="${value}" ${state.blockStatus === value ? 'selected' : ''}>${label(value)}</option>`).join('')}</select></div><button class="btn btn-secondary" type="submit">${icon('filter')}筛选</button></form></section>
    <section class="panel"><div class="panel-head"><h3>占用记录</h3><span class="mono">${data.total} 条</span></div>${table(['占用编号','场地时段','类型','联系人','状态','创建时间','操作'], data.records.map(item => `<tr><td class="mono">${escapeHtml(item.blockNo)}</td><td>${escapeHtml(item.sellableUnitName)}<br><span class="mono">${item.date} ${item.startTime}-${item.endTime}</span></td><td>${status(item.blockType)}</td><td>${escapeHtml(item.contactName)}<br><span class="mono">${escapeHtml(item.contactPhone)}</span></td><td>${status(item.blockStatus)}</td><td class="mono">${shortDate(item.createTime)}</td><td>${item.blockStatus === 'ACTIVE' ? `<button class="btn btn-danger table-action" data-cancel-block="${item.id}">取消占用</button>` : '-'}</td></tr>`), '暂无人工占用记录。')}${pager(data, 'blocks')}</section>`;
  document.querySelector('#block-filters').addEventListener('submit', event => { event.preventDefault(); state.blockDate = document.querySelector('#block-date').value; state.blockStatus = document.querySelector('#block-status').value; state.pages.blocks = 1; renderBlocks(); });
  document.querySelectorAll('[data-cancel-block]').forEach(button => button.addEventListener('click', () => openCancelBlockModal(button.dataset.cancelBlock)));
  bindPager('blocks', renderBlocks);
}

function openCancelBlockModal(blockId) {
  openModal('取消人工占用', '<p>仅未来时段可以取消；库存会按当前排期版本恢复为可预约或待对账。</p><div class="field-error" role="alert"></div>', async () => {
    await api(`/venue/blocks/${blockId}/cancel`, { method: 'POST', mutating: true }); notify('人工占用已取消'); renderBlocks();
  }, '确认取消');
}

async function renderGate() {
  const data = await api('/venue/transaction-gate');
  const gates = [data.organizationGate, data.storeGate];
  document.querySelector('#main').innerHTML = `${pageHeading('交易门禁', '任意一层存在活动 blocker 都会禁止新增预约，不影响已创建订单履约。', `<button class="btn btn-primary" id="manage-gate">${icon('shield-alert')}管理门店暂停原因</button>`)}
    <section class="panel"><div class="panel-head"><h3>当前交易状态</h3>${status(data.allowsNewTransaction ? 'OPEN' : 'BLOCKED')}</div>${table(['范围','门禁状态','版本','活动原因'], gates.map(gate => `<tr><td>${gate.scopeType === 'ORGANIZATION' ? '经营主体' : '当前门店'}</td><td>${status(gate.gateStatus)}</td><td class="mono">${gate.gateVersion}</td><td>${gate.blockers.filter(blocker => blocker.blockerStatus === 'ACTIVE').map(blocker => escapeHtml(blocker.reason || blocker.causeType)).join('<br>') || '-'}</td></tr>`))}</section>`;
  document.querySelector('#manage-gate').addEventListener('click', () => openGateModal('/venue', '当前门店', 'storeGate')); refreshIcons();
}

function renderNewVenue() {
  document.querySelector('#main').innerHTML = `${pageHeading('新建场馆', '创建经营主体、唯一门店和首个场馆管理员。临时密码仅用于线下交接。', `<button class="btn btn-secondary" id="back-new-venue">${icon('arrow-left')}返回场馆列表</button>`)}
    <section class="panel"><form class="panel-body" id="venue-form"><div class="split"><div class="field"><label>经营主体名称</label><input name="organizationName" required maxlength="128"></div><div class="field"><label>场馆名称</label><input name="storeName" required maxlength="128"></div><div class="field"><label>管理员姓名</label><input name="adminDisplayName" required maxlength="64"></div><div class="field"><label>管理员账号</label><input name="adminUsername" required maxlength="64"></div><div class="field"><label>初始密码</label><input type="password" name="initialPassword" required minlength="8" autocomplete="new-password"></div><div class="field"><label>服务费率（基点）</label><input type="number" name="serviceRateBasisPoints" value="600" min="0" max="10000" required><small>600 = 6.00%</small></div></div><button class="btn btn-primary" type="submit">${icon('building-2')}创建并进入交接</button><div class="field-error" role="alert"></div></form></section>`;
  document.querySelector('#venue-form').addEventListener('submit', async event => {
    event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement); const error = formElement.querySelector('.field-error'); error.textContent = '';
    try { const result = await api('/admin/organizations', { method: 'POST', mutating: true, body: Object.fromEntries(form.entries()) }); notify(`已创建场馆，门店编号 ${result.storeNo}`); navigate('venue-detail', result.organizationId); } catch (errorValue) { error.textContent = messageOf(errorValue); }
  }); refreshIcons();
  document.querySelector('#back-new-venue').addEventListener('click', () => navigate('venues'));
}

function openModal(title, body, onConfirm, confirmText = '确认') {
  const previous = document.activeElement;
  const node = document.createElement('dialog'); node.className = 'modal-dialog'; node.setAttribute('aria-labelledby', 'modal-title');
  node.innerHTML = `<section class="modal"><div class="modal-head"><h3 id="modal-title">${escapeHtml(title)}</h3><button class="btn btn-secondary btn-icon" data-close aria-label="关闭">${icon('x')}</button></div><div class="modal-body">${body}</div><div class="modal-foot"><button class="btn btn-secondary" data-close>取消</button><button class="btn btn-primary" data-confirm>${escapeHtml(confirmText)}</button></div></section>`;
  document.body.append(node); node.showModal(); refreshIcons();
  const close = () => node.close(); node.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', close));
  node.addEventListener('click', event => { if (event.target === node) close(); });
  node.addEventListener('close', () => { node.remove(); previous?.focus?.(); }, { once: true });
  node.querySelector('[data-confirm]').addEventListener('click', async () => { const button = node.querySelector('[data-confirm]'); button.disabled = true; try { await onConfirm(node); close(); } catch (error) { button.disabled = false; const target = node.querySelector('.field-error'); if (target) target.textContent = messageOf(error); else notify(error, true); } });
  node.querySelector('input, textarea, select')?.focus();
  return node;
}

function openPhoneModal(root, orderId, orderNo) {
  openModal(`查看 ${orderNo} 完整手机号`, `<div class="field"><label for="reason">查看原因</label><textarea id="reason" maxlength="256" required></textarea><small>操作会被写入审计记录。</small></div><div class="field-error" role="alert"></div>`, async node => {
    const reason = node.querySelector('#reason').value.trim(); if (!reason) throw new Error('请填写查看原因');
    const data = await api(`${root}/orders/${orderId}/contact-phone/reveal`, { method: 'POST', mutating: true, body: { reason } }); notify(`完整手机号：${data.contactPhone}`);
  }, '授权查看');
}

function openEmployeeModal() {
  openModal('新增店员', `<div class="field"><label for="employee-name">店员姓名</label><input id="employee-name" maxlength="64" required></div><div class="field-error" role="alert"></div>`, async node => {
    const employeeName = node.querySelector('#employee-name').value.trim(); if (!employeeName) throw new Error('请填写店员姓名');
    await api('/venue/employees', { method: 'POST', mutating: true, body: { employeeName } }); notify('店员已创建'); renderEmployees();
  }, '创建');
}

function issueBindingCode(employeeId) {
  openModal('生成一次性绑定码', `<p>绑定码将在确认后显示一次，10 分钟内有效。请在受控渠道交给对应店员。</p><div class="field-error" role="alert"></div>`, async () => {
    const data = await api(`/venue/employees/${employeeId}/binding-code`, { method: 'POST', mutating: true });
    notify(`绑定码：${data.bindingCode}`);
  }, '生成');
}

function updateEmployeeStatus(employeeId, nextStatus) {
  const disabling = nextStatus === 'DISABLED';
  openModal(disabling ? '停用店员' : '重新启用店员', `<p>${disabling ? '停用后，该店员所有小程序会话和未使用绑定码都会立即失效。' : '启用后，店员需重新绑定或登录后才能核销。'}</p><div class="field-error" role="alert"></div>`, async () => {
    await api(`/venue/employees/${employeeId}/status`, { method: 'PATCH', mutating: true, body: { employeeStatus: nextStatus } }); notify(disabling ? '店员已停用' : '店员已重新启用'); renderEmployees();
  }, disabling ? '确认停用' : '确认启用');
}

function openGenerateBillModal() {
  const period = new Date().toISOString().slice(0, 7);
  openModal('生成月账单草稿', `<div class="field"><label for="billing-period">账期</label><input id="billing-period" value="${period}" pattern="\\d{4}-\\d{2}" required><small>仅归集尚未归属账单的不可变分录。</small></div><div class="field-error" role="alert"></div>`, async node => {
    const billingPeriod = node.querySelector('#billing-period').value; await api('/admin/billing/monthly-bills/generate', { method: 'POST', mutating: true, body: { billingPeriod } }); notify('账单草稿已生成'); renderBills();
  }, '生成');
}

bootstrap();
