<script setup lang="ts">
import { validateSlot } from "../validation";
import { computed, onMounted, reactive, ref } from "vue";
import { Plus, X } from "@lucide/vue";
import { api, localDateTime, money, today } from "../api";
import { hasPermission } from "../auth";
import { statusLabel, statusTone } from "../labels";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { CourtView, SessionView, SportType } from "../types";

const selectedDate = ref(today());
const sessions = ref<SessionView[]>([]);
const courts = ref<CourtView[]>([]);
const sports = ref<SportType[]>([]);
const error = ref("");
const createError = ref("");
const loading = ref(false);
const createDialog = ref<HTMLDialogElement>();
const editingSession = ref<SessionView | null>(null);
const form = reactive({
  courtId: 0, sportTypeId: 0, date: selectedDate.value,
  startTime: "09:00", endTime: "10:00", priceYuan: 60,
});
const formCourts = computed(() => courts.value.filter(court => court.status === "ACTIVE" || court.id === editingSession.value?.courtId));
const formSports = computed(() => sports.value.filter(sport =>
  courts.value.find(court => court.id === form.courtId)?.sportIds.split(",").includes(String(sport.id))));
function selectCourtSport() {
  const retained = editingSession.value?.sportTypeId;
  const supported = courts.value.find(court => court.id === form.courtId)?.sportIds.split(",") || [];
  if (!formSports.value.some(sport => sport.id === form.sportTypeId)
      && !(form.sportTypeId === retained && supported.includes(String(retained)))) {
    form.sportTypeId = formSports.value[0]?.id || 0;
  }
}
const courtId = ref("");
const sportId = ref("");
const status = ref("");
const canEdit = hasPermission("SESSION_EDIT");
const filteredSessions = computed(() => sessions.value.filter(session =>
  (!courtId.value || session.courtId === Number(courtId.value))
  && (!sportId.value || session.sportTypeId === Number(sportId.value))
  && (!status.value || session.status === status.value)));
const { page, pageSize, total, pagedItems } = usePagination(filteredSessions);

async function load() {
  error.value = "";
  try {
    if (!selectedDate.value) throw new Error("请选择场次日期");
    [sessions.value, courts.value, sports.value] = await Promise.all([
      api<SessionView[]>(`/venue-admin/sessions/list?date=${selectedDate.value}`),
      api<CourtView[]>("/venue-admin/courts/list"),
      api<SportType[]>("/venue-admin/sports/list"),
    ]);
    if (!form.courtId) form.courtId = courts.value.find(court => court.status === "ACTIVE")?.id || 0;
    selectCourtSport();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场次加载失败";
  }
}

function openCreate() {
  editingSession.value = null;
  Object.assign(form, {
    courtId: courts.value.find(court => court.status === "ACTIVE")?.id || 0,
    sportTypeId: sports.value[0]?.id || 0,
    date: selectedDate.value,
    startTime: "09:00",
    endTime: "10:00",
    priceYuan: 60,
  });
  selectCourtSport();
  createError.value = "";
  createDialog.value?.showModal();
}

function openEdit(session: SessionView) {
  editingSession.value = session;
  Object.assign(form, {
    courtId: session.courtId,
    sportTypeId: session.sportTypeId,
    date: session.startAt.slice(0, 10),
    startTime: session.startAt.slice(11, 16),
    endTime: session.endAt.slice(11, 16),
    priceYuan: session.priceFen / 100,
  });
  createError.value = "";
  createDialog.value?.showModal();
}

async function save() {
  createError.value = "";
  loading.value = true;
  try {
    validateSlot(form, formCourts.value);
    await api(editingSession.value
      ? `/venue-admin/sessions/${editingSession.value.id}`
      : "/venue-admin/sessions/create", {
      method: editingSession.value ? "PUT" : "POST",
      body: JSON.stringify({
        courtId: form.courtId,
        sportTypeId: form.sportTypeId,
        date: form.date,
        startTime: form.startTime,
        endTime: form.endTime,
        priceFen: Math.round(form.priceYuan * 100),
      }),
    });
    selectedDate.value = form.date;
    createDialog.value?.close();
    await load();
  } catch (reason) {
    createError.value = reason instanceof Error ? reason.message : "场次保存失败";
  } finally {
    loading.value = false;
  }
}

async function closeSession(session: SessionView) {
  error.value = "";
  try {
    await api(`/venue-admin/sessions/${session.id}/close`, { method: "POST" });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场次关闭失败";
  }
}

async function openSession(session: SessionView) {
  error.value = "";
  try {
    await api(`/venue-admin/sessions/${session.id}/open`, { method: "POST" });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场次开启失败";
  }
}

function canOpen(session: SessionView) {
  return session.status === "CLOSED" && new Date(session.startAt).getTime() > Date.now();
}

