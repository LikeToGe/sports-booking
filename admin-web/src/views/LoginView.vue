<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { LogIn } from "@lucide/vue";
import { api } from "../api";
import { setSession } from "../auth";
import type { AdminPrincipal } from "../types";

const router = useRouter();
const username = ref("");
const password = ref("");
const error = ref("");
const loading = ref(false);

async function login() {
  error.value = "";
  loading.value = true;
  try {
    const result = await api<{ token: string; principal: AdminPrincipal }>("/auth/admin/login", {
      method: "POST",
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    });
    setSession(result.token, result.principal);
    await router.replace(result.principal.role === "PLATFORM_ADMIN" ? "/platform/venues" : "/today");
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "登录失败，请检查账号后重试";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-copy">
      <span class="wordmark">约场</span>
      <h1>今天的场次，直接处理。</h1>
      <p>场馆、排期、预约与核销集中在一处。</p>
    </section>
    <form class="login-form" aria-labelledby="login-title" @submit.prevent="login">
      <div>
        <h2 id="login-title">登录管理后台</h2>
        <p class="muted">使用平台或场馆管理员账号</p>
      </div>
      <label class="field">
        <span>用户名</span>
        <input v-model.trim="username" name="username" autocomplete="username" required>
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" name="password" type="password" autocomplete="current-password" required>
      </label>
      <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
      <button class="button" type="submit" :disabled="loading">
        <LogIn :size="18" aria-hidden="true" />
        {{ loading ? "正在登录" : "登录" }}
      </button>
    </form>
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

.login-copy {
  min-height: 18rem;
  background: var(--color-ink);
  color: var(--color-paper);
}

.login-copy h1 {
  max-width: 12ch;
  font-size: var(--text-2xl);
}

.login-copy p {
  color: var(--color-rule);
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
}
</style>
