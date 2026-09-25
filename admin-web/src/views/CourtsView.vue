<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ImageUp, Plus, X } from "@lucide/vue";
import { api } from "../api";
import { hasPermission } from "../auth";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { CourtView, SportType } from "../types";

const courts = ref<CourtView[]>([]);
const sports = ref<SportType[]>([]);
const name = ref("");
const selectedSports = ref<number[]>([]);
const customName = ref("");
const editingCourt = ref<CourtView | null>(null);
const editingSport = ref<SportType | null>(null);
const error = ref("");
const createError = ref("");
const loading = ref(false);
const courtDialog = ref<HTMLDialogElement>();
const sportDialog = ref<HTMLDialogElement>();
const keyword = ref("");
const sportId = ref("");
const status = ref("");
const canEdit = hasPermission("COURT_EDIT");
const filteredCourts = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return courts.value.filter(court =>
    (!query || court.name.toLowerCase().includes(query))
    && (!sportId.value || court.sportIds.split(",").includes(sportId.value))
    && (!status.value || court.status === status.value));
});
const customSports = computed(() => sports.value.filter(sport => sport.ownerVenueId !== null));
const retainedSports = computed(() => {
  if (!editingCourt.value) return [];
  const names = editingCourt.value.sportNames.split(",");
  return editingCourt.value.sportIds.split(",").filter(Boolean).map((id, index) => ({
    id: Number(id), name: names[index] || id,
  })).filter(item => !sports.value.some(sport => sport.id === item.id));
});
const { page, pageSize, total, pagedItems } = usePagination(filteredCourts);

async function load() {
  try {
    [courts.value, sports.value] = await Promise.all([
      api<CourtView[]>("/venue-admin/courts/list"),
      api<SportType[]>("/venue-admin/sports/list"),
    ]);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场地加载失败";
  }
}

async function saveCourt() {
  loading.value = true;
  createError.value = "";
  try {
    if (editingCourt.value) {
      await api(`/venue-admin/courts/${editingCourt.value.id}`, {
        method: "PUT",
        body: JSON.stringify({
          name: name.value, sportIds: selectedSports.value, status: editingCourt.value.status,
        }),
      });
    } else {
      await api("/venue-admin/courts/create", {
        method: "POST", body: JSON.stringify({ name: name.value, sportIds: selectedSports.value }),
      });
    }
    name.value = "";
    selectedSports.value = [];
    courtDialog.value?.close();
    await load();
  } catch (reason) {
    createError.value = reason instanceof Error ? reason.message : "场地保存失败";
  } finally {
    loading.value = false;
  }
}

async function saveCustomSport() {
  createError.value = "";
  try {
    if (editingSport.value) {
      await api(`/venue-admin/sports/${editingSport.value.id}`, {
        method: "PUT", body: JSON.stringify({ name: customName.value }),
      });
    } else {
      await api("/venue-admin/sports/create", {
        method: "POST", body: JSON.stringify({ name: customName.value }),
      });
    }
    customName.value = "";
    editingSport.value = null;
    await load();
  } catch (reason) {
    createError.value = reason instanceof Error ? reason.message : "场馆运动类型保存失败";
  }
}

function openCourtDialog() {
  editingCourt.value = null;
  name.value = "";
  selectedSports.value = [];
  createError.value = "";
  courtDialog.value?.showModal();
}

function openCourtEdit(court: CourtView) {
  editingCourt.value = court;
  name.value = court.name;
  selectedSports.value = court.sportIds.split(",").filter(Boolean).map(Number);
  createError.value = "";
  courtDialog.value?.showModal();
}

function openSportDialog() {
  editingSport.value = null;
  customName.value = "";
  createError.value = "";
  sportDialog.value?.showModal();
}

function openSportEdit(sport: SportType) {
  editingSport.value = sport;
  customName.value = sport.name;
  createError.value = "";
}

async function toggleCourt(court: CourtView) {
  const sportIds = court.sportIds.split(",").filter(Boolean).map(Number);
  await api(`/venue-admin/courts/${court.id}`, {
    method: "PUT",
    body: JSON.stringify({ name: court.name, sportIds, status: court.status === "ACTIVE" ? "DISABLED" : "ACTIVE" }),
  });
  await load();
}

