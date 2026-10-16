<script setup lang="ts">
import { requireText, requireNumber } from "../validation";
import { reactive, ref } from "vue";
import { ShieldBan } from "@lucide/vue";
import { api } from "../api";
const form = reactive({ userId: 0, capability: "POST", days: 1, reason: "" });
const saving = ref(false); const error = ref(""); const message = ref("");
async function submit() {
  saving.value = true; error.value = "";
  try {
    requireNumber(form.userId, "用户 ID", 1, Number.MAX_SAFE_INTEGER);
    requireText(form.reason, "限制原因", 255);
    const query = new URLSearchParams({ userId: String(form.userId), capability: form.capability, days: String(form.days), reason: form.reason.trim() });
    await api(`/platform/content-governance/restrictions?${query}`, { method: "POST" });
    message.value = "内容权限限制已保存";
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "保存失败"; }
  finally { saving.value = false; }
}
</script>
<template><section class="page content-restrictions-page"><header class="page-header"><div><h1>账号内容权限</h1><p>按用户与能力设置限制，保留处置原因。</p></div></header><p v-if="error" class="error-banner" role="alert">{{ error }}</p><p v-if="message" class="saved-message" role="status">{{ message }}</p><form class="form-panel restriction-form" @submit.prevent="submit"><label class="field"><span>用户 ID</span><input v-model.number="form.userId" type="number" min="1" required /></label><label class="field"><span>限制能力</span><select v-model="form.capability"><option value="POST">发布动态</option><option value="COMMENT">评论与回复</option><option value="RECRUITMENT">申请找搭子</option></select></label><label class="field"><span>限制时长</span><select v-model.number="form.days"><option :value="1">1 天</option><option :value="7">7 天</option><option :value="30">30 天</option><option :value="0">永久</option></select></label><label class="field"><span>原因</span><textarea v-model="form.reason" rows="4" required maxlength="255" /></label><button class="button primary" :disabled="saving"><ShieldBan :size="18" />保存限制</button></form></section></template>
<style scoped>.restriction-form { max-width: 640px; display: grid; gap: var(--space-md); }.restriction-form .button { justify-self: start; }</style>
