<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ImageUp, Save } from "@lucide/vue";
import { api } from "../api";
import type { Venue } from "../types";

const form = reactive<Partial<Venue>>({});
const error = ref("");
const saved = ref(false);
const file = ref<File>();

async function load() {
  try {
    Object.assign(form, await api<Venue>("/venue-admin/profile/detail"));
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场馆资料加载失败";
  }
}

async function save() {
  error.value = "";
  saved.value = false;
  try {
    await api("/venue-admin/profile/update", {
      method: "PUT",
      body: JSON.stringify({
        name: form.name,
        city: form.city,
        address: form.address,
        latitude: form.latitude,
        longitude: form.longitude,
        phone: form.phone,
        facilities: form.facilities,
        entryNotice: form.entryNotice,
        bookingDaysAhead: form.bookingDaysAhead,
        minBookingMinutes: form.minBookingMinutes,
        cancelCutoffMinutes: form.cancelCutoffMinutes,
      }),
    });
    if (file.value) {
      const body = new FormData();
      body.append("file", file.value);
      await api("/venue-admin/profile/cover", { method: "POST", body });
      file.value = undefined;
    }
    saved.value = true;
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "场馆资料保存失败";
  }
}

function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header"><div><h1>场馆资料</h1><p>公开资料和预约规则保存后立即生效。</p></div></header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="saved" class="saved-message" role="status">资料已保存</p>
    <form class="form-panel profile-form" @submit.prevent="save">
      <div class="form-grid">
        <label class="field"><span>场馆名称</span><input v-model.trim="form.name" required></label>
        <label class="field"><span>城市</span><input v-model.trim="form.city" required></label>
        <label class="field"><span>联系电话</span><input v-model.trim="form.phone" required></label>
        <label class="field"><span>地址</span><input v-model.trim="form.address" required></label>
        <label class="field"><span>纬度</span><input v-model.number="form.latitude" type="number" step="0.0000001"></label>
        <label class="field"><span>经度</span><input v-model.number="form.longitude" type="number" step="0.0000001"></label>
        <label class="field"><span>开放未来天数</span><input v-model.number="form.bookingDaysAhead" type="number" min="1" required></label>
        <label class="field"><span>最晚预约（分钟）</span><input v-model.number="form.minBookingMinutes" type="number" min="0" required></label>
        <label class="field"><span>取消截止（分钟）</span><input v-model.number="form.cancelCutoffMinutes" type="number" min="0" required></label>
      </div>
      <label class="field"><span>设施</span><textarea v-model.trim="form.facilities"></textarea></label>
      <label class="field"><span>入场须知</span><textarea v-model.trim="form.entryNotice"></textarea></label>
      <label class="file-control">
        <ImageUp :size="20" aria-hidden="true" />
        <span>{{ file?.name || "选择场馆封面" }}</span>
        <input type="file" accept="image/jpeg,image/png,image/webp" @change="selectFile">
      </label>
      <button class="button" type="submit"><Save :size="18" aria-hidden="true" />保存资料</button>
    </form>
  </section>
</template>

<style scoped>
.profile-form {
  max-width: 64rem;
}

.file-control {
  display: flex;
  align-items: center;
  min-height: var(--control-height);
  gap: var(--space-xs);
  width: fit-content;
  padding-inline: var(--space-md);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.file-control input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.saved-message {
  color: var(--color-accent-strong);
}
</style>
