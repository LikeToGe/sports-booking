<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Plus, Save, X } from "@lucide/vue";
import { api } from "../api";
import { statusLabel, statusTone } from "../labels";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { PlatformVenue, Venue } from "../types";

const venues = ref<PlatformVenue[]>([]);
const loading = ref(false);
const savingRateId = ref<number | null>(null);
const error = ref("");
const createError = ref("");
const createDialog = ref<HTMLDialogElement>();
const keyword = ref("");
const status = ref("");
const rateDrafts = reactive<Record<number, number>>({});
const form = reactive({
  name: "", address: "", phone: "", adminUsername: "", adminPassword: "",
});
const filteredVenues = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return venues.value.filter(({ venue }) =>
    (!query || [venue.name, venue.address, venue.phone].some(value => value.toLowerCase().includes(query)))
    && (!status.value || venue.status === status.value));
});
const { page, pageSize, total, pagedItems } = usePagination(filteredVenues);

async function load() {
  try {
    venues.value = await api<PlatformVenue[]>("/platform/venues/list");
    for (const item of venues.value) {
      rateDrafts[item.venue.id] = item.serviceFeeRateBasisPoints / 100;
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场馆加载失败";
  }
}

async function create() {
  loading.value = true;
  createError.value = "";
  try {
    await api("/platform/venues/create", {
      method: "POST",
      body: JSON.stringify({
        venue: {
          name: form.name,
          city: "郑州市",
          address: form.address,
          phone: form.phone,
          facilities: "",
          entryNotice: "",
          bookingDaysAhead: 7,
          minBookingMinutes: 30,
          cancelCutoffMinutes: 120,
        },
        adminUsername: form.adminUsername,
        adminPassword: form.adminPassword,
      }),
    });
    Object.assign(form, { name: "", address: "", phone: "", adminUsername: "", adminPassword: "" });
    createDialog.value?.close();
    await load();
  } catch (reason) {
    createError.value = reason instanceof Error ? reason.message : "场馆创建失败";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  createError.value = "";
  createDialog.value?.showModal();
}

async function setStatus(venue: Venue, status: Venue["status"]) {
  error.value = "";
  try {
    await api(`/platform/venues/${venue.id}/status`, { method: "PATCH", body: JSON.stringify({ status }) });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "状态更新失败";
  }
}

async function setServiceFeeRate(item: PlatformVenue) {
  const venueId = item.venue.id;
  savingRateId.value = venueId;
  error.value = "";
  try {
    await api(`/platform/venues/${venueId}/service-fee-rate`, {
      method: "PATCH",
      body: JSON.stringify({ serviceFeeRatePercent: rateDrafts[venueId] }),
    });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "服务费率更新失败";
  } finally {
    savingRateId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>场馆</h1>
        <p>创建合作场馆、管理员账号并控制上架状态。</p>
      </div>
      <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增场馆</button>
    </header>

    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div class="filter-bar" aria-label="场馆筛选">
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="场馆、地址或电话"></label>
      <label class="field"><span>状态</span><select v-model="status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="ACTIVE">启用</option><option value="SUSPENDED">暂停</option></select></label>
    </div>
    <section class="data-panel" aria-label="合作场馆">
      <header class="data-panel-head"><div><h2>合作场馆</h2><p>统一管理入驻资料和上架状态</p></div><span class="record-count">{{ total }} 家</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>场馆</th><th>地址</th><th>预约规则</th><th>服务费率</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="item in pagedItems" :key="item.venue.id">
            <td data-label="场馆"><strong>{{ item.venue.name }}</strong><br><span class="muted">{{ item.venue.phone }}</span></td>
            <td data-label="地址">{{ item.venue.address }}</td>
            <td data-label="预约规则">未来 {{ item.venue.bookingDaysAhead }} 天 · 提前 {{ item.venue.cancelCutoffMinutes }} 分钟截止取消</td>
            <td data-label="服务费率">
              <form class="rate-editor" @submit.prevent="setServiceFeeRate(item)">
                <input :id="`rate-${item.venue.id}`" v-model.number="rateDrafts[item.venue.id]" type="number" min="0" max="30" step="0.01" required :aria-label="`${item.venue.name}服务费率`">
                <span aria-hidden="true">%</span>
                <button class="icon-button" type="submit" :aria-label="`保存${item.venue.name}服务费率`" title="保存服务费率" :disabled="savingRateId === item.venue.id">
                  <Save :size="16" aria-hidden="true" />
                </button>
              </form>
            </td>
            <td data-label="状态"><span class="status" :data-tone="statusTone(item.venue.status)">{{ statusLabel(item.venue.status) }}</span></td>
            <td data-label="操作">
              <button v-if="item.venue.status !== 'ACTIVE'" class="button secondary" type="button" @click="setStatus(item.venue, 'ACTIVE')">上架</button>
              <button v-else class="button secondary" type="button" @click="setStatus(item.venue, 'SUSPENDED')">暂停</button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredVenues.length" class="empty-state"><strong>{{ venues.length ? "没有匹配的场馆" : "还没有合作场馆" }}</strong><span>{{ venues.length ? "调整搜索或状态后重试。" : "新增场馆后可创建对应管理员账号。" }}</span></div>
      <PaginationBar v-if="filteredVenues.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="createDialog" class="modal wide">
      <form method="dialog" class="modal-head"><h2>新增场馆</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="create">
        <p v-if="createError" class="error-banner" role="alert">{{ createError }}</p>
        <div class="form-grid">
          <label class="field"><span>场馆名称</span><input v-model.trim="form.name" required></label>
          <label class="field"><span>联系电话</span><input v-model.trim="form.phone" required></label>
          <label class="field"><span>地址</span><input v-model.trim="form.address" required></label>
          <label class="field"><span>管理员用户名</span><input v-model.trim="form.adminUsername" required></label>
          <label class="field"><span>初始密码</span><input v-model="form.adminPassword" type="password" required></label>
        </div>
        <div class="modal-actions"><button class="button secondary" type="button" @click="createDialog?.close()">取消</button><button class="button" type="submit" :disabled="loading">{{ loading ? "正在创建" : "创建场馆" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.rate-editor {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
}

.rate-editor input {
  width: 4.25rem;
  min-height: 2.75rem;
  padding-inline: var(--space-xs);
  border: 1px solid var(--color-rule-2);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  font: inherit;
  font-variant-numeric: tabular-nums;
}

.rate-editor .icon-button {
  flex: 0 0 2.75rem;
  width: 2.75rem;
  min-width: 2.75rem;
}

.rate-editor input:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}
</style>
