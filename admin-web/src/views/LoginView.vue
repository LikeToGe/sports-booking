<script setup lang="ts">
import { requireText } from "../validation";
import { onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { PanelsTopLeft, Clipboard, LogIn, RefreshCw, ScanLine } from "@lucide/vue";
import { api } from "../api";
import { defaultRoute, setSession } from "../auth";
import type { AdminPrincipal } from "../types";

interface ScanTicket {
  ticket: string;
  qrContent: string;
  qrDataUrl?: string;
  expiresAt: string;
  status: string;
}

const router = useRouter();
const mode = ref<"account" | "scan">("account");
const username = ref("");
const password = ref("");
const error = ref("");
const loading = ref(false);
const scanTicket = ref<ScanTicket>();
let pollTimer: number | undefined;

async function login() {
  error.value = "";
  loading.value = true;
  try {
    requireText(username.value, "用户名");
    requireText(password.value, "密码");
    const result = await api<{ token: string; principal: AdminPrincipal }>("/auth/admin/login", {
      method: "POST",
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    });
    setSession(result.token, result.principal);
    await router.replace(defaultRoute());
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "登录失败，请检查账号后重试";
  } finally {
    loading.value = false;
  }
}

function stopPolling() {
  if (pollTimer) window.clearInterval(pollTimer);
  pollTimer = undefined;
}

async function createScanTicket() {
  stopPolling();
  error.value = "";
  loading.value = true;
  try {
    scanTicket.value = await api<ScanTicket>("/auth/admin/web-login-tickets", { method: "POST" });
    pollTimer = window.setInterval(pollScanTicket, 1500);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "登录票据创建失败";
  } finally {
    loading.value = false;
  }
}

async function pollScanTicket() {
  if (!scanTicket.value || loading.value) return;
  try {
    const current = await api<ScanTicket>(`/auth/admin/web-login-tickets/${scanTicket.value.ticket}`);
    scanTicket.value = current;
    if (current.status === "CONFIRMED") await consumeScanTicket();
    else if (["EXPIRED", "CONSUMED"].includes(current.status)) stopPolling();
  } catch (reason) {
    stopPolling();
    error.value = reason instanceof Error ? reason.message : "扫码状态查询失败";
    if (scanTicket.value) scanTicket.value = { ...scanTicket.value, status: "EXPIRED" };
  }
}

async function consumeScanTicket() {
  if (!scanTicket.value) return;
  stopPolling();
  loading.value = true;
  try {
    const result = await api<{ token: string; principal: AdminPrincipal }>(`/auth/admin/web-login-tickets/${scanTicket.value.ticket}/consume`, { method: "POST" });
    setSession(result.token, result.principal);
    await router.replace(defaultRoute());
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "扫码登录失败";
  } finally {
    loading.value = false;
  }
}

function switchMode(next: "account" | "scan") {
  mode.value = next;
  error.value = "";
  stopPolling();
  if (next === "scan") void createScanTicket();
}

async function copyQrContent() {
  if (scanTicket.value?.qrContent) await navigator.clipboard.writeText(scanTicket.value.qrContent);
}

onUnmounted(stopPolling);
</script>

<template>
  <main class="login-page">
    <section class="login-copy">
      <div class="login-brand"><span class="brand-mark"><PanelsTopLeft :size="26" aria-hidden="true" /></span><div><span class="wordmark">约场</span><span>场馆运营管理</span></div></div>
      <h1>每一场，都安排妥当。</h1>
      <p>场馆、排期、预约与核销集中在一处。</p>
    </section>
    <section class="login-form" aria-labelledby="login-title">
      <div>
        <h2 id="login-title">登录管理后台</h2>
        <p class="muted">管理员使用账号登录，员工使用小程序确认登录</p>
      </div>
      <div class="login-modes" role="tablist" aria-label="登录方式">
        <button type="button" role="tab" :aria-selected="mode === 'account'" @click="switchMode('account')">账号登录</button>
        <button type="button" role="tab" :aria-selected="mode === 'scan'" @click="switchMode('scan')">员工扫码</button>
      </div>
      <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
      <form v-if="mode === 'account'" class="login-fields" @submit.prevent="login">
        <label class="field"><span>用户名</span><input v-model.trim="username" name="username" autocomplete="username" required></label>
        <label class="field"><span>密码</span><input v-model="password" name="password" type="password" autocomplete="current-password" required></label>
        <button class="button" type="submit" :disabled="loading"><LogIn :size="18" aria-hidden="true" />{{ loading ? "正在登录" : "登录" }}</button>
      </form>
      <div v-else class="scan-login" aria-live="polite">
        <ScanLine :size="36" aria-hidden="true" />
        <template v-if="scanTicket">
          <strong>{{ scanTicket.status === "CONFIRMED" ? "已确认，正在登录" : scanTicket.status === "EXPIRED" ? "登录内容已过期" : "等待小程序确认" }}</strong>
          <img v-if="scanTicket.qrDataUrl" class="scan-qr" :src="scanTicket.qrDataUrl" alt="员工登录二维码">
          <code>{{ scanTicket.qrContent }}</code>
          <span class="muted">在小程序“我的 - Web 登录”中扫码，或复制以上内容。</span>
          <button class="button secondary" type="button" :disabled="loading" @click="copyQrContent"><Clipboard :size="18" aria-hidden="true" />复制扫码内容</button>
        </template>
        <button v-if="!scanTicket || scanTicket.status === 'EXPIRED'" class="button" type="button" :disabled="loading" @click="createScanTicket"><RefreshCw :size="18" aria-hidden="true" />{{ loading ? "正在生成" : "重新生成" }}</button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page { display: grid; min-height: 100dvh; background: var(--color-surface); }
.login-copy { position: relative; display: grid; align-content: end; gap: 16px; min-height: 300px; margin: 20px; padding: 36px; overflow: hidden; border-radius: 16px; isolation: isolate; background: var(--color-ink) url("/venue-1.jpg") center / cover no-repeat; color: var(--color-on-dark); }
.login-copy::before { position: absolute; inset: 0; z-index: -1; content: ""; background: linear-gradient(180deg, rgb(14 43 46 / 45%), rgb(14 43 46 / 88%)); }
.login-brand { position: absolute; top: 36px; left: 36px; display: flex; align-items: center; gap: 12px; }
.login-brand > div { display: grid; gap: 5px; }
.login-brand > div > span:last-child { font-size: 12px; }
.login-copy h1 { font-size: 36px; line-height: 1.4; font-weight: 600; }
.login-copy p { font-size: 13px; }
.login-form { display: grid; align-content: center; gap: 24px; width: min(100%, 408px); margin: 0 auto; padding: 36px 24px; }
.login-form h2 { font-size: 24px; }
.login-form > div:first-child p { margin-top: 12px; font-size: 13px; line-height: 1.8; }
.login-fields, .scan-login { display: grid; gap: 20px; }
.login-modes { display: flex; gap: 28px; border-bottom: 1px solid var(--color-rule); }
.login-modes button { min-height: 44px; padding: 0; border: 0; border-bottom: 2px solid transparent; border-radius: 0; background: transparent; color: var(--color-muted); font-size: 13px; }
.login-modes button[aria-selected="true"] { border-bottom-color: var(--color-primary); color: var(--color-primary); }
.scan-login { justify-items: center; min-height: 18rem; align-content: center; text-align: center; }
.scan-login code { width: 100%; max-height: 6rem; padding: 12px; overflow: auto; border: 1px solid var(--color-rule); border-radius: var(--radius-control); background: var(--color-paper); overflow-wrap: anywhere; white-space: pre-wrap; user-select: all; font-size: 12px; }
.scan-qr { width: 180px; aspect-ratio: 1; object-fit: contain; }
.scan-login .muted { font-size: 12px; }
@media (min-width: 52rem) { .login-page { grid-template-columns: minmax(0, .46fr) minmax(0, .54fr); } }
@media (max-width: 40rem) { .login-copy { min-height: 240px; margin: 12px; padding: 24px; } .login-brand { top: 24px; left: 24px; } .login-copy h1 { font-size: 27px; } .login-form { padding-top: 24px; } }
</style>
