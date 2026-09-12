<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Copy, Plus, X } from "@lucide/vue";
import { api, money } from "../api";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { CourtView, SportType, WeeklySlot } from "../types";

const dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
const activeDay = ref(1);
const slots = ref<WeeklySlot[]>([]);
const courts = ref<CourtView[]>([]);
const sports = ref<SportType[]>([]);
const error = ref("");
const createError = ref("");
const loading = ref(false);
const createDialog = ref<HTMLDialogElement>();
const editingSlot = ref<WeeklySlot | null>(null);
const form = reactive({ courtId: 0, sportTypeId: 0, startTime: "09:00", endTime: "10:00", priceYuan: 60 });
const courtId = ref("");
const sportId = ref("");
const daySlots = computed(() => slots.value.filter(slot => slot.dayOfWeek === activeDay.value && slot.status === "ACTIVE"));
const filteredDaySlots = computed(() => daySlots.value.filter(slot =>
  (!courtId.value || slot.courtId === Number(courtId.value))
  && (!sportId.value || slot.sportTypeId === Number(sportId.value))));
const { page, pageSize, total, pagedItems } = usePagination(filteredDaySlots);

async function load() {
  try {
    [slots.value, courts.value, sports.value] = await Promise.all([
      api<WeeklySlot[]>("/venue-admin/weekly-slots/list"),
      api<CourtView[]>("/venue-admin/courts/list"),
      api<SportType[]>("/venue-admin/sports/list"),
    ]);
    if (!form.courtId && courts.value[0]) form.courtId = courts.value[0].id;
    if (!form.sportTypeId && sports.value[0]) form.sportTypeId = sports.value[0].id;
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "排期加载失败";
  }
}

