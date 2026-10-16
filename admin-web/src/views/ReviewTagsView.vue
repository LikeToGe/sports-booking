<script setup lang="ts">
import { requireText, requireSelection } from "../validation";
import { computed, onMounted, ref } from "vue";
import { Pencil, Plus, RefreshCw, X } from "@lucide/vue";
import { api } from "../api";
import type { SportType } from "../types";

interface ReviewTag {
  id: number;
  name: string;
  status: "ACTIVE" | "DISABLED";
  sportTypeIds: number[];
}

const tags = ref<ReviewTag[]>([]);
const sports = ref<SportType[]>([]);
const availableSports = computed(() => sports.value.filter(sport => sport.ownerVenueId == null && sport.status === "ACTIVE"));
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const formError = ref("");
const message = ref("");
const editing = ref<ReviewTag | null>(null);
const name = ref("");
const status = ref<ReviewTag["status"]>("ACTIVE");
const sportTypeIds = ref<number[]>([]);
const dialog = ref<HTMLDialogElement | null>(null);
const keyword = ref("");
const statusFilter = ref("");
const filteredTags = computed(() => tags.value.filter(tag =>
  (!keyword.value.trim() || tag.name.toLowerCase().includes(keyword.value.trim().toLowerCase()))
  && (!statusFilter.value || tag.status === statusFilter.value)));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    [tags.value, sports.value] = await Promise.all([
      api<ReviewTag[]>("/platform/review-tags"),
      api<SportType[]>("/platform/sports/list"),
    ]);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "评价标签加载失败";
  } finally {
    loading.value = false;
  }
}

function openForm(tag: ReviewTag | null) {
  editing.value = tag;
  name.value = tag?.name || "";
  status.value = tag?.status || "ACTIVE";
  sportTypeIds.value = [...(tag?.sportTypeIds || [])];
  formError.value = "";
  dialog.value?.showModal();
}

function sportNames(tag: ReviewTag) {
  return sports.value.filter(sport => tag.sportTypeIds.includes(sport.id)).map(sport => sport.name).join("、") || "无";
}

async function save() {
  if (saving.value) return;
  saving.value = true;
  formError.value = "";
  try {
    requireText(name.value, "评价标签名称", 40);
    requireSelection(sportTypeIds.value, "适用运动");
    await api(editing.value ? `/platform/review-tags/${editing.value.id}` : "/platform/review-tags", {
      method: editing.value ? "PUT" : "POST",
      body: JSON.stringify({ name: name.value.trim(), status: status.value, sportTypeIds: sportTypeIds.value }),
    });
    message.value = editing.value ? "评价标签已更新" : "评价标签已创建";
    dialog.value?.close();
    await load();
  } catch (cause) {
    formError.value = cause instanceof Error ? cause.message : "保存失败";
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="page review-tags-page">
    <header class="page-header"><div><h1>评价标签</h1><p>维护公开评价可选择的体验标签。</p></div><div class="toolbar"><button class="button secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="18" aria-hidden="true" />刷新</button><button class="button" type="button" :disabled="!availableSports.length" @click="openForm(null)"><Plus :size="18" aria-hidden="true" />新增标签</button></div></header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="saved-message" aria-live="polite">{{ message }}</p>
    <div class="filter-bar" aria-label="评价标签筛选"><label class="field filter-search"><span>关键词</span><input v-model.trim="keyword" type="search" placeholder="标签名称"></label><label class="field"><span>状态</span><select v-model="statusFilter"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><button class="button secondary" type="button" @click="keyword = ''; statusFilter = ''">重置筛选</button></div>
    <section class="data-panel" aria-label="评价标签" :aria-busy="loading">
      <header class="data-panel-head"><div><h2>固定标签</h2><p>按标签名称和启用状态快速定位</p></div><span class="record-count">{{ filteredTags.length }} 项</span></header>
      <div v-if="filteredTags.length" class="table-wrap"><table><thead><tr><th>名称</th><th>适用运动</th><th>状态</th><th>操作</th></tr></thead><tbody>
        <tr v-for="tag in filteredTags" :key="tag.id"><td data-label="名称"><strong>{{ tag.name }}</strong></td><td data-label="适用运动">{{ sportNames(tag) }}</td><td data-label="状态"><span class="status" :data-tone="tag.status === 'ACTIVE' ? 'active' : 'danger'">{{ tag.status === "ACTIVE" ? "启用" : "停用" }}</span></td><td data-label="操作"><button class="button secondary" type="button" @click="openForm(tag)"><Pencil :size="16" aria-hidden="true" />编辑</button></td></tr>
      </tbody></table></div>
      <div v-else class="empty-state"><strong>{{ loading ? "正在加载" : tags.length ? "没有匹配的评价标签" : "暂无评价标签" }}</strong><span>{{ tags.length ? "调整筛选条件后重新查看。" : "新增标签后可用于评价分析。" }}</span></div>
    </section>

    <dialog ref="dialog" class="modal" aria-labelledby="reviewtagsview-dialog-title">
      <form method="dialog" class="modal-head"><h2 id="reviewtagsview-dialog-title">{{ editing ? "编辑标签" : "新增标签" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="save">
        <p v-if="formError" class="error-banner" role="alert">{{ formError }}</p>
        <label class="field"><span>标签名称</span><input v-model.trim="name" maxlength="40" required></label>
        <fieldset class="sport-field"><legend>适用运动</legend><label v-for="sport in availableSports" :key="sport.id"><input v-model="sportTypeIds" type="checkbox" :value="sport.id"><span>{{ sport.name }}</span></label></fieldset>
        <label class="field"><span>状态</span><select v-model="status"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
        <div class="modal-actions"><button class="button secondary" type="button" @click="dialog?.close()">取消</button><button class="button" type="submit" :disabled="saving">{{ saving ? "保存中" : "保存" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.sport-field { display: flex; flex-wrap: wrap; gap: var(--space-sm); margin: 0; padding: var(--space-sm); border: 1px solid var(--color-rule); }
.sport-field legend { padding: 0 var(--space-xs); }
.sport-field label { display: inline-flex; align-items: center; gap: var(--space-xs); min-height: 2.5rem; }
</style>
