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
  <section class="page content-settings-page">
    <header class="page-header"><div><h1>审核配置</h1><p>按内容类型配置审核，并维护社区规则与隐私说明。</p></div><button class="button secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="18" aria-hidden="true" />刷新</button></header>
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
      <label class="field text-field"><span>社区规则版本</span><input v-model="agreement.rulesVersion" maxlength="40" required /></label>
      <label class="field text-field"><span>社区规则正文</span><textarea v-model="agreement.rulesText" maxlength="3000" rows="5" required /></label>
      <label class="field text-field"><span>隐私说明版本</span><input v-model="agreement.privacyVersion" maxlength="40" required /></label>
      <label class="field text-field"><span>隐私说明正文</span><textarea v-model="agreement.privacyText" maxlength="3000" rows="5" required /></label>
      <label><input v-model="agreement.forceReaccept" type="checkbox" /><span>版本变化后要求重新接受</span></label>
      <button class="button primary" type="button" :disabled="savingAgreement || loading || !agreement.rulesVersion.trim() || !agreement.privacyVersion.trim() || !agreement.rulesText.trim() || !agreement.privacyText.trim()" @click="saveAgreement"><Save :size="17" aria-hidden="true" />{{ savingAgreement ? "保存中" : "保存协议" }}</button>
    </section>
  </section>
</template>

<style scoped>
.settings-panel { display: grid; gap: var(--space-md); max-width: none; align-content: start; padding: var(--space-lg); }
.settings-panel label { display: grid; grid-template-columns: auto minmax(0, 1fr); min-height: 64px; padding-block: 14px; border-bottom: 1px solid var(--color-rule); gap: 0 var(--space-sm); align-items: center; }
.settings-panel input[type="checkbox"] { grid-row: span 2; width: 1.25rem; height: 1.25rem; accent-color: var(--color-primary); }
.settings-panel small { color: var(--color-muted); }
.settings-panel .button { justify-self: start; }
.agreement-panel { margin-top: 0; }
.agreement-panel .text-field { display: grid; grid-template-columns: 1fr; gap: var(--space-xs); }
.agreement-panel .text-field input, .agreement-panel textarea { width: 100%; padding: var(--space-sm); border: 1px solid var(--color-rule); background: var(--color-surface); color: var(--color-ink); }
.content-settings-page { grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); align-items: start; }
.content-settings-page > .page-header, .content-settings-page > .error-banner, .content-settings-page > .saved-message { grid-column: 1 / -1; }
.settings-panel:not(.agreement-panel) label { grid-template-columns: minmax(0, 1fr) auto; }
.settings-panel:not(.agreement-panel) input[type="checkbox"] { grid-column: 2; grid-row: 1 / 3; appearance: none; position: relative; width: 36px; height: 21px; margin: 0; border-radius: 99px; background: var(--color-rule-strong); cursor: pointer; }
.settings-panel:not(.agreement-panel) input:checked { background: var(--color-primary); }
.settings-panel:not(.agreement-panel) input::after { content: ""; position: absolute; left: 3px; top: 3px; width: 15px; height: 15px; border-radius: 50%; background: var(--color-surface); }
.settings-panel:not(.agreement-panel) input:checked::after { left: 18px; }
.settings-panel:not(.agreement-panel) span, .settings-panel:not(.agreement-panel) small { grid-column: 1; }
@media (max-width: 60rem) { .content-settings-page { grid-template-columns: 1fr; } }
</style>
