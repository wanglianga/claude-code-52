// 通用：依赖 Spring Security 表单登录后的会话 Cookie
async function api(method, url, body) {
  const opt = { method, headers: { 'Content-Type': 'application/json' } };
  if (body !== undefined) opt.body = JSON.stringify(body);
  const res = await fetch(url, opt);
  let data = null;
  try { data = await res.json(); } catch (e) { /* ignore */ }
  if (!res.ok) {
    throw new Error(data && data.error ? data.error : ('HTTP ' + res.status));
  }
  return data;
}

function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function badge(s) {
  const map = { CLOSED: 'b-CLOSED-EVT' };
  const cls = map[s] || ('b-' + s);
  const label = {
    PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', OPERATING: '营业中',
    POWER_CUT: '断电中', SUSPENDED: '责令停业', MIGRATING: '迁线中', CLOSED: '已撤摊',
    OPEN: '处置中', RECOVERED: '已复电'
  }[s] || s;
  return `<span class="badge ${cls}">${label}</span>`;
}
function deviceLabel(k) {
  return ({ STOVE: '烤炉/电热', FREEZER: '冰柜/冷柜', LIGHTBOX: '灯牌', SPEAKER: '音响', LIGHTING: '照明', EQUIP: '其他' })[k] || k;
}
function roleLabel(r) {
  return ({ VENDOR: '摊主', ELECTRICIAN: '电工', ADMIN: '管理员', SECURITY: '安保', CASHIER: '收费', FIREFIGHTER: '消防' })[r] || r || '';
}
function enumLabel(e) {
  return ({
    PASS: '检查合格', RECTIFY: '限期整改', FAIL: '不合格·立即停业',
    TRIP: '跳闸', OVERHEAT: '线路发热', PRIVATE_WIRING: '私拉电线',
    WATERLOG: '雨水浸泡', ODOR_COMPLAINT: '投诉异味', FIRE_HAZARD: '消防隐患',
    VENDOR: '摊主', ELECTRICIAN: '电工', MARKET: '市场方', FORCE_MAJURE: '不可抗力',
    FOOD: '餐饮热食', SNACK: '小吃加工', DRINK: '饮品冰品', RETAIL: '百货零售', GAME: '游艺娱乐', OTHER: '其他'
  })[e] || e || '';
}
function toast(msg, ok) {
  const box = document.getElementById('toast');
  if (!box) { alert(msg); return; }
  box.textContent = msg;
  box.className = ok ? 'ok' : 'err';
  setTimeout(() => { box.textContent = ''; }, 6000);
}
async function refreshAll() {
  if (typeof load === 'function') {
    try { await load(); } catch (e) { toast(e.message, false); }
  }
}
