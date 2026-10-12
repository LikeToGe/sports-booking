<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { Check, RefreshCw, ShieldAlert, Undo2, X } from "@lucide/vue";
import { api, localDateTime } from "../api";

const props = defineProps<{ kind: "reports" | "appeals" }>();
interface CaseRow { id: number; userId: number; targetType: string; targetId: number; reason?: string; description?: string; action?: string; status: string; createdAt: string; decisionReason?: string; }
const rows = ref<CaseRow[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const message = ref("");
const pendingOnly = ref(true);
const keyword = ref("");
const targetType = ref("");
const statusFilter = ref("");
const selected = ref<CaseRow | null>(null);
const result = ref("");
const reason = ref("");
const dialog = ref<HTMLDialogElement | null>(null);
const title = computed(() => props.kind === "reports" ? "内容举报" : "内容申诉");
const statuses: Record<string, string> = { PENDING: "待处理", RESOLVED: "已处理", REJECTED: "已驳回", UPHELD: "维持处置", REVERSED: "已撤销" };
const targets: Record<string, string> = { POST: "动态", REVIEW: "评价", REVIEW_REPLY: "官方回复", COMMENT: "评论", USER: "用户" };
const reasons: Record<string, string> = { SPAM: "广告或垃圾内容", ABUSE: "辱骂或骚扰", PRIVACY: "泄露隐私", MISLEADING: "不实内容", OTHER: "其他" };
const actions: Record<string, string> = { REJECT: "拒绝", HIDE: "隐藏", DELETE: "删除", RESTORE: "恢复", RESTRICT: "限制内容权限" };
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => {
    const targetLabel = targets[row.targetType] || row.targetType;
    const text = [targetLabel, row.targetType, String(row.targetId), String(row.userId), row.reason, row.description, row.decisionReason].filter(Boolean).join(" ").toLowerCase();
    return (!query || text.includes(query)) && (!targetType.value || row.targetType === targetType.value) && (!statusFilter.value || row.status === statusFilter.value);
  });
});

