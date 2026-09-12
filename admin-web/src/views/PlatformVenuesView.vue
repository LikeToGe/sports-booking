<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Plus, X } from "@lucide/vue";
import { api, percent } from "../api";
import { statusLabel, statusTone } from "../labels";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { PlatformVenue, Venue } from "../types";

interface VenueForm {
  name: string;
  city: string;
  address: string;
  latitude: number | "";
  longitude: number | "";
  phone: string;
  facilities: string;
  entryNotice: string;
  bookingDaysAhead: number;
  minBookingMinutes: number;
  cancelCutoffMinutes: number;
  serviceFeeRatePercent: number;
  adminUsername: string;
  adminPassword: string;
}

const emptyForm = (): VenueForm => ({
  name: "", city: "郑州市", address: "", latitude: "", longitude: "", phone: "",
  facilities: "", entryNotice: "", bookingDaysAhead: 7, minBookingMinutes: 30,
  cancelCutoffMinutes: 120, serviceFeeRatePercent: 0, adminUsername: "", adminPassword: "",
});

const venues = ref<PlatformVenue[]>([]);
const submitting = ref(false);
const resetting = ref(false);
const error = ref("");
const message = ref("");
const formError = ref("");
const resetError = ref("");
const venueDialog = ref<HTMLDialogElement>();
const detailDialog = ref<HTMLDialogElement>();
const resetDialog = ref<HTMLDialogElement>();
const editingVenue = ref<PlatformVenue | null>(null);
const detailItem = ref<PlatformVenue | null>(null);
const resetVenue = ref<Venue | null>(null);
const newPassword = ref("");
const keyword = ref("");
const status = ref("");
const form = reactive<VenueForm>(emptyForm());
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
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场馆加载失败";
  }
}

function venuePayload() {
  return {
    name: form.name,
    city: form.city,
    address: form.address,
    latitude: form.latitude === "" ? null : form.latitude,
    longitude: form.longitude === "" ? null : form.longitude,
    phone: form.phone,
    facilities: form.facilities,
    entryNotice: form.entryNotice,
    bookingDaysAhead: form.bookingDaysAhead,
    minBookingMinutes: form.minBookingMinutes,
    cancelCutoffMinutes: form.cancelCutoffMinutes,
  };
}

async function submitVenue() {
  submitting.value = true;
  formError.value = "";
  message.value = "";
  try {
    if (editingVenue.value) {
      const venueId = editingVenue.value.venue.id;
      // ponytail: 顺序调用避免两个场馆整行更新互相覆盖；要求原子保存时再合并后端接口。
      await api(`/platform/venues/${venueId}/update`, {
        method: "PUT", body: JSON.stringify(venuePayload()),
      });
      await api(`/platform/venues/${venueId}/service-fee-rate`, {
        method: "PATCH", body: JSON.stringify({ serviceFeeRatePercent: form.serviceFeeRatePercent }),
      });
      await api(`/platform/venues/${venueId}/admin`, {
        method: "PUT",
        body: JSON.stringify({ username: form.adminUsername, password: form.adminPassword || null }),
      });
      message.value = `${form.name}资料已更新`;
    } else {
      await api("/platform/venues/create", {
        method: "POST",
        body: JSON.stringify({
          venue: venuePayload(), adminUsername: form.adminUsername, adminPassword: form.adminPassword,
        }),
      });
      message.value = `${form.name}已创建`;
    }
    venueDialog.value?.close();
    await load();
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "场馆保存失败";
  } finally {
    submitting.value = false;
  }
}

function openCreate() {
  editingVenue.value = null;
  formError.value = "";
  Object.assign(form, emptyForm());
  venueDialog.value?.showModal();
}

function openEdit(item: PlatformVenue) {
  const venue = item.venue;
  editingVenue.value = item;
  formError.value = "";
  Object.assign(form, {
    name: venue.name,
    city: venue.city,
    address: venue.address,
    latitude: venue.latitude ?? "",
    longitude: venue.longitude ?? "",
    phone: venue.phone,
    facilities: venue.facilities,
    entryNotice: venue.entryNotice,
    bookingDaysAhead: venue.bookingDaysAhead,
    minBookingMinutes: venue.minBookingMinutes,
    cancelCutoffMinutes: venue.cancelCutoffMinutes,
    serviceFeeRatePercent: item.serviceFeeRateBasisPoints / 100,
    adminUsername: item.adminUsername,
    adminPassword: "",
  });
  venueDialog.value?.showModal();
}

function openDetail(item: PlatformVenue) {
  detailItem.value = item;
  detailDialog.value?.showModal();
}

function openReset(venue: Venue) {
  resetVenue.value = venue;
  newPassword.value = "";
  resetError.value = "";
  resetDialog.value?.showModal();
}

