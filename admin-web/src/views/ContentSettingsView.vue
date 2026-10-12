<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { RefreshCw, Save, ShieldCheck } from "@lucide/vue";
import { api } from "../api";

const setting = reactive({ postEnabled: true, commentEnabled: true, reviewEnabled: true });
const agreement = reactive({ rulesVersion: "", privacyVersion: "", rulesText: "", privacyText: "", forceReaccept: true });
const loading = ref(false);
const saving = ref(false);
const savingAgreement = ref(false);
const error = ref("");
const message = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [moderation, currentAgreement] = await Promise.all([
      api<typeof setting>("/platform/content-settings/get"),
      api<typeof agreement>("/platform/content-agreement"),
    ]);
    Object.assign(setting, moderation);
    Object.assign(agreement, currentAgreement);
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : "审核配置加载失败"; }
  finally { loading.value = false; }
}

async function saveAgreement() {
  if (savingAgreement.value) return;
  savingAgreement.value = true;
  error.value = "";
  try {
    Object.assign(agreement, await api<typeof agreement>("/platform/content-agreement", {
      method: "PUT", body: JSON.stringify(agreement),
    }));
    message.value = "协议配置已保存";
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "协议配置保存失败"; }
  finally { savingAgreement.value = false; }
}

async function save() {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  try { Object.assign(setting, await api<typeof setting>("/platform/content-settings/update", { method: "PUT", body: JSON.stringify(setting) })); message.value = "审核配置已保存"; }
  catch (cause) { error.value = cause instanceof Error ? cause.message : "审核配置保存失败"; }
  finally { saving.value = false; }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-header"><div><h1>内容审核配置</h1></div><button class="button secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="18" aria-hidden="true" />刷新</button></header>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="saved-message" role="status"><ShieldCheck :size="17" aria-hidden="true" />{{ message }}</p>
    <section class="data-panel settings-panel" :aria-busy="loading">
      <label><input v-model="setting.postEnabled" type="checkbox" /> <span>动态审核</span><small>关闭只影响之后提交的新动态，已公开动态不变。</small></label>
      <label><input v-model="setting.commentEnabled" type="checkbox" /> <span>评论审核</span><small>评论业务接入后生效。</small></label>
      <label><input v-model="setting.reviewEnabled" type="checkbox" /> <span>评价与官方回复审核</span><small>关闭只影响之后提交的新版本，旧公开版本继续展示。</small></label>
      <button class="button primary" type="button" :disabled="saving || loading" @click="save"><Save :size="17" aria-hidden="true" />{{ saving ? "保存中" : "保存配置" }}</button>
    </section>
    <section class="data-panel settings-panel agreement-panel" :aria-busy="loading">
      <header class="data-panel-head"><h2>社区协议</h2></header>
      <label class="text-field"><span>社区规则版本</span><input v-model="agreement.rulesVersion" maxlength="40" required /></label>
      <label class="text-field"><span>社区规则正文</span><textarea v-model="agreement.rulesText" maxlength="3000" rows="5" required /></label>
      <label class="text-field"><span>隐私说明版本</span><input v-model="agreement.privacyVersion" maxlength="40" required /></label>
      <label class="text-field"><span>隐私说明正文</span><textarea v-model="agreement.privacyText" maxlength="3000" rows="5" required /></label>
      <label><input v-model="agreement.forceReaccept" type="checkbox" /><span>版本变化后要求重新接受</span></label>
      <button class="button primary" type="button" :disabled="savingAgreement || loading || !agreement.rulesVersion.trim() || !agreement.privacyVersion.trim() || !agreement.rulesText.trim() || !agreement.privacyText.trim()" @click="saveAgreement"><Save :size="17" aria-hidden="true" />{{ savingAgreement ? "保存中" : "保存协议" }}</button>
    </section>
  </section>
</template>

<style scoped>
.settings-panel { display: grid; gap: var(--space-md); max-width: 42rem; padding: var(--space-lg); }
.settings-panel label { display: grid; grid-template-columns: auto 1fr; gap: 0 var(--space-sm); align-items: center; }
.settings-panel input[type="checkbox"] { grid-row: span 2; width: 1.1rem; height: 1.1rem; }
.settings-panel small { color: var(--color-muted); }
.settings-panel .button { justify-self: start; }
.agreement-panel { margin-top: var(--space-lg); }
.agreement-panel .text-field { display: grid; grid-template-columns: 1fr; gap: var(--space-xs); }
.agreement-panel .text-field input, .agreement-panel textarea { width: 100%; padding: var(--space-sm); border: 1px solid var(--color-rule); background: var(--color-surface); color: var(--color-ink); }
</style>
