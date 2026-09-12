<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Plus, X } from "@lucide/vue";
import { api } from "../api";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { SportType } from "../types";

const sports = ref<SportType[]>([]);
const name = ref("");
const error = ref("");
const createError = ref("");
const message = ref("");
const loading = ref(false);
const statusChangingId = ref<number | null>(null);
const createDialog = ref<HTMLDialogElement>();
const editingSport = ref<SportType | null>(null);
const keyword = ref("");
const filteredSports = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return sports.value.filter(sport => !query || sport.name.toLowerCase().includes(query));
});
const { page, pageSize, total, pagedItems } = usePagination(filteredSports);

async function load() {
  try {
    sports.value = await api<SportType[]>("/platform/sports/list");
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "运动类型加载失败";
  }
}

async function save() {
  loading.value = true;
  createError.value = "";
  message.value = "";
  try {
    if (editingSport.value) {
      await api(`/platform/sports/${editingSport.value.id}`, {
        method: "PUT", body: JSON.stringify({ name: name.value }),
      });
      message.value = "运动类型已更新";
    } else {
      await api("/platform/sports/create", { method: "POST", body: JSON.stringify({ name: name.value }) });
      message.value = "运动类型已创建";
    }
    name.value = "";
    createDialog.value?.close();
    await load();
  } catch (reason) {
    createError.value = reason instanceof Error ? reason.message : "运动类型保存失败";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingSport.value = null;
  name.value = "";
  createError.value = "";
  createDialog.value?.showModal();
}

function openEdit(sport: SportType) {
  editingSport.value = sport;
  name.value = sport.name;
  createError.value = "";
  createDialog.value?.showModal();
}

async function toggleStatus(sport: SportType) {
  statusChangingId.value = sport.id;
  error.value = "";
  try {
    await api(`/platform/sports/${sport.id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status: sport.status === "ACTIVE" ? "DISABLED" : "ACTIVE" }),
    });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "状态更新失败";
  } finally {
    statusChangingId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h1>运动类型</h1>
        <p>首页筛选只使用平台运动类型。</p>
      </div>
      <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增类型</button>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="saved-message" aria-live="polite">{{ message }}</p>
    <div class="filter-bar" aria-label="运动类型筛选">
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="运动类型名称"></label>
    </div>
    <section class="data-panel" aria-label="平台运动类型">
      <header class="data-panel-head"><div><h2>平台运动类型</h2><p>用于小程序首页筛选和场馆资源配置</p></div><span class="record-count">{{ total }} 项</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>名称</th><th>范围</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="sport in pagedItems" :key="sport.id">
            <td data-label="名称"><strong>{{ sport.name }}</strong></td>
            <td data-label="范围">全平台</td>
            <td data-label="状态"><span class="status" :data-tone="sport.status === 'ACTIVE' ? 'active' : 'danger'">{{ sport.status === "ACTIVE" ? "启用" : "停用" }}</span></td>
            <td data-label="操作"><div class="toolbar"><button class="button secondary" type="button" @click="openEdit(sport)">编辑</button><button class="button secondary sport-status-action" type="button" :disabled="statusChangingId === sport.id" :aria-busy="statusChangingId === sport.id" @click="toggleStatus(sport)">{{ sport.status === "ACTIVE" ? "停用" : "启用" }}</button></div></td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredSports.length" class="empty-state"><strong>{{ sports.length ? "没有匹配的运动类型" : "还没有运动类型" }}</strong><span>{{ sports.length ? "调整名称后重试。" : "新增后会进入小程序首页筛选。" }}</span></div>
      <PaginationBar v-if="filteredSports.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="createDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>{{ editingSport ? "编辑运动类型" : "新增运动类型" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="save">
        <p v-if="createError" class="error-banner" role="alert">{{ createError }}</p>
        <label class="field"><span>类型名称</span><input v-model.trim="name" required></label>
        <div class="modal-actions"><button class="button secondary" type="button" @click="createDialog?.close()">取消</button><button class="button" type="submit" :disabled="loading">{{ loading ? "正在保存" : editingSport ? "保存修改" : "创建类型" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.sport-status-action {
  width: 5rem;
  transform: none !important;
  transition: none !important;
}

.sport-status-action:hover,
.sport-status-action:active {
  transform: none !important;
}
</style>