async function uploadCourtImage(court: CourtView, event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  error.value = "";
  try {
    const body = new FormData();
    body.append("file", file);
    await api(`/venue-admin/courts/${court.id}/image`, { method: "POST", body });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场地图片上传失败";
  } finally {
    input.value = "";
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>场地</h1><p>一个场地可以支持多种运动，同一时间仍只接受一个预约。</p></div>
      <div v-if="canEdit" class="toolbar">
        <button class="button secondary" type="button" @click="openSportDialog">管理运动类型</button>
        <button class="button" type="button" @click="openCourtDialog"><Plus :size="18" aria-hidden="true" />新增场地</button>
      </div>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div class="filter-bar" aria-label="场地筛选">
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="场地名称"></label>
      <label class="field"><span>运动</span><select v-model="sportId"><option value="">全部运动</option><option v-for="sport in sports" :key="sport.id" :value="String(sport.id)">{{ sport.name }}</option></select></label>
      <label class="field"><span>状态</span><select v-model="status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
    </div>
    <section class="data-panel" aria-label="场地列表">
      <header class="data-panel-head"><div><h2>场地列表</h2><p>维护场地、运动范围和营业状态</p></div><span class="record-count">{{ total }} 条</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>场地</th><th>图片</th><th>运动</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="court in pagedItems" :key="court.id">
            <td data-label="场地"><strong>{{ court.name }}</strong></td>
            <td data-label="图片">
              <img v-if="court.imagePath" class="court-image" :src="court.imagePath" :alt="`${court.name}场地图片`">
              <span v-else class="muted">未上传</span>
            </td>
            <td data-label="运动">{{ court.sportNames }}</td>
            <td data-label="状态"><span class="status" :data-tone="court.status === 'ACTIVE' ? 'active' : 'danger'">{{ court.status === "ACTIVE" ? "启用" : "停用" }}</span></td>
            <td data-label="操作">
              <div v-if="canEdit" class="court-actions">
                <button class="button secondary" type="button" @click="openCourtEdit(court)">编辑</button>
                <label class="button secondary"><ImageUp :size="18" aria-hidden="true" /><span>上传图片</span><input class="file-input" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadCourtImage(court, $event)"></label>
                <button class="button secondary" type="button" @click="toggleCourt(court)">{{ court.status === "ACTIVE" ? "停用" : "启用" }}</button>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredCourts.length" class="empty-state"><strong>{{ courts.length ? "没有匹配的场地" : "还没有场地" }}</strong><span>{{ courts.length ? "调整搜索、运动或状态后重试。" : "新增场地后可继续配置排期。" }}</span></div>
      <PaginationBar v-if="filteredCourts.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="courtDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>{{ editingCourt ? "编辑场地" : "新增场地" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="saveCourt">
        <p v-if="createError" class="error-banner" role="alert">{{ createError }}</p>
        <label class="field"><span>场地名称</span><input v-model.trim="name" required></label>
        <fieldset class="check-list">
          <legend>支持的运动</legend>
          <label v-for="sport in sports" :key="sport.id"><input v-model="selectedSports" type="checkbox" :value="sport.id"><span>{{ sport.name }}</span></label>
          <label v-for="sport in retainedSports" :key="sport.id"><input v-model="selectedSports" type="checkbox" :value="sport.id"><span>{{ sport.name }}（已停用）</span></label>
        </fieldset>
        <div class="modal-actions"><button class="button secondary" type="button" @click="courtDialog?.close()">取消</button><button class="button" type="submit" :disabled="loading">{{ loading ? "正在保存" : editingCourt ? "保存修改" : "创建场地" }}</button></div>
      </form>
    </dialog>

    <dialog ref="sportDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>本场馆运动类型</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="saveCustomSport">
        <p class="muted">只在本场馆内使用，不进入首页全局筛选。</p>
        <p v-if="createError" class="error-banner" role="alert">{{ createError }}</p>
        <label class="field"><span>类型名称</span><input v-model.trim="customName" required></label>
        <div class="modal-actions"><button class="button secondary" type="button" @click="sportDialog?.close()">关闭</button><button v-if="editingSport" class="button secondary" type="button" @click="editingSport = null; customName = ''">新增类型</button><button class="button" type="submit">{{ editingSport ? "保存修改" : "创建类型" }}</button></div>
      </form>
      <div v-if="customSports.length" class="managed-list">
        <div v-for="sport in customSports" :key="sport.id"><strong>{{ sport.name }}</strong><button class="button secondary" type="button" @click="openSportEdit(sport)">编辑</button></div>
      </div>
    </dialog>
  </section>
</template>

<style scoped>
.check-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs) var(--space-md);
  padding: 0;
  border: 0;
}

.check-list legend {
  width: 100%;
  margin-block-end: var(--space-xs);
  color: var(--color-ink-2);
  font-size: var(--text-xs);
  font-weight: 600;
}

.check-list label {
  display: flex;
  align-items: center;
  min-height: 2.75rem;
  gap: var(--space-xs);
}

.check-list input {
  accent-color: var(--color-accent);
}

.court-image {
  display: block;
  width: 4rem;
  height: 3rem;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.court-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}

.court-actions .button {
  position: relative;
}

.managed-list {
  display: grid;
  padding: 0 var(--space-md) var(--space-md);
}

.managed-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding-block: var(--space-xs);
  border-block-start: 1px solid var(--color-rule-2);
}

.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