async function resetAdminPassword() {
  if (!resetVenue.value) return;
  resetting.value = true;
  resetError.value = "";
  message.value = "";
  try {
    await api(`/platform/venues/${resetVenue.value.id}/admin-password`, {
      method: "PATCH", body: JSON.stringify({ password: newPassword.value }),
    });
    message.value = `${resetVenue.value.name}管理员密码已重置`;
    resetDialog.value?.close();
  } catch (reason) {
    resetError.value = reason instanceof Error ? reason.message : "密码重置失败";
  } finally {
    resetting.value = false;
  }
}

async function setStatus(venue: Venue, nextStatus: Venue["status"]) {
  error.value = "";
  message.value = "";
  try {
    await api(`/platform/venues/${venue.id}/status`, {
      method: "PATCH", body: JSON.stringify({ status: nextStatus }),
    });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "状态更新失败";
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>场馆</h1><p>创建合作场馆、维护完整资料和管理员账号，并控制上架状态。</p></div>
      <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增场馆</button>
    </header>

    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="saved-message" aria-live="polite">{{ message }}</p>
    <div class="filter-bar" aria-label="场馆筛选">
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="场馆、地址或电话"></label>
      <label class="field"><span>状态</span><select v-model="status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="ACTIVE">启用</option><option value="SUSPENDED">暂停</option></select></label>
    </div>
    <section class="data-panel" aria-label="合作场馆">
      <header class="data-panel-head"><div><h2>合作场馆</h2><p>统一管理入驻资料、账号和上架状态</p></div><span class="record-count">{{ total }} 家</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>场馆</th><th>地址</th><th>预约规则</th><th>服务费率</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="item in pagedItems" :key="item.venue.id">
            <td data-label="场馆"><strong>{{ item.venue.name }}</strong><br><span class="muted">{{ item.venue.phone }}</span></td>
            <td data-label="地址">{{ item.venue.city }} · {{ item.venue.address }}</td>
            <td data-label="预约规则">未来 {{ item.venue.bookingDaysAhead }} 天 · 提前 {{ item.venue.cancelCutoffMinutes }} 分钟截止取消</td>
            <td data-label="服务费率"><strong class="rate-value">{{ percent(item.serviceFeeRateBasisPoints) }}</strong></td>
            <td data-label="状态"><span class="status" :data-tone="statusTone(item.venue.status)">{{ statusLabel(item.venue.status) }}</span></td>
            <td data-label="操作"><div class="row-actions">
              <button class="button secondary" type="button" @click="openDetail(item)">查看</button>
              <button class="button secondary" type="button" @click="openEdit(item)">编辑</button>
              <button class="button secondary" type="button" @click="openReset(item.venue)">重置密码</button>
              <button v-if="item.venue.status !== 'ACTIVE'" class="button secondary" type="button" @click="setStatus(item.venue, 'ACTIVE')">上架</button>
              <button v-else class="button secondary" type="button" @click="setStatus(item.venue, 'SUSPENDED')">暂停</button>
            </div></td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredVenues.length" class="empty-state"><strong>{{ venues.length ? "没有匹配的场馆" : "还没有合作场馆" }}</strong><span>{{ venues.length ? "调整搜索或状态后重试。" : "新增场馆后可创建对应管理员账号。" }}</span></div>
      <PaginationBar v-if="filteredVenues.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="venueDialog" class="modal wide">
      <form method="dialog" class="modal-head"><h2>{{ editingVenue ? "编辑场馆" : "新增场馆" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body venue-form" @submit.prevent="submitVenue">
        <p v-if="formError" class="error-banner" role="alert">{{ formError }}</p>
        <fieldset class="form-section"><legend>基本资料</legend><div class="form-grid">
          <label class="field"><span>场馆名称 *</span><input v-model.trim="form.name" required></label>
          <label class="field"><span>联系电话 *</span><input v-model.trim="form.phone" type="tel" required></label>
          <label class="field"><span>所在城市 *</span><input v-model.trim="form.city" required></label>
          <label class="field"><span>详细地址 *</span><input v-model.trim="form.address" required></label>
          <label class="field"><span>纬度</span><input v-model.number="form.latitude" type="number" step="any"></label>
          <label class="field"><span>经度</span><input v-model.number="form.longitude" type="number" step="any"></label>
          <label class="field span-2"><span>场馆设施</span><textarea v-model.trim="form.facilities" placeholder="如停车场、淋浴、更衣室、器材租赁"></textarea></label>
          <label class="field span-2"><span>入场须知</span><textarea v-model.trim="form.entryNotice" placeholder="填写入场方式、注意事项等"></textarea></label>
        </div></fieldset>
        <fieldset class="form-section"><legend>预约规则</legend><div class="form-grid">
          <label class="field"><span>可提前预约天数 *</span><input v-model.number="form.bookingDaysAhead" type="number" min="1" required></label>
          <label class="field"><span>最晚提前预约（分钟）*</span><input v-model.number="form.minBookingMinutes" type="number" min="0" required></label>
          <label class="field"><span>最晚提前取消（分钟）*</span><input v-model.number="form.cancelCutoffMinutes" type="number" min="0" required></label>
          <label v-if="editingVenue" class="field"><span>平台服务费率（%）*</span><input v-model.number="form.serviceFeeRatePercent" type="number" min="0" max="30" step="0.01" required></label>
        </div></fieldset>
        <fieldset class="form-section"><legend>管理员账号</legend><div class="form-grid">
          <label class="field"><span>管理员用户名 *</span><input v-model.trim="form.adminUsername" autocomplete="off" required></label>
          <label class="field"><span>{{ editingVenue ? "新密码（留空不修改）" : "初始密码 *" }}</span><input v-model="form.adminPassword" type="password" autocomplete="new-password" :required="!editingVenue"></label>
        </div></fieldset>
        <div class="modal-actions"><button class="button secondary" type="button" @click="venueDialog?.close()">取消</button><button class="button" type="submit" :disabled="submitting">{{ submitting ? "正在保存" : editingVenue ? "保存修改" : "创建场馆" }}</button></div>
      </form>
    </dialog>

    <dialog ref="detailDialog" class="modal wide">
      <form method="dialog" class="modal-head"><h2>{{ detailItem?.venue.name || "场馆详情" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <div v-if="detailItem" class="modal-body">
        <dl class="detail-grid">
          <div><dt>状态</dt><dd><span class="status" :data-tone="statusTone(detailItem.venue.status)">{{ statusLabel(detailItem.venue.status) }}</span></dd></div>
          <div><dt>联系电话</dt><dd>{{ detailItem.venue.phone }}</dd></div>
          <div class="span-2"><dt>地址</dt><dd>{{ detailItem.venue.city }} · {{ detailItem.venue.address }}</dd></div>
          <div><dt>经纬度</dt><dd>{{ detailItem.venue.latitude ?? "未填写" }} / {{ detailItem.venue.longitude ?? "未填写" }}</dd></div>
          <div><dt>平台服务费率</dt><dd>{{ percent(detailItem.serviceFeeRateBasisPoints) }}</dd></div>
          <div><dt>管理员用户名</dt><dd>{{ detailItem.adminUsername }}</dd></div>
          <div><dt>可提前预约</dt><dd>{{ detailItem.venue.bookingDaysAhead }} 天</dd></div>
          <div><dt>最晚提前预约</dt><dd>{{ detailItem.venue.minBookingMinutes }} 分钟</dd></div>
          <div><dt>最晚提前取消</dt><dd>{{ detailItem.venue.cancelCutoffMinutes }} 分钟</dd></div>
          <div><dt>封面</dt><dd>{{ detailItem.venue.coverPath ? "已上传" : "未上传" }}</dd></div>
          <div class="span-2"><dt>场馆设施</dt><dd>{{ detailItem.venue.facilities || "未填写" }}</dd></div>
          <div class="span-2"><dt>入场须知</dt><dd>{{ detailItem.venue.entryNotice || "未填写" }}</dd></div>
        </dl>
        <div class="modal-actions"><button class="button secondary" type="button" @click="detailDialog?.close()">关闭</button><button class="button" type="button" @click="detailDialog?.close(); openEdit(detailItem)">编辑资料</button></div>
      </div>
    </dialog>

    <dialog ref="resetDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>重置管理员密码</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="resetAdminPassword">
        <p>为 <strong>{{ resetVenue?.name }}</strong> 设置新的后台登录密码。</p>
        <p v-if="resetError" class="error-banner" role="alert">{{ resetError }}</p>
        <label class="field"><span>新密码 *</span><input v-model="newPassword" type="password" autocomplete="new-password" required></label>
        <div class="modal-actions"><button class="button secondary" type="button" @click="resetDialog?.close()">取消</button><button class="button" type="submit" :disabled="resetting">{{ resetting ? "正在重置" : "确认重置" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.row-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
}

.row-actions {
  flex-wrap: wrap;
}

.row-actions .button {
  min-height: 2.25rem;
  padding-inline: var(--space-sm);
  font-size: var(--text-xs);
}

.rate-value {
  font-variant-numeric: tabular-nums;
}

.venue-form,
.form-section {
  display: grid;
  gap: var(--space-md);
}

.form-section {
  margin: 0;
  padding: 0 0 var(--space-md);
  border: 0;
  border-block-end: 1px solid var(--color-rule-2);
}

.form-section legend {
  padding: 0;
  font-weight: 700;
}

.span-2 {
  grid-column: 1 / -1;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-block-start: 1px solid var(--color-rule-2);
}

.detail-grid > div {
  display: grid;
  gap: var(--space-2xs);
  min-width: 0;
  padding: var(--space-sm) 0;
  border-block-end: 1px solid var(--color-rule-2);
}

.detail-grid dt {
  color: var(--color-muted);
  font-size: var(--text-xs);
  font-weight: 600;
}

.detail-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (max-width: 39.999rem) {
  .detail-grid { grid-template-columns: 1fr; }
  .detail-grid .span-2 { grid-column: auto; }
}
</style>