async function closeDay() {
  error.value = "";
  try {
    if (!selectedDate.value) throw new Error("请选择要关闭的日期");
    await api("/venue-admin/sessions/close-day", {
      method: "POST", body: JSON.stringify({ date: selectedDate.value }),
    });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "当天关闭失败";
  }
}

onMounted(load);
</script>

<template>
  <section class="page sessions-page">
    <header class="page-header">
      <div><h1>具体场次</h1><p>按具体日期查看、临时加场或关闭可预约场次。</p></div>
      <div v-if="canEdit" class="toolbar">
        <button class="button danger" type="button" @click="closeDay">关闭当天</button>
        <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增场次</button>
      </div>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div class="filter-bar" aria-label="具体场次筛选">
      <label class="field"><span>日期</span><input v-model="selectedDate" type="date" @change="load"></label>
      <label class="field"><span>场地</span><select v-model="courtId"><option value="">全部场地</option><option v-for="court in courts" :key="court.id" :value="String(court.id)">{{ court.name }}</option></select></label>
      <label class="field"><span>运动</span><select v-model="sportId"><option value="">全部运动</option><option v-for="sport in sports" :key="sport.id" :value="String(sport.id)">{{ sport.name }}</option></select></label>
      <label class="field"><span>状态</span><select v-model="status"><option value="">全部状态</option><option value="OPEN">开放</option><option value="HELD">占位中</option><option value="BOOKED">已预约</option><option value="CLOSED">已关闭</option></select></label>
    </div>
    <section class="data-panel" aria-label="具体场次">
      <header class="data-panel-head"><div><h2>场次明细</h2><p>{{ selectedDate }} 的全部具体场次</p></div><span class="record-count">{{ total }} 条</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>时间</th><th>场地</th><th>运动</th><th class="numeric-column">价格</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="session in pagedItems" :key="session.id">
            <td data-label="时间">{{ localDateTime(session.startAt) }}</td>
            <td data-label="场地">{{ session.courtName }}</td>
            <td data-label="运动">{{ session.sportName }}</td>
            <td data-label="价格" class="numeric-column">{{ money(session.priceFen) }}</td>
            <td data-label="状态"><span class="status" :data-tone="statusTone(session.status)">{{ statusLabel(session.status) }}</span></td>
            <td data-label="操作"><div v-if="canEdit" class="toolbar">
              <button class="button secondary" type="button" @click="openEdit(session)">编辑</button>
              <button v-if="session.status === 'OPEN'" class="button secondary" type="button" @click="closeSession(session)">关闭</button>
              <button v-else-if="canOpen(session)" class="button secondary" type="button" @click="openSession(session)">开启</button>
            </div></td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredSessions.length" class="empty-state"><strong>{{ sessions.length ? "没有匹配的具体场次" : "当天没有场次" }}</strong><span>{{ sessions.length ? "调整场地、运动或状态后重试。" : "点击“新增场次”临时加场。" }}</span></div>
      <PaginationBar v-if="filteredSessions.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="createDialog" class="modal" aria-labelledby="sessionsview-createDialog-title">
      <form method="dialog" class="modal-head"><h2 id="sessionsview-createDialog-title">{{ editingSession ? "编辑场次" : "新增场次" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="save">
        <p v-if="createError" class="error-banner" role="alert">{{ createError }}</p>
        <label class="field"><span>日期</span><input v-model="form.date" type="date" required></label>
        <div class="form-grid">
          <label class="field"><span>场地</span><select v-model.number="form.courtId" required @change="selectCourtSport"><option :value="0" disabled>请选择场地</option><option v-for="court in formCourts" :key="court.id" :value="court.id">{{ court.name }}</option></select></label>
          <label class="field"><span>运动</span><select v-model.number="form.sportTypeId" required><option :value="0" disabled>{{ formSports.length ? "请选择运动" : "该场地暂无可用运动" }}</option><option v-if="editingSession && courts.find(court => court.id === form.courtId)?.sportIds.split(',').includes(String(editingSession.sportTypeId)) && !sports.some(sport => sport.id === editingSession?.sportTypeId)" :value="editingSession.sportTypeId">{{ editingSession.sportName }}（已停用）</option><option v-for="sport in formSports" :key="sport.id" :value="sport.id">{{ sport.name }}</option></select></label>
          <label class="field"><span>开始</span><input v-model="form.startTime" type="time" required></label>
          <label class="field"><span>结束</span><input v-model="form.endTime" type="time" required></label>
          <label class="field"><span>价格（元）</span><input v-model.number="form.priceYuan" type="number" min="0" max="21474836.47" step="0.01" required></label>
        </div>
        <div class="modal-actions"><button class="button secondary" type="button" @click="createDialog?.close()">取消</button><button class="button" type="submit" :disabled="loading">{{ loading ? "正在保存" : editingSession ? "保存修改" : "创建场次" }}</button></div>
      </form>
    </dialog>
  </section>
</template>