async function load() {
  loading.value = true; error.value = "";
  try {
    const status = statusFilter.value || (pendingOnly.value ? "PENDING" : "");
    rows.value = await api<CaseRow[]>(`/platform/content-governance/${props.kind}${status ? `?status=${status}` : ""}`);
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : "加载失败"; }
  finally { loading.value = false; }
}
function decide(row: CaseRow, status: string) {
  selected.value = row; result.value = status; reason.value = ""; dialog.value?.showModal();
}
async function submit() {
  if (!selected.value || !reason.value.trim() || saving.value) return;
  saving.value = true; error.value = "";
  try {
    const suffix = props.kind === "reports" ? "resolve" : "decide";
    const field = props.kind === "reports" ? "resolution" : "reason";
    const query = new URLSearchParams({ status: result.value, [field]: reason.value.trim() });
    await api(`/platform/content-governance/${props.kind}/${selected.value.id}/${suffix}?${query}`, { method: "POST" });
    message.value = "处理结果已保存"; dialog.value?.close(); await load();
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "处理失败"; }
  finally { saving.value = false; }
}
async function releaseHold(row: CaseRow) {
  try {
    await api(`/platform/content-governance/targets/${row.targetType}/${row.targetId}/release-hold`, { method: "POST" });
    message.value = "证据保留已解除";
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "解除保留失败"; }
}
onMounted(load);
watch(() => props.kind, load);
</script>
<template>
  <section class="page">
    <header class="page-header"><h1>{{ title }}</h1><button class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="18" />刷新</button></header>
    <div class="governance-filters" aria-label="内容治理筛选">
      <label class="field filter-search"><span>关键词</span><input v-model.trim="keyword" type="search" placeholder="目标、用户 ID、原因或说明"></label>
      <label class="field"><span>目标类型</span><select v-model="targetType"><option value="">全部目标</option><option v-for="(label, value) in targets" :key="value" :value="value">{{ label }}</option></select></label>
      <label class="field"><span>处理状态</span><select v-model="statusFilter" @change="load"><option value="">{{ pendingOnly ? "待处理" : "全部状态" }}</option><option value="PENDING">待处理</option><option value="RESOLVED">已处理</option><option value="REJECTED">已驳回</option><option value="UPHELD">维持处置</option><option value="REVERSED">已撤销</option></select></label>
      <label class="pending-filter"><input v-model="pendingOnly" type="checkbox" @change="load" />仅待处理</label>
      <button class="button secondary filter-reset" type="button" @click="keyword = ''; targetType = ''; statusFilter = ''; pendingOnly = true; load()">重置筛选</button>
    </div>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p><p v-if="message" class="saved-message" role="status">{{ message }}</p>
    <section class="data-panel governance-panel" :aria-busy="loading"><header class="data-panel-head"><div><h2>{{ title }}记录</h2><p>按目标、状态和关键词定位治理案件</p></div><span class="record-count">{{ filteredRows.length }} 条</span></header><div class="table-wrap"><table>
      <thead><tr><th>目标</th><th>提交用户</th><th>原因与说明</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead>
      <tbody><tr v-for="row in filteredRows" :key="row.id"><td data-label="目标"><strong>{{ targets[row.targetType] }} #{{ row.targetId }}</strong><span class="subline">用户 #{{ row.userId }}</span></td><td data-label="提交用户">#{{ row.userId }}</td><td data-label="原因与说明" class="case-body"><span class="case-reason">{{ reasons[row.reason || ''] || actions[row.action || ''] }}</span><span>{{ row.description || "未填写说明" }}</span><p v-if="row.decisionReason">{{ row.decisionReason }}</p></td><td data-label="状态"><span class="status" :data-tone="row.status === 'PENDING' ? 'warning' : row.status === 'REJECTED' || row.status === 'REVERSED' ? 'danger' : 'active'">{{ statuses[row.status] }}</span></td><td data-label="提交时间" class="numeric">{{ localDateTime(`${row.createdAt}Z`) }}</td><td data-label="操作"><div v-if="row.status === 'PENDING'" class="row-actions"><button class="button secondary" @click="decide(row, kind === 'reports' ? 'RESOLVED' : 'UPHELD')"><Check :size="16" />{{ kind === 'reports' ? '处理完成' : '维持处置' }}</button><button class="button secondary" @click="decide(row, kind === 'reports' ? 'REJECTED' : 'REVERSED')"><Undo2 :size="16" />{{ kind === 'reports' ? '驳回举报' : '撤销处置' }}</button></div><button v-else class="button secondary" @click="releaseHold(row)">解除证据保留</button></td></tr></tbody>
    </table></div><div v-if="!filteredRows.length" class="empty-state"><RefreshCw v-if="loading" class="spin" :size="28" aria-hidden="true" /><ShieldAlert v-else :size="30" aria-hidden="true" /><strong>{{ loading ? '正在加载治理记录' : rows.length ? '没有匹配的治理记录' : '暂无待处理内容' }}</strong><span>{{ rows.length ? '调整筛选条件后重新查看。' : '新的举报和申诉会出现在这里。' }}</span></div></section>
    <dialog ref="dialog" class="case-dialog" @close="selected = null"><form @submit.prevent="submit"><header><h2>{{ statuses[result] }}</h2><button class="icon-button" type="button" aria-label="关闭" @click="dialog?.close()"><X :size="18" /></button></header><label class="field"><span>处理原因</span><textarea v-model="reason" rows="4" required maxlength="255" /></label><p v-if="error" class="error-banner">{{ error }}</p><footer><button class="button secondary" type="button" @click="dialog?.close()">取消</button><button class="button primary" :disabled="saving || !reason.trim()">保存结果</button></footer></form></dialog>
  </section>
</template>
<style scoped>
.governance-filters { display: flex; flex-wrap: wrap; align-items: end; gap: var(--space-sm); padding: var(--space-md); border: 1px solid var(--color-rule); border-radius: var(--radius-card); background: var(--color-surface); }
.governance-filters .field { flex: 1 1 12rem; min-width: 10rem; }
.governance-filters .filter-search { flex: 2 1 20rem; }
.pending-filter { display: inline-flex; align-items: center; gap: var(--space-xs); min-height: var(--control-height); white-space: nowrap; }
.filter-reset { min-height: var(--control-height); }
.case-body { max-width: 28rem; white-space: normal; overflow-wrap: anywhere; }
.case-body { display: grid; gap: var(--space-2xs); }
.case-reason { color: var(--color-ink); font-weight: 700; }
.subline { display: block; color: var(--color-muted); font-size: var(--text-xs); }
.governance-panel .table-wrap { border-block-start: 0; }
.row-actions { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.case-dialog { width: min(30rem,calc(100vw - 2rem)); padding: var(--space-lg); color: var(--color-ink); background: var(--color-surface); border: 1px solid var(--color-rule); }
.case-dialog::backdrop { background: rgb(0 0 0 / 50%); }
.case-dialog form { display: grid; gap: var(--space-md); }
.case-dialog header,.case-dialog footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); }
.case-dialog textarea { width: 100%; }
</style>
