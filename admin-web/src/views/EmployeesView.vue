<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { KeyRound, Plus, X } from "@lucide/vue";
import { api, localDateTime } from "../api";
import PaginationBar from "../components/PaginationBar.vue";
import { usePagination } from "../pagination";
import type { Employee } from "../types";

const employees = ref<Employee[]>([]);
const name = ref("");
const error = ref("");
const createError = ref("");
const loading = ref(false);
const createDialog = ref<HTMLDialogElement>();
const editingEmployee = ref<Employee | null>(null);
const bindingCode = ref("");
const bindingExpiry = ref("");
const keyword = ref("");
const binding = ref("");
const status = ref("");
const filteredEmployees = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return employees.value.filter(employee =>
    (!query || employee.name.toLowerCase().includes(query))
    && (!binding.value || (binding.value === "BOUND") === Boolean(employee.userId))
    && (!status.value || employee.status === status.value));
});
const { page, pageSize, total, pagedItems } = usePagination(filteredEmployees);

async function load() {
  try {
    employees.value = await api<Employee[]>("/venue-admin/employees/list");
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "员工加载失败";
  }
}

async function save() {
  createError.value = "";
  loading.value = true;
  try {
    if (editingEmployee.value) {
      await api(`/venue-admin/employees/${editingEmployee.value.id}`, {
        method: "PUT", body: JSON.stringify({ name: name.value }),
      });
    } else {
      const result = await api<{ bindingCode: string; expiresAt: string }>("/venue-admin/employees/create", {
        method: "POST", body: JSON.stringify({ name: name.value }),
      });
      showCode(result);
    }
    name.value = "";
    createDialog.value?.close();
    await load();
  } catch (reason) {
    createError.value = reason instanceof Error ? reason.message : "员工保存失败";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingEmployee.value = null;
  name.value = "";
  createError.value = "";
  createDialog.value?.showModal();
}

function openEdit(employee: Employee) {
  editingEmployee.value = employee;
  name.value = employee.name;
  createError.value = "";
  createDialog.value?.showModal();
}

async function issue(employee: Employee) {
  error.value = "";
  try {
    const result = await api<{ bindingCode: string; expiresAt: string }>(`/venue-admin/employees/${employee.id}/binding-code`, { method: "POST" });
    showCode(result);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "绑定码生成失败";
  }
}

async function toggle(employee: Employee) {
  await api(`/venue-admin/employees/${employee.id}/status`, {
    method: "PATCH", body: JSON.stringify({ status: employee.status === "ACTIVE" ? "DISABLED" : "ACTIVE" }),
  });
  await load();
}

function showCode(result: { bindingCode: string; expiresAt: string }) {
  bindingCode.value = result.bindingCode;
  bindingExpiry.value = result.expiresAt;
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div><h1>员工</h1><p>员工在同一个小程序中绑定身份并完成核销。</p></div>
      <button class="button" type="button" @click="openCreate"><Plus :size="18" aria-hidden="true" />新增员工</button>
    </header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div v-if="bindingCode" class="binding-result" aria-live="polite">
      <span>绑定码</span><strong>{{ bindingCode }}</strong><span>{{ localDateTime(bindingExpiry) }} 前有效</span>
    </div>
    <div class="filter-bar" aria-label="员工筛选">
      <label class="field filter-search"><span>搜索</span><input v-model="keyword" type="search" placeholder="员工姓名"></label>
      <label class="field"><span>微信绑定</span><select v-model="binding"><option value="">全部</option><option value="BOUND">已绑定</option><option value="UNBOUND">未绑定</option></select></label>
      <label class="field"><span>状态</span><select v-model="status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
    </div>
    <section class="data-panel" aria-label="员工列表">
      <header class="data-panel-head"><div><h2>员工列表</h2><p>管理员工身份、绑定和可用状态</p></div><span class="record-count">{{ total }} 人</span></header>
      <div class="table-wrap">
        <table>
          <thead><tr><th>员工</th><th>微信绑定</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
          <tr v-for="employee in pagedItems" :key="employee.id">
            <td data-label="员工"><strong>{{ employee.name }}</strong></td>
            <td data-label="微信绑定">{{ employee.userId ? "已绑定" : "未绑定" }}</td>
            <td data-label="状态"><span class="status" :data-tone="employee.status === 'ACTIVE' ? 'active' : 'danger'">{{ employee.status === "ACTIVE" ? "启用" : "停用" }}</span></td>
            <td data-label="操作"><div class="toolbar"><button class="button secondary" type="button" @click="openEdit(employee)">编辑</button><button class="button secondary" type="button" @click="issue(employee)"><KeyRound :size="17" aria-hidden="true" />绑定码</button><button class="button secondary" type="button" @click="toggle(employee)">{{ employee.status === "ACTIVE" ? "停用" : "启用" }}</button></div></td>
          </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!filteredEmployees.length" class="empty-state"><strong>{{ employees.length ? "没有匹配的员工" : "还没有员工" }}</strong><span>{{ employees.length ? "调整姓名、绑定或状态后重试。" : "新增员工后可生成微信绑定码。" }}</span></div>
      <PaginationBar v-if="filteredEmployees.length" v-model="page" :total="total" :page-size="pageSize" />
    </section>

    <dialog ref="createDialog" class="modal">
      <form method="dialog" class="modal-head"><h2>{{ editingEmployee ? "编辑员工" : "新增员工" }}</h2><button class="icon-button" aria-label="关闭"><X :size="18" aria-hidden="true" /></button></form>
      <form class="modal-body" @submit.prevent="save">
        <p v-if="createError" class="error-banner" role="alert">{{ createError }}</p>
        <label class="field"><span>员工姓名</span><input v-model.trim="name" required></label>
        <div class="modal-actions"><button class="button secondary" type="button" @click="createDialog?.close()">取消</button><button class="button" type="submit" :disabled="loading">{{ loading ? "正在保存" : editingEmployee ? "保存修改" : "创建员工" }}</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.binding-result {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-sm);
  padding: var(--space-md);
  border-block: 1px solid var(--color-accent);
  color: var(--color-accent-strong);
}

.binding-result strong {
  font-family: var(--font-numeric);
  font-size: var(--text-xl);
  font-variant-numeric: tabular-nums;
}
</style>