async function save() {
  createError.value = "";
  loading.value = true;
  try {
    await api(editingSlot.value
      ? `/venue-admin/weekly-slots/${editingSlot.value.id}/update`
      : "/venue-admin/weekly-slots/create", {
      method: editingSlot.value ? "PUT" : "POST",
      body: JSON.stringify({ ...form, dayOfWeek: activeDay.value, priceFen: Math.round(form.priceYuan * 100) }),
    });
    createDialog.value?.close();
    await load();
  } catch (reason) {
    createError.value = reason instanceof Error ? reason.message : "排期保存失败";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingSlot.value = null;
  Object.assign(form, {
    courtId: courts.value[0]?.id || 0,
    sportTypeId: sports.value[0]?.id || 0,
    startTime: "09:00",
    endTime: "10:00",
    priceYuan: 60,
  });
  createError.value = "";
  createDialog.value?.showModal();
}

function openEdit(slot: WeeklySlot) {
  editingSlot.value = slot;
  Object.assign(form, {
    courtId: slot.courtId,
    sportTypeId: slot.sportTypeId,
    startTime: slot.startTime.slice(0, 5),
    endTime: slot.endTime.slice(0, 5),
    priceYuan: slot.priceFen / 100,
  });
  createError.value = "";
  createDialog.value?.showModal();
}

async function remove(slot: WeeklySlot) {
  error.value = "";
  try {
    await api(`/venue-admin/weekly-slots/${slot.id}/disable`, { method: "DELETE" });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场次停用失败";
  }
}

async function copyToWeekdays() {
  const targetDays = [1, 2, 3, 4, 5, 6, 7].filter(day => day !== activeDay.value);
  error.value = "";
  try {
    await api("/venue-admin/weekly-slots/copy-day", {
      method: "POST", body: JSON.stringify({ sourceDay: activeDay.value, targetDays }),
    });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "排期复制失败";
  }
}

function nameOf(items: Array<{ id: number; name: string }>, id: number) {
  return items.find(item => item.id === id)?.name || "—";
}

function sportName(id: number) {
  const activeName = sports.value.find(sport => sport.id === id)?.name;
  if (activeName) return activeName;
  for (const court of courts.value) {
    const index = court.sportIds.split(",").indexOf(String(id));
    if (index >= 0) return `${court.sportNames.split(",")[index] || id}（已停用）`;
  }
  return "—";
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>排期</h1><p>按星期维护固定场次，已预约场次不会被后续修改覆盖。</p></div>
      <div class="toolbar">
        <button class="button secondary" type="button" @click="copyToWeekdays"><Copy :size="18" aria-hidden="true" />复制排期</button>
        <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增排期</button>
      </div>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div class="filter-bar schedule-filter-bar" aria-label="固定排期筛选">
      <div class="tabs" role="tablist" aria-label="星期">
        <button v-for="(day, index) in dayNames" :key="day" class="button" :class="{ secondary: activeDay !== index + 1 }" type="button" role="tab" :aria-selected="activeDay === index + 1" @click="activeDay = index + 1">{{ day }}</button>
      </div>
      <label class="field"><span>场地</span><select v-model="courtId"><option value="">全部场地</option><option v-for="court in courts" :key="court.id" :value="String(court.id)">{{ court.name }}</option></select></label>
      <label class="field"><span>运动</span><select v-model="sportId"><option value="">全部运动</option><option v-for="sport in sports" :key="sport.id" :value="String(sport.id)">{{ sport.name }}</option></select></label>
    </div>
    <section class="data-panel" aria-label="固定排期">
      <header class="data-panel-head"><div><h2>{{ dayNames[activeDay - 1] }}排期</h2><p>启用中的固定场次模板</p></div><span class="record-count">{{ total }} 条</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>时间</th><th>场地</th><th>运动</th><th>价格</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="slot in pagedItems" :key="slot.id">
            <td data-label="时间"><strong>{{ slot.startTime.slice(0, 5) }}–{{ slot.endTime.slice(0, 5) }}</strong></td>
            <td data-label="场地">{{ nameOf(courts, slot.courtId) }}</td>
            <td data-label="运动">{{ sportName(slot.sportTypeId) }}</td>
            <td data-label="价格">{{ money(slot.priceFen) }}</td>
            <td data-label="操作"><div class="toolbar"><button class="button secondary" type="button" @click="openEdit(slot)">编辑</button><button class="button secondary" type="button" @click="remove(slot)">停用</button></div></td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredDaySlots.length" class="empty-state"><strong>{{ daySlots.length ? "没有匹配的固定排期" : `${dayNames[activeDay - 1]}没有固定排期` }}</strong><span>{{ daySlots.length ? "调整场地或运动后重试。" : "点击“新增排期”进行创建。" }}</span></div>
      <PaginationBar v-if="filteredDaySlots.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="createDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>{{ editingSlot ? "编辑排期" : "新增排期" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="save">
        <p v-if="createError" class="error-banner" role="alert">{{ createError }}</p>
        <label class="field"><span>星期</span><input :value="dayNames[activeDay - 1]" disabled></label>
        <div class="form-grid">
          <label class="field"><span>场地</span><select v-model.number="form.courtId" required><option v-for="court in courts" :key="court.id" :value="court.id">{{ court.name }}</option></select></label>
          <label class="field"><span>运动</span><select v-model.number="form.sportTypeId" required><option v-if="editingSlot && !sports.some(sport => sport.id === editingSlot?.sportTypeId)" :value="editingSlot.sportTypeId">{{ sportName(editingSlot.sportTypeId) }}</option><option v-for="sport in sports" :key="sport.id" :value="sport.id">{{ sport.name }}</option></select></label>
          <label class="field"><span>开始</span><input v-model="form.startTime" type="time" required></label>
          <label class="field"><span>结束</span><input v-model="form.endTime" type="time" required></label>
          <label class="field"><span>价格（元）</span><input v-model.number="form.priceYuan" type="number" min="0" step="1" required></label>
        </div>
        <div class="modal-actions"><button class="button secondary" type="button" @click="createDialog?.close()">取消</button><button class="button" type="submit" :disabled="loading">{{ loading ? "正在保存" : editingSlot ? "保存修改" : "创建排期" }}</button></div>
      </form>
    </dialog>
  </section>
</template>
