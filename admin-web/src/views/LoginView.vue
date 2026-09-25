<script setup lang="ts">
import { onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Clipboard, LogIn, RefreshCw, ScanLine } from "@lucide/vue";
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
      <span class="wordmark">约场</span>
      <h1>场次就绪，直接开打。</h1>
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
.login-page {
  display: grid;
  min-height: 100dvh;
  background: var(--color-paper);
}

.login-copy,
.login-form {
  display: grid;
  align-content: center;
  gap: var(--space-lg);
  padding: var(--space-xl) var(--space-lg);
}

.login-fields,
.scan-login {
  display: grid;
  gap: var(--space-md);
}

.login-modes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: var(--space-2xs);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-control);
}

.login-modes button {
  min-height: var(--control-height);
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  cursor: pointer;
}

.login-modes button[aria-selected="true"] {
  background: var(--color-ink);
  color: var(--color-paper);
}

.scan-login {
  justify-items: center;
  min-height: 18rem;
  align-content: center;
  text-align: center;
}

.scan-login code {
  width: 100%;
  max-height: 8rem;
  padding: var(--space-md);
  overflow: auto;
  border: 1px solid var(--color-rule);
  background: var(--color-paper-2);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  user-select: all;
}

.scan-qr { width: min(15rem, 72vw); aspect-ratio: 1; object-fit: contain; }

.login-copy {
  position: relative;
  isolation: isolate;
  min-height: 18rem;
  overflow: hidden;
  border-block-start: var(--space-xs) solid var(--color-success);
  background: var(--color-ink) url("/venue-1.jpg") center / cover no-repeat;
  color: var(--color-paper);
}

.login-copy::before {
  position: absolute;
  inset: 0;
  z-index: -1;
  background: var(--color-scrim);
  content: "";
}

.login-copy h1 {
  max-width: 10ch;
  font-size: 2rem;
  font-weight: 700;
  line-height: 1.05;
}

.login-copy p {
  color: var(--color-paper);
}

.login-form {
  width: min(100%, 28rem);
  margin-inline: auto;
}

@media (min-width: 52rem) {
  .login-page {
    grid-template-columns: minmax(20rem, 0.85fr) minmax(28rem, 1.15fr);
  }

  .login-copy,
  .login-form {
    padding: var(--space-2xl);
  }

  .login-copy h1 {
    max-width: 9ch;
    font-size: 3.5rem;
  }
}
</style>
