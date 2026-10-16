<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Pencil, Plus, Trash2, X } from "@lucide/vue";
import { api } from "../api";
import type { PermissionCatalog, VenueRole } from "../types";

const permissions = ref<PermissionCatalog[]>([]);
const roles = ref<VenueRole[]>([]);
const dialog = ref<HTMLDialogElement>();
const editing = ref<VenueRole>();
const name = ref("");
const status = ref<"ACTIVE" | "DISABLED">("ACTIVE");
const selected = ref<string[]>([]);
const error = ref("");
const formError = ref("");
const loading = ref(false);
const groups = computed(() => permissions.value.reduce<Record<string, PermissionCatalog[]>>((result, permission) => {
  (result[permission.group] ||= []).push(permission);
  return result;
}, {}));

async function load() {
  error.value = "";
  try {
    [permissions.value, roles.value] = await Promise.all([
      api<PermissionCatalog[]>("/venue-admin/permissions"),
      api<VenueRole[]>("/venue-admin/roles"),
    ]);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "角色权限加载失败";
  }
}

function openCreate() {
  editing.value = undefined;
  name.value = "";
  status.value = "ACTIVE";
  selected.value = [];
  formError.value = "";
  dialog.value?.showModal();
}

function openEdit(role: VenueRole) {
  editing.value = role;
  name.value = role.name;
  status.value = role.status;
  selected.value = [...role.permissions];
  formError.value = "";
  dialog.value?.showModal();
}

async function save() {
  loading.value = true;
  formError.value = "";
  try {
    const path = editing.value ? `/venue-admin/roles/${editing.value.id}` : "/venue-admin/roles";
    await api(path, {
      method: editing.value ? "PUT" : "POST",
      body: JSON.stringify({ name: name.value, status: status.value, permissions: selected.value }),
    });
    dialog.value?.close();
    await load();
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "角色保存失败";
  } finally {
    loading.value = false;
  }
}

async function remove(role: VenueRole) {
  if (!window.confirm(`确定删除角色“${role.name}”吗？`)) return;
  try {
    await api(`/venue-admin/roles/${role.id}`, { method: "DELETE" });
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "角色删除失败";
  }
}

onMounted(load);
</script>

<template>
  <section class="page roles-page">
    <header class="page-header">
      <div><h1>角色权限</h1><p>角色可组合固定权限，员工获得所分配角色的权限并集。</p></div>
      <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增角色</button>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <section class="data-panel" aria-label="角色列表">
      <header class="data-panel-head"><div><h2>场馆角色</h2><p>权限调整在员工下一次请求时生效</p></div><span class="record-count">{{ roles.length }} 个</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>角色</th><th>状态</th><th>权限</th><th>员工</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="role in roles" :key="role.id">
            <td data-label="角色"><strong>{{ role.name }}</strong></td>
            <td data-label="状态"><span class="status" :data-tone="role.status === 'ACTIVE' ? 'active' : 'danger'">{{ role.status === "ACTIVE" ? "启用" : "停用" }}</span></td>
            <td data-label="权限">{{ role.permissions.length }} 项</td>
            <td data-label="员工">{{ role.employeeCount }} 人</td>
            <td data-label="操作"><div class="toolbar"><button class="icon-button" type="button" aria-label="编辑角色" title="编辑角色" @click="openEdit(role)"><Pencil :size="18" aria-hidden="true" /></button><button class="icon-button" type="button" aria-label="删除角色" title="删除角色" :disabled="role.employeeCount > 0" @click="remove(role)"><Trash2 :size="18" aria-hidden="true" /></button></div></td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!roles.length" class="empty-state"><strong>还没有角色</strong><span>新增角色后即可分配给员工。</span></div>
    </section>

    <dialog ref="dialog" class="modal wide" aria-labelledby="rolesview-dialog-title">
      <form method="dialog" class="modal-head"><h2 id="rolesview-dialog-title">{{ editing ? "编辑角色" : "新增角色" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="save">
        <p v-if="formError" class="error-banner" role="alert">{{ formError }}</p>
        <div class="form-grid">
          <label class="field"><span>角色名称</span><input v-model.trim="name" required maxlength="40"></label>
          <label class="field"><span>状态</span><select v-model="status"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
        </div>
        <fieldset v-for="(items, group) in groups" :key="group" class="permission-group">
          <legend>{{ group }}</legend>
          <label v-for="permission in items" :key="permission.code"><input v-model="selected" type="checkbox" :value="permission.code"><span>{{ permission.name }}</span></label>
        </fieldset>
        <div class="modal-actions"><button class="button secondary" type="button" @click="dialog?.close()">取消</button><button class="button" type="submit" :disabled="loading">{{ loading ? "正在保存" : "保存角色" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.permission-group {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr));
  gap: var(--space-sm);
  margin: 0;
  padding: var(--space-md);
  border: 1px solid var(--color-rule); border-radius: var(--radius-control);
}

.permission-group legend { padding-inline: var(--space-xs); font-weight: 600; }
.permission-group label { display: flex; align-items: center; min-height: 2.75rem; gap: var(--space-sm); cursor: pointer; }
.permission-group input { width: 1.125rem; height: 1.125rem; accent-color: var(--color-accent); }
</style>
