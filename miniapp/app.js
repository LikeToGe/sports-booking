function getOrCreateDeviceId() {
  const stored = localStorage.getItem('sportsBookingDeviceId');
  if (stored && stored.length <= 128) return stored;
  const generated = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;
  localStorage.setItem('sportsBookingDeviceId', generated);
  return generated;
}

const deviceId = getOrCreateDeviceId();

const state = {
  apiBase: localStorage.getItem('sportsBookingApi') || 'http://127.0.0.1:8080/api',
  user: readSession('sportsBookingMiniUser'),
  staff: readSession('sportsBookingStaff'),
  view: 'venues',
  date: businessDate(0),
  selectedVenueId: null,
  pages: {},
  pageSizes: {},
  venueKeyword: '',
  venueCategoryId: '',
  orderStatus: '',
};

const app = document.querySelector('#app');
const toast = document.querySelector('#toast');

function readSession(key) {
  try { return JSON.parse(localStorage.getItem(key) || 'null'); } catch (_) { return null; }
}
function writeSession(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char])); }
function icon(name) { return `<i data-lucide="${name}" aria-hidden="true"></i>`; }
function refreshIcons() { window.lucide?.createIcons({ attrs: { 'stroke-width': 1.8 } }); }
function cssToken(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }
function key() { return `${Date.now()}-${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`; }
function money(fen) { return fen == null ? '-' : `¥${(Number(fen) / 100).toFixed(2)}`; }
function businessDate(offset) { return new Date(Date.now() + offset * 86400000).toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }); }
function dateLabel(date) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', weekday: 'short', timeZone: 'Asia/Shanghai' }).format(new Date(`${date}T00:00:00+08:00`)); }
function statusClass(value) {
  if (['CONFIRMED', 'COMPLETED', 'VERIFIED', 'REFUND_SUCCEEDED', 'SUCCEEDED', 'ACTIVE'].includes(value)) return 'status-good';
  if (['PENDING_PAYMENT', 'PROCESSING', 'CREATED'].includes(value)) return 'status-info';
  if (['UNKNOWN', 'PAYMENT_UNKNOWN', 'REFUND_UNKNOWN'].includes(value)) return 'status-warn';
  return 'status-bad';
}
const labels = { ACTIVE: '正常', PENDING_PAYMENT: '待支付', PAYMENT_PROCESSING: '支付处理中', PAYMENT_UNKNOWN: '支付待确认', CONFIRMED: '待核销', COMPLETED: '已完成', CANCELLED: '已取消', VERIFIED: '已核销', UNVERIFIED: '未核销', SUCCEEDED: '成功', FAILED: '失败', PROCESSING: '处理中', UNKNOWN: '待确认', REFUND_SUCCEEDED: '退款完成', REFUND_UNKNOWN: '退款待确认', CREATED: '已创建' };
function label(value) { return labels[value] || value || '-'; }
function pageFor(key) { return state.pages[key] || 1; }
function pageSizeFor(key) { return state.pageSizes[key] || 10; }
function pager(data, key) {
  const current = Number(data.current || 1); const size = Number(data.size || pageSizeFor(key)); const total = Number(data.total || 0); const pages = Math.max(1, Math.ceil(total / size));
  const sizes = [10, 20, 50].map(value => `<option value="${value}" ${size === value ? 'selected' : ''}>${value} 条</option>`).join('');
  return `<nav class="mobile-pager" aria-label="分页"><span>第 ${current}/${pages} 页，共 ${total} 条</span><label>每页 <select data-page-size-key="${key}">${sizes}</select></label><button class="btn btn-small" data-page-key="${key}" data-page="${current - 1}" ${current === 1 ? 'disabled' : ''}>上一页</button><button class="btn btn-small" data-page-key="${key}" data-page="${current + 1}" ${current === pages ? 'disabled' : ''}>下一页</button></nav>`;
}
function bindPager(key, render) { document.querySelectorAll(`[data-page-key="${key}"]`).forEach(button => button.addEventListener('click', () => { state.pages[key] = Number(button.dataset.page); render(); })); document.querySelector(`[data-page-size-key="${key}"]`)?.addEventListener('change', event => { state.pageSizes[key] = Number(event.target.value); state.pages[key] = 1; render(); }); }
function status(value) { return `<span class="status ${statusClass(value)}">${escapeHtml(label(value))}</span>`; }
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
function notify(message, isError = false) { toast.textContent = messageOf(message); toast.setAttribute('role', isError ? 'alert' : 'status'); toast.className = `toast show${isError ? ' error' : ''}`; clearTimeout(notify.timer); notify.timer = setTimeout(() => { toast.className = 'toast'; }, 3800); }
function imageMarkup(url) {
  try { const parsed = new URL(url); if (parsed.protocol === 'https:' || parsed.protocol === 'http:') return `<img class="venue-image" src="${escapeHtml(parsed.href)}" alt="场馆图片">`; } catch (_) { /* Fall through to the neutral venue marker. */ }
  return `<div class="venue-image-fallback">${icon('building-2')}</div>`;
}

