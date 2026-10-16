<script setup lang="ts">
import { onMounted, ref } from "vue";
import { CircleCheck, CircleX, Pencil, Plus, RefreshCw, WalletCards, X } from "@lucide/vue";
import { api, money } from "../api";

interface RechargePlan {
  id: number;
  venueId: number;
  name: string;
  paidAmountFen: number;
  giftAmountFen: number;
  giftValidDays: number | null;
  status: "ACTIVE" | "DISABLED";
}

const plans = ref<RechargePlan[]>([]);
const error = ref("");
const formError = ref("");
const message = ref("");
const loading = ref(false);
const saving = ref(false);
const statusChangingId = ref<number | null>(null);
const editingPlan = ref<RechargePlan | null>(null);
const dialog = ref<HTMLDialogElement>();
const form = ref({ name: "", paidAmountFen: 0, giftAmountFen: 0, giftValidDays: undefined as number | undefined });

async function load() {
  loading.value = true;
  error.value = "";
  try {
    plans.value = await api<RechargePlan[]>("/venue-admin/recharge-plans");
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "充值档位加载失败";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingPlan.value = null;
  form.value = { name: "", paidAmountFen: 0, giftAmountFen: 0, giftValidDays: undefined };
  formError.value = "";
  dialog.value?.showModal();
}

function openEdit(plan: RechargePlan) {
  editingPlan.value = plan;
  form.value = {
    name: plan.name,
    paidAmountFen: plan.paidAmountFen,
    giftAmountFen: plan.giftAmountFen,
    giftValidDays: plan.giftValidDays ?? undefined,
  };
  formError.value = "";
  dialog.value?.showModal();
}

async function save() {
  saving.value = true;
  formError.value = "";
  message.value = "";
  const values = { ...form.value, giftValidDays: form.value.giftValidDays || undefined };
  try {
    if (editingPlan.value) {
      await api(`/venue-admin/recharge-plans/${editingPlan.value.id}`, {
        method: "PUT",
        body: JSON.stringify({ ...values, status: editingPlan.value.status }),
      });
      message.value = "充值档位已更新";
    } else {
      await api("/venue-admin/recharge-plans", { method: "POST", body: JSON.stringify(values) });
      message.value = "充值档位已创建";
    }
    dialog.value?.close();
    await load();
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "充值档位保存失败";
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(plan: RechargePlan) {
  statusChangingId.value = plan.id;
  error.value = "";
  message.value = "";
  try {
    const status = plan.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    await api(`/venue-admin/recharge-plans/${plan.id}`, {
      method: "PUT",
      body: JSON.stringify({
        name: plan.name,
        paidAmountFen: plan.paidAmountFen,
        giftAmountFen: plan.giftAmountFen,
        giftValidDays: plan.giftValidDays,
        status,
      }),
    });
    message.value = status === "ACTIVE" ? "充值档位已启用" : "充值档位已停用";
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "充值档位状态更新失败";
  } finally {
    statusChangingId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="page recharge-plans-page">
    <header class="page-header">
      <div><h1>充值档位</h1><p>档位修改只影响后续充值，历史充值保留原快照。</p></div>
      <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增档位</button>
    </header>

    <p v-if="error" class="error-banner" role="alert">{{ error }} <button class="inline-command" type="button" @click="load">重新加载</button></p>
    <p v-if="message" class="saved-message" aria-live="polite"><CircleCheck :size="17" aria-hidden="true" />{{ message }}</p>

    <section class="data-panel" aria-label="充值档位列表" :aria-busy="loading">
      <header class="data-panel-head">
        <div><h2>全部档位</h2><p>实付与赠送分别列示，历史充值保留原档位快照。</p></div>
        <span class="record-count">{{ plans.length }} 项</span>
      </header>
      <div v-if="plans.length" class="table-wrap">
        <table>
          <thead><tr><th>档位</th><th class="numeric-column">实付</th><th class="numeric-column">赠送</th><th>赠送有效期</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="plan in plans" :key="plan.id">
              <td data-label="档位"><strong>{{ plan.name }}</strong></td>
              <td data-label="实付" class="numeric-column numeric">{{ money(plan.paidAmountFen) }}</td>
              <td data-label="赠送" class="numeric-column numeric">{{ money(plan.giftAmountFen) }}</td>
              <td data-label="赠送有效期">{{ plan.giftValidDays ? `${plan.giftValidDays} 天` : "无赠送有效期" }}</td>
              <td data-label="状态">
                <span class="status status-with-icon" :data-tone="plan.status === 'ACTIVE' ? 'active' : 'danger'">
                  <CircleCheck v-if="plan.status === 'ACTIVE'" :size="15" aria-hidden="true" />
                  <CircleX v-else :size="15" aria-hidden="true" />
                  {{ plan.status === "ACTIVE" ? "启用" : "停用" }}
                </span>
              </td>
              <td data-label="操作">
                <div class="toolbar">
                  <button class="button secondary" type="button" @click="openEdit(plan)"><Pencil :size="16" aria-hidden="true" />编辑</button>
                  <button class="button secondary stable-action" type="button" :disabled="statusChangingId === plan.id" :aria-busy="statusChangingId === plan.id" @click="toggleStatus(plan)">
                    <RefreshCw :size="16" aria-hidden="true" />{{ statusChangingId === plan.id ? "处理中" : plan.status === "ACTIVE" ? "停用" : "启用" }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state" aria-live="polite"><RefreshCw class="spin" :size="26" aria-hidden="true" /><strong>正在加载充值档位</strong></div>
      <div v-else class="empty-state"><WalletCards :size="28" aria-hidden="true" /><strong>还没有充值档位</strong><span>新增后，会员可按档位完成充值。</span><button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增档位</button></div>
    </section>

    <dialog ref="dialog" class="modal" aria-labelledby="rechargeplansview-dialog-title">
      <form method="dialog" class="modal-head"><h2 id="rechargeplansview-dialog-title">{{ editingPlan ? "编辑充值档位" : "新增充值档位" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="save">
        <p v-if="formError" class="error-banner" role="alert">{{ formError }}</p>
        <label class="field"><span>档位名称</span><input v-model.trim="form.name" required :disabled="saving"></label>
        <div class="amount-fields">
          <label class="field"><span>实付金额（分）</span><input v-model.number="form.paidAmountFen" type="number" min="1" step="1" inputmode="numeric" required :disabled="saving"></label>
          <label class="field"><span>赠送金额（分）</span><input v-model.number="form.giftAmountFen" type="number" min="0" step="1" inputmode="numeric" required :disabled="saving"></label>
        </div>
        <label class="field"><span>赠送有效天数（无赠送可留空）</span><input v-model.number="form.giftValidDays" type="number" min="1" step="1" inputmode="numeric" :disabled="saving"></label>
        <div class="form-summary" aria-live="polite"><span>档位展示</span><strong>{{ money(form.paidAmountFen) }} + 赠送 {{ money(form.giftAmountFen) }}</strong></div>
        <div class="modal-actions"><button class="button secondary" type="button" :disabled="saving" @click="dialog?.close()">取消</button><button class="button stable-save" type="submit" :disabled="saving" :aria-busy="saving">{{ saving ? "正在保存" : editingPlan ? "保存修改" : "创建档位" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.amount-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md);
}

.form-summary {
  display: grid;
  gap: var(--space-xs);
  padding-block: var(--space-sm);
  border-block: 1px solid var(--color-rule-2);
}

.form-summary span {
  color: var(--color-muted);
  font-size: var(--text-xs);
}

.stable-action {
  width: 5.75rem;
}

.stable-save {
  min-width: 6.5rem;
}

@media (max-width: 30rem) {
  .amount-fields {
    grid-template-columns: 1fr;
  }
}
</style>