async function request(path, options = {}) {
  const { method = 'GET', body, token = null, mutating = false } = options;
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  if (mutating) {
    headers['Idempotency-Key'] = key();
    headers['X-Device-Id'] = deviceId;
  }
  let response;
  try {
    response = await fetch(`${state.apiBase}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (error) {
    throw new Error(messageOf(error));
  }
  const payload = await response.json().catch(() => ({ code: 0, msg: '服务返回了不可读取的响应' }));
  if (!response.ok || payload.code !== 1) {
    const error = new Error(messageOf(payload.msg || `请求失败（${response.status}）`));
    error.status = response.status;
    throw error;
  }
  return payload.data;
}
function userApi(path, options = {}) { return request(path, { ...options, token: state.user?.accessToken }); }
function staffApi(path, options = {}) { return request(path, { ...options, token: state.staff?.accessToken }); }

function renderLogin() {
  app.innerHTML = `<section class="login"><div class="login-copy"><div class="brand"><span class="brand-mark">${icon('calendar-check-2')}</span><span>场地预约</span></div><h1>选定时段，完成预约。</h1><p>开发测试环境使用模拟身份和支付服务。</p></div><section class="login-panel"><form id="login-form"><h2>用户登录</h2><p>登录后可浏览场馆并完成预约流程。</p><div class="field"><label for="api-base">后端地址</label><input id="api-base" value="${escapeHtml(state.apiBase)}" required></div><div class="field"><label for="mock-code">模拟用户代码</label><input id="mock-code" value="mock:mini-browser-user" required></div><button class="btn btn-primary btn-wide" type="submit">进入预约</button></form></section></section>`;
  document.querySelector('#login-form').addEventListener('submit', loginUser); refreshIcons();
}

async function loginUser(event) {
  event.preventDefault(); const form = event.currentTarget;
  state.apiBase = form.querySelector('#api-base').value.replace(/\/$/, ''); localStorage.setItem('sportsBookingApi', state.apiBase);
  const button = form.querySelector('button'); button.disabled = true;
  try { state.user = await request('/mini/auth/login', { method: 'POST', body: { code: form.querySelector('#mock-code').value.trim() } }); writeSession('sportsBookingMiniUser', state.user); renderShell(); } catch (reason) { notify(reason, true); button.disabled = false; }
}

function renderShell() {
  app.innerHTML = `<section class="screen"><header class="mobile-header"><div class="brand"><span class="brand-mark">${icon('calendar-check-2')}</span><span>场地预约</span></div><button class="icon-btn" id="settings" aria-label="后端设置">${icon('settings-2')}</button></header><section id="content" class="content"></section><nav class="bottom-nav" aria-label="主导航"><button data-view="venues" ${state.view === 'venues' ? 'aria-current="page"' : ''}>${icon('map-pinned')}<span>场馆</span></button><button data-view="orders" ${state.view === 'orders' ? 'aria-current="page"' : ''}>${icon('receipt-text')}<span>订单</span></button><button data-view="mine" ${['mine','staff'].includes(state.view) ? 'aria-current="page"' : ''}>${icon('user-round')}<span>我的</span></button></nav></section>`;
  document.querySelector('#settings').addEventListener('click', openSettingsModal);
  document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => { state.view = button.dataset.view; state.selectedVenueId = null; renderCurrent(); }));
  renderCurrent(); refreshIcons();
}

async function renderCurrent() {
  const target = document.querySelector('#content'); target.innerHTML = '<div class="loading">正在加载...</div>';
  try {
    if (state.view === 'venues') return state.selectedVenueId ? renderVenueDetail(state.selectedVenueId) : renderVenues();
    if (state.view === 'orders') return renderOrders();
    if (state.view === 'mine') return renderMine();
    return renderStaff();
  } catch (error) {
    if (error.status === 401) { state.user = null; localStorage.removeItem('sportsBookingMiniUser'); renderLogin(); return; }
    target.innerHTML = `<div class="empty">${escapeHtml(messageOf(error))}<br><button class="btn btn-primary retry-button" id="retry">重新加载</button></div>`;
    document.querySelector('#retry').addEventListener('click', renderCurrent);
  }
}

async function renderVenues() {
  const query = new URLSearchParams({ date: state.date, page: String(pageFor('venues')), pageSize: String(pageSizeFor('venues')) });
  if (state.venueKeyword) query.set('keyword', state.venueKeyword);
  if (state.venueCategoryId) query.set('sportCategoryId', state.venueCategoryId);
  const [data, categories] = await Promise.all([request(`/mini/venues?${query}`), request(`/mini/venues/sport-categories?date=${encodeURIComponent(state.date)}`)]);
  const dates = Array.from({ length: 7 }, (_, index) => businessDate(index));
  document.querySelector('#content').innerHTML = `<section class="mini-hero"><span>场地预约</span><h1>今天想去哪儿运动？</h1><p>选择日期与项目，锁定你的运动时间。</p></section><div class="date-strip">${dates.map(date => `<button data-date="${date}" ${state.date === date ? 'aria-current="date"' : ''}>${dateLabel(date)}</button>`).join('')}</div><form class="mobile-filters" id="venue-filters"><label class="search-field">${icon('search')}<span class="sr-only">搜索场馆</span><input id="venue-keyword" value="${escapeHtml(state.venueKeyword)}" placeholder="名称或地址"></label><select id="venue-category" aria-label="运动项目"><option value="">全部项目</option>${categories.map(item => `<option value="${item.id}" ${state.venueCategoryId === item.id ? 'selected' : ''}>${escapeHtml(item.name)}</option>`).join('')}</select><button class="btn btn-secondary btn-small" type="submit">筛选</button></form><div class="list-heading"><h2>可预约场馆</h2><span>${data.total} 家</span></div><div class="venue-list">${data.records.map(venue => `<button class="venue-row" data-venue="${venue.storeId}">${imageMarkup(venue.imageUrls?.[0])}<span><h2>${escapeHtml(venue.storeName)}</h2><p>${escapeHtml(venue.address || '地址待补充')}</p><p>${escapeHtml(venue.sportCategoryNames || (venue.facilities || []).join(' · ') || '固定时段预约')}</p>${venue.startingPriceFen != null ? `<strong class="venue-price">${money(venue.startingPriceFen)} 起</strong>` : ''}</span><span class="chevron">${icon('chevron-right')}</span></button>`).join('') || '<div class="empty">当前条件下没有可预约场馆</div>'}</div>${pager(data, 'venues')}`;
  document.querySelector('#venue-filters').addEventListener('submit', event => { event.preventDefault(); state.venueKeyword = document.querySelector('#venue-keyword').value.trim(); state.venueCategoryId = document.querySelector('#venue-category').value; state.pages.venues = 1; renderVenues(); });
  document.querySelectorAll('[data-date]').forEach(button => button.addEventListener('click', () => { state.date = button.dataset.date; state.pages.venues = 1; renderVenues(); }));
  document.querySelectorAll('[data-venue]').forEach(button => button.addEventListener('click', () => { state.selectedVenueId = button.dataset.venue; renderCurrent(); })); refreshIcons();
  bindPager('venues', renderVenues);
}

async function renderVenueDetail(storeId) {
  const venue = await request(`/mini/venues/${storeId}?date=${encodeURIComponent(state.date)}`);
  document.querySelector('#content').innerHTML = `<button class="btn btn-small" id="back-venues">${icon('arrow-left')}返回场馆</button><section class="detail-hero" style="margin-top:12px"><h2>${escapeHtml(venue.storeName)}</h2><p>${escapeHtml(venue.introduction || venue.address || '固定时段预约')}</p></section><div class="detail-meta"><span>${icon('map-pin')} ${escapeHtml(venue.address || '地址待补充')}</span><span>${icon('phone')} ${escapeHtml(venue.contactPhone || '联系电话待补充')}</span><span>${icon('circle-check')} ${escapeHtml((venue.facilities || []).join(' · ') || '请以入场说明为准')}</span></div><div class="section-heading"><h2>${state.date} 可预约时段</h2><span class="mono">${venue.slots.length} 个</span></div><div class="slot-list">${venue.slots.map(slot => `<section class="slot-row"><div><strong>${escapeHtml(slot.sellableUnitName)}</strong><span>${slot.startTime}-${slot.endTime} · v${slot.sellableUnitVersionNo}</span><div class="slot-price">${money(slot.priceFen)}</div></div><button class="btn btn-primary btn-small" data-slot="${slot.id}">预约</button></section>`).join('') || '<div class="empty">当前日期没有可用固定时段</div>'}</div><div class="section-heading"><h2>入场说明</h2></div><p class="page-subtitle">${escapeHtml(venue.entryNotice || '暂无入场说明')}</p>`;
  document.querySelector('#back-venues').addEventListener('click', () => { state.selectedVenueId = null; renderCurrent(); });
  document.querySelectorAll('[data-slot]').forEach(button => button.addEventListener('click', () => openBookingModal(venue, venue.slots.find(slot => slot.id === button.dataset.slot)))); refreshIcons();
}

function openBookingModal(venue, slot) {
  openModal('确认预约', `<div class="booking-form"><div class="detail-sheet booking-summary"><dl><dt>场馆</dt><dd>${escapeHtml(venue.storeName)}</dd><dt>场地</dt><dd>${escapeHtml(slot.sellableUnitName)}</dd><dt>时段</dt><dd class="mono">${state.date} ${slot.startTime}-${slot.endTime}</dd><dt>金额</dt><dd class="mono">${money(slot.priceFen)}</dd></dl></div><div class="field" style="margin-top:16px"><label for="contact-name">联系人</label><input id="contact-name" maxlength="64" required></div><div class="field"><label for="contact-phone">手机号</label><input id="contact-phone" inputmode="numeric" maxlength="32" required></div><p class="policy-note">开始前可取消；已支付订单取消后自动发起全额退款。</p><label class="consent"><input id="privacy-consent" type="checkbox" required><span>我已阅读并同意《预约隐私政策 v1》，授权本次预约使用联系人和手机号。</span></label><div class="field-error" role="alert"></div></div>`, async node => {
    const contactName = node.querySelector('#contact-name').value.trim(); const contactPhone = node.querySelector('#contact-phone').value.trim(); if (!contactName || !contactPhone) throw new Error('请填写联系人和手机号'); if (!node.querySelector('#privacy-consent').checked) throw new Error('请先阅读并同意预约隐私政策');
    const booking = await userApi('/mini/bookings', { method: 'POST', mutating: true, body: { inventorySlotId: slot.id, contactName, contactPhone, privacyPolicyVersion: 'v1', privacyConsentScene: 'booking' } }); notify('预约已创建，请完成模拟支付'); state.view = 'orders'; renderOrderDetail(booking.id);
  }, '提交预约', 'booking-modal');
}

async function renderOrders() {
  const query = new URLSearchParams({ page: String(pageFor('orders')), pageSize: String(pageSizeFor('orders')) });
  if (state.orderStatus) query.set('status', state.orderStatus);
  const data = await userApi(`/mini/bookings?${query}`);
  document.querySelector('#content').innerHTML = `<h1 class="page-title">我的订单</h1><p class="page-subtitle">支付、退款和核销状态以订单详情为准。</p><form class="status-tabs" id="order-filters">${[['','全部'],['PENDING_PAYMENT','待支付'],['PAYMENT_PROCESSING','处理中'],['PAYMENT_UNKNOWN','待确认'],['CONFIRMED','待使用'],['COMPLETED','已完成'],['CANCELLED','已取消']].map(([value, text]) => `<button type="submit" name="status" value="${value}" class="${state.orderStatus === value ? 'is-active' : ''}">${text}</button>`).join('')}</form><div class="order-list">${data.records.map(order => orderRow(order)).join('') || '<div class="empty">当前状态没有订单</div>'}</div>${pager(data, 'orders')}`;
  document.querySelector('#order-filters').addEventListener('submit', event => { event.preventDefault(); state.orderStatus = event.submitter?.value || ''; state.pages.orders = 1; renderOrders(); });
  document.querySelectorAll('[data-order]').forEach(button => button.addEventListener('click', () => renderOrderDetail(button.dataset.order)));
  bindPager('orders', renderOrders);
}

function orderRow(order) {
  return `<button class="order-row" data-order="${order.id}"><span class="order-row-top"><span><h2>${escapeHtml(order.storeName)}</h2><span class="mono">${order.date} ${order.startTime}-${order.endTime}</span></span>${status(order.orderStatus)}</span><span>${escapeHtml(order.sellableUnitName)} · <strong>${money(order.priceFen)}</strong></span><span class="muted mono">${escapeHtml(order.orderNo)}</span></button>`;
}

async function renderOrderDetail(orderId) {
  const order = await userApi(`/mini/bookings/${orderId}`);
  const paymentRecoverable = order.paymentId && ['CREATED', 'PROCESSING', 'UNKNOWN'].includes(order.paymentStatus);
  const refundRecoverable = order.refundId && !['REFUND_SUCCEEDED', 'FAILED'].includes(order.refundStatus);
  document.querySelector('#content').innerHTML = `<button class="btn btn-small" id="back-orders">${icon('arrow-left')}返回订单</button><h1 class="page-title" style="margin-top:16px">订单详情</h1><section class="detail-sheet"><div>${status(order.orderStatus)} ${status(order.verificationStatus)}</div><dl><dt>订单号</dt><dd class="mono">${escapeHtml(order.orderNo)}</dd><dt>场馆</dt><dd>${escapeHtml(order.storeName)}</dd><dt>场地时段</dt><dd>${escapeHtml(order.sellableUnitName)}<br><span class="mono">${order.date} ${order.startTime}-${order.endTime}</span></dd><dt>预约金额</dt><dd class="mono">${money(order.priceFen)}</dd><dt>联系人</dt><dd>${escapeHtml(order.contactName)} ${escapeHtml(order.contactPhone)}</dd><dt>支付状态</dt><dd>${order.paymentStatus ? status(order.paymentStatus) : '-'}</dd><dt>支付时间</dt><dd class="mono">${escapeHtml(order.payTime || '-')}</dd><dt>退款状态</dt><dd>${order.refundStatus ? status(order.refundStatus) : '-'}</dd><dt>核销时间</dt><dd class="mono">${escapeHtml(order.verifiedAt || '-')}</dd></dl><div class="order-actions">${order.orderStatus === 'PENDING_PAYMENT' ? `<button class="btn btn-primary" id="pay-order">${icon('credit-card')}模拟支付</button>` : ''}${paymentRecoverable ? `<button class="btn btn-secondary" id="refresh-payment">${icon('refresh-cw')}查询支付</button>` : ''}${refundRecoverable ? `<button class="btn btn-secondary" id="refresh-refund">${icon('refresh-cw')}查询退款</button>` : ''}${['PENDING_PAYMENT', 'CONFIRMED'].includes(order.orderStatus) && order.verificationStatus !== 'VERIFIED' ? `<button class="btn btn-danger" id="cancel-order">${icon('x-circle')}取消预约</button>` : ''}${order.orderStatus === 'CONFIRMED' ? `<button class="btn btn-primary" id="show-credential">${icon('qr-code')}核销凭证</button>` : ''}</div></section>`;
  document.querySelector('#back-orders').addEventListener('click', renderOrders);
  document.querySelector('#pay-order')?.addEventListener('click', () => beginPayment(order));
  document.querySelector('#refresh-payment')?.addEventListener('click', () => refreshPayment(order.paymentId, order.id));
  document.querySelector('#refresh-refund')?.addEventListener('click', () => refreshRefund(order.refundId, order.id));
  document.querySelector('#cancel-order')?.addEventListener('click', () => cancelOrder(order));
  document.querySelector('#show-credential')?.addEventListener('click', () => showCredential(order)); refreshIcons();
}

async function beginPayment(order) {
  const payment = await userApi('/mini/payments', { method: 'POST', mutating: true, body: { orderId: order.id } });
  const node = openModal('模拟支付', `<div class="detail-sheet"><dl><dt>订单号</dt><dd class="mono">${escapeHtml(order.orderNo)}</dd><dt>金额</dt><dd class="mono">${money(payment.amountFen)}</dd><dt>支付渠道</dt><dd>模拟支付服务</dd><dt>库存保留</dt><dd id="hold-countdown" class="mono">计算中</dd></dl></div><p class="policy-note">关闭窗口不会取消订单，可从订单详情继续查询支付状态。</p><div class="field-error" role="alert"></div>`, async () => {}, '暂不支付');
  const expiresAt = new Date(`${order.holdExpiresAt.replace(' ', 'T')}Z`).getTime();
  const updateCountdown = () => {
    const seconds = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
    const target = node.querySelector('#hold-countdown');
    if (target) target.textContent = seconds ? `${Math.floor(seconds / 60)}分${String(seconds % 60).padStart(2, '0')}秒` : '已到期';
  };
  updateCountdown();
  const timer = setInterval(updateCountdown, 1000);
  node.addEventListener('close', () => clearInterval(timer), { once: true });
  const actions = document.createElement('div'); actions.className = 'modal-foot'; actions.innerHTML = `<button class="btn btn-primary" data-payment-outcome="SUCCESS">成功</button><button class="btn" data-payment-outcome="UNKNOWN">处理中</button><button class="btn btn-danger" data-payment-outcome="FAILED">失败</button>`; node.querySelector('.modal').append(actions);
  node.querySelectorAll('[data-payment-outcome]').forEach(button => button.addEventListener('click', async () => {
    button.disabled = true;
    try { await userApi(`/mini/payments/${payment.id}/mock-result`, { method: 'POST', mutating: true, body: { outcome: button.dataset.paymentOutcome, providerEventNo: `mini-web-${Date.now()}` } }); node.close(); notify('模拟支付结果已提交'); renderOrderDetail(order.id); } catch (error) { button.disabled = false; notify(error, true); }
  }));
}

async function refreshPayment(paymentId, orderId) {
  const payment = await userApi(`/mini/payments/${paymentId}`);
  notify(`支付状态：${label(payment.paymentStatus)}`);
  renderOrderDetail(orderId);
}

async function refreshRefund(refundId, orderId) {
  const refund = await userApi(`/mini/refunds/${refundId}`);
  notify(`退款状态：${label(refund.refundStatus)}`);
  renderOrderDetail(orderId);
}

async function cancelOrder(order) {
  const node = openModal(`取消 ${order.orderNo}`, '<p class="page-subtitle">取消前会再次校验预约开始时间与核销事实。</p><div class="field-error" role="alert"></div>', async () => {
    const result = await userApi(`/mini/bookings/${order.id}/cancel`, { method: 'POST', mutating: true }); notify('预约已取消'); if (result.refundId) openRefundModal(result.refundId, order.id); else renderOrderDetail(order.id);
  }, '确认取消');
  return node;
}

function openRefundModal(refundId, orderId) {
  const node = openModal('模拟退款', '<p class="page-subtitle">退款义务已创建，可提交模拟到账结果完成退款。</p><div class="field-error" role="alert"></div>', async () => {}, '稍后处理');
  const action = document.createElement('div'); action.className = 'modal-foot'; action.innerHTML = '<button class="btn btn-primary" data-refund-success>模拟到账</button>'; node.querySelector('.modal').append(action);
  action.querySelector('[data-refund-success]').addEventListener('click', async event => { event.currentTarget.disabled = true; try { await userApi(`/mini/refunds/${refundId}/mock-result`, { method: 'POST', mutating: true, body: { outcome: 'SUCCESS' } }); node.close(); notify('退款已完成'); renderOrderDetail(orderId); } catch (error) { event.currentTarget.disabled = false; notify(error, true); } });
}

async function showCredential(order) {
  const credential = await userApi(`/mini/bookings/${order.id}/verification-credential`, { method: 'POST', mutating: true });
  document.querySelector('#content').innerHTML = `<button class="btn btn-small" id="back-order-detail">${icon('arrow-left')}返回订单</button><h1 class="page-title" style="margin-top:16px">核销凭证</h1><section class="qr-panel"><canvas id="credential-qr" width="232" height="232"></canvas><strong class="code-large">${escapeHtml(credential.shortCode)}</strong><span class="mono muted">${escapeHtml(credential.validFrom)} 至 ${escapeHtml(credential.validTo)}</span><span>${escapeHtml(credential.storeName)} · ${escapeHtml(credential.sellableUnitName)}</span><button class="btn btn-secondary" id="refresh-credential">${icon('refresh-cw')}刷新凭证</button></section>`;
  document.querySelector('#back-order-detail').addEventListener('click', () => renderOrderDetail(order.id));
  document.querySelector('#refresh-credential').addEventListener('click', () => showCredential(order));
  if (window.QRCode) window.QRCode.toCanvas(document.querySelector('#credential-qr'), credential.qrToken, { width: 232, margin: 1, color: { dark: cssToken('--color-ink'), light: cssToken('--color-surface') } });
  else notify('二维码组件未加载，短码仍可用于核销', true); refreshIcons();
}

function renderMine() {
  document.querySelector('#content').innerHTML = `<h1 class="page-title">我的</h1><p class="page-subtitle">管理当前登录会话与店员核销入口。</p><section class="detail-sheet"><dl><dt>用户编号</dt><dd class="mono">${escapeHtml(state.user.userId)}</dd><dt>会话有效至</dt><dd class="mono">${escapeHtml(state.user.accessExpiresAt)}</dd></dl></section><div class="mine-actions"><button class="mine-action" id="enter-staff"><span>${icon('scan-line')}<strong>店员核销</strong></span>${icon('chevron-right')}</button><button class="mine-action danger" id="user-logout"><span>${icon('log-out')}<strong>退出登录</strong></span></button></div>`;
  document.querySelector('#enter-staff').addEventListener('click', () => { state.view = 'staff'; renderCurrent(); });
  document.querySelector('#user-logout').addEventListener('click', logoutUser);
  refreshIcons();
}

async function logoutUser() {
  try { await userApi('/mini/auth/logout', { method: 'POST' }); } catch (_) { /* 本地清理仍可阻止旧会话继续使用。 */ }
  state.user = null;
  state.staff = null;
  localStorage.removeItem('sportsBookingMiniUser');
  localStorage.removeItem('sportsBookingStaff');
  renderLogin();
}

async function renderStaff() {
  if (!state.staff?.accessToken) return renderStaffBinding();
  try {
    const [session, records] = await Promise.all([staffApi('/staff/binding/session'), staffApi(`/staff/verifications/mine?page=${pageFor('staff')}&pageSize=${pageSizeFor('staff')}`)]);
    document.querySelector('#content').innerHTML = `<button class="btn btn-small" id="back-mine">${icon('arrow-left')}返回我的</button><h1 class="page-title">店员核销</h1><section class="staff-session"><strong>${escapeHtml(session.employeeName)}</strong><br><span class="mono">门店 ${escapeHtml(session.storeId)}</span></section><section class="detail-sheet"><div class="field"><label for="short-code">6 位短码</label><input id="short-code" inputmode="numeric" maxlength="6" placeholder="输入用户短码"></div><button class="btn btn-primary btn-wide" id="precheck-short">预检短码</button><div class="field" style="margin-top:16px"><label for="qr-token">二维码令牌</label><textarea id="qr-token" placeholder="扫码设备可将令牌填入此处"></textarea></div><button class="btn btn-secondary btn-wide" id="precheck-qr">预检二维码</button></section><div class="section-heading"><h2>本人核销记录</h2><button class="btn btn-small" id="staff-logout">退出店员</button></div><div class="attempt-list">${records.records.map(item => `<section class="attempt"><strong>${escapeHtml(item.orderNo)}</strong><span>${escapeHtml(item.sellableUnitName)} · ${item.date} ${item.startTime}-${item.endTime}</span><br>${status(item.attemptResult)} <span class="mono">${escapeHtml(item.createTime)}</span>${item.failureReason ? `<br><span class="muted">${escapeHtml(item.failureReason)}</span>` : ''}</section>`).join('') || '<div class="empty">暂无核销记录</div>'}</div>${pager(records, 'staff')}`;
    document.querySelector('#back-mine').addEventListener('click', () => { state.view = 'mine'; renderCurrent(); });
    document.querySelector('#precheck-short').addEventListener('click', () => precheckStaff('/staff/verifications/short-code/precheck', { shortCode: document.querySelector('#short-code').value.trim() }));
    document.querySelector('#precheck-qr').addEventListener('click', () => precheckStaff('/staff/verifications/qr/precheck', { qrToken: document.querySelector('#qr-token').value.trim() }));
    document.querySelector('#staff-logout').addEventListener('click', logoutStaff); bindPager('staff', renderStaff); refreshIcons();
  } catch (error) { if (error.status === 401) { state.staff = null; localStorage.removeItem('sportsBookingStaff'); return renderStaffBinding(); } throw error; }
}

function renderStaffBinding() {
  document.querySelector('#content').innerHTML = `<button class="btn btn-small" id="back-mine">${icon('arrow-left')}返回我的</button><h1 class="page-title">店员身份绑定</h1><p class="page-subtitle">使用管理端一次性绑定码，建立独立的店员会话。</p><section class="detail-sheet"><form id="staff-bind-form"><div class="field"><label for="binding-code">绑定码</label><input id="binding-code" autocomplete="one-time-code" required></div><div class="field"><label for="staff-mock-code">模拟微信代码</label><input id="staff-mock-code" value="mock:mini-browser-staff" required></div><div class="field-error" role="alert"></div><button class="btn btn-primary btn-wide" type="submit">绑定并进入核销</button></form></section>`;
  document.querySelector('#back-mine').addEventListener('click', () => { state.view = 'mine'; renderCurrent(); });
  document.querySelector('#staff-bind-form').addEventListener('submit', bindStaff); refreshIcons();
}

async function bindStaff(event) {
  event.preventDefault(); const form = event.currentTarget; const error = form.querySelector('.field-error'); error.textContent = '';
  try { state.staff = await request('/staff/binding', { method: 'POST', body: { bindingCode: form.querySelector('#binding-code').value.trim(), code: form.querySelector('#staff-mock-code').value.trim() } }); writeSession('sportsBookingStaff', state.staff); renderStaff(); } catch (reason) { error.textContent = messageOf(reason); }
}

async function precheckStaff(path, body) {
  try { if (!Object.values(body)[0]) throw new Error('请填写核销凭证'); const credential = await staffApi(path, { method: 'POST', body }); openVerificationConfirm(credential); } catch (error) { notify(error, true); }
}

function openVerificationConfirm(credential) {
  openModal('确认核销', `<section class="detail-sheet"><dl><dt>订单号</dt><dd class="mono">${escapeHtml(credential.orderNo)}</dd><dt>场馆</dt><dd>${escapeHtml(credential.storeName)}</dd><dt>场地时段</dt><dd>${escapeHtml(credential.sellableUnitName)}<br><span class="mono">${credential.date} ${credential.startTime}-${credential.endTime}</span></dd><dt>用户</dt><dd>${escapeHtml(credential.contactName)} ${escapeHtml(credential.contactPhone)}</dd></dl></section><div class="field-error" role="alert"></div>`, async () => {
    await staffApi(`/staff/verifications/${credential.credentialId}/confirm`, { method: 'POST', body: { verificationMethod: credential.verificationMethod } }); notify('核销成功'); renderStaff();
  }, '确认核销');
}

async function logoutStaff() {
  try { await staffApi('/staff/binding/logout', { method: 'POST' }); } catch (_) { /* Local cleanup still prevents an obsolete token from being reused. */ }
  state.staff = null; localStorage.removeItem('sportsBookingStaff'); renderStaff();
}

function openSettingsModal() {
  openModal('后端连接', `<div class="field"><label for="settings-api">后端地址</label><input id="settings-api" value="${escapeHtml(state.apiBase)}" required></div><div class="field-error" role="alert"></div>`, async node => {
    state.apiBase = node.querySelector('#settings-api').value.replace(/\/$/, ''); localStorage.setItem('sportsBookingApi', state.apiBase); notify('后端地址已保存'); renderCurrent();
  }, '保存');
}

function openModal(title, body, onConfirm, confirmText = '确认', modalClass = '') {
  const node = document.createElement('dialog'); node.className = 'modal-backdrop';
  node.innerHTML = `<section class="modal ${modalClass}" aria-labelledby="modal-title"><div class="modal-head"><h2 id="modal-title">${escapeHtml(title)}</h2><button class="icon-btn" data-close aria-label="关闭">${icon('x')}</button></div><div>${body}</div><div class="modal-foot"><button class="btn" data-close>取消</button><button class="btn btn-primary" data-confirm>${escapeHtml(confirmText)}</button></div></section>`;
  document.body.append(node); node.showModal(); refreshIcons();
  const close = () => node.close();
  node.addEventListener('close', () => node.remove(), { once: true });
  node.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', close)); node.addEventListener('click', event => { if (event.target === node) close(); });
  node.querySelector('[data-confirm]').addEventListener('click', async () => { const button = node.querySelector('[data-confirm]'); button.disabled = true; try { await onConfirm(node); close(); } catch (error) { button.disabled = false; const target = node.querySelector('.field-error'); if (target) target.textContent = messageOf(error); else notify(error, true); } });
  node.querySelector('input, textarea, select')?.focus(); return node;
}

if (state.user?.accessToken) renderShell(); else renderLogin();
