<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Building2,
  CalendarDays,
  ChartNoAxesCombined,
  CircleUserRound,
  ClipboardList,
  Clock3,
  Dumbbell,
  LogOut,
  Menu,
  Settings2,
  Tags,
  UsersRound,
  X,
} from "@lucide/vue";
import { api } from "./api";
import { auth, clearSession } from "./auth";

const route = useRoute();
const router = useRouter();
const menuOpen = ref(false);

const isPublic = computed(() => Boolean(route.meta.public));
const platformGroups = [
  { label: "经营", links: [{ to: "/platform/reports", label: "经营总报表", icon: ChartNoAxesCombined }] },
  { label: "平台资源", links: [
    { to: "/platform/venues", label: "场馆管理", icon: Building2 },
    { to: "/platform/sports", label: "运动类型", icon: Tags },
  ] },
];
const venueGroups = [
  { label: "日常运营", links: [
    { to: "/today", label: "今日场次", icon: CalendarDays },
    { to: "/reports", label: "经营报表", icon: ChartNoAxesCombined },
    { to: "/bookings", label: "预约管理", icon: ClipboardList },
  ] },
  { label: "场馆资源", links: [
    { to: "/courts", label: "场地管理", icon: Dumbbell },
    { to: "/schedule", label: "固定排期", icon: Settings2 },
    { to: "/sessions", label: "具体场次", icon: Clock3 },
  ] },
  { label: "组织与设置", links: [
    { to: "/employees", label: "员工管理", icon: UsersRound },
    { to: "/profile", label: "场馆资料", icon: CircleUserRound },
  ] },
];
const navGroups = computed(() => auth.principal?.role === "PLATFORM_ADMIN" ? platformGroups : venueGroups);
const links = computed(() => navGroups.value.flatMap(group => group.links));
const currentLabel = computed(() => links.value.find(link => link.to === route.path)?.label || "运营工作台");

async function logout() {
  try {
    await api("/auth/admin/logout", { method: "POST" });
  } finally {
    clearSession();
    await router.replace("/login");
  }
}
</script>

<template>
  <RouterView v-if="isPublic" />
  <div v-else class="app-shell">
    <header class="mobile-header">
      <div><span class="wordmark">约场</span><span class="mobile-page-name">{{ currentLabel }}</span></div>
      <button class="icon-button" type="button" :aria-label="menuOpen ? '关闭导航' : '打开导航'" @click="menuOpen = !menuOpen">
        <X v-if="menuOpen" :size="20" aria-hidden="true" />
        <Menu v-else :size="20" aria-hidden="true" />
      </button>
    </header>

    <aside class="sidebar" :class="{ 'is-open': menuOpen }">
      <div class="brand-block">
        <span class="brand-mark">约</span>
        <div><span class="wordmark">约场</span><span class="brand-caption">运营管理后台</span></div>
      </div>
      <nav class="primary-nav" aria-label="主导航">
        <section v-for="group in navGroups" :key="group.label" class="nav-group">
          <h2>{{ group.label }}</h2>
          <RouterLink v-for="link in group.links" :key="link.to" :to="link.to" @click="menuOpen = false">
            <component :is="link.icon" :size="18" aria-hidden="true" />
            <span>{{ link.label }}</span>
          </RouterLink>
        </section>
      </nav>
      <div class="account-block">
        <div>
          <strong>{{ auth.principal?.displayName }}</strong>
          <span>{{ auth.principal?.role === "PLATFORM_ADMIN" ? "平台管理员" : "场馆管理员" }}</span>
        </div>
        <button class="icon-button" type="button" aria-label="退出登录" title="退出登录" @click="logout">
          <LogOut :size="18" aria-hidden="true" />
        </button>
      </div>
    </aside>
    <button v-if="menuOpen" class="nav-backdrop" type="button" aria-label="关闭导航" @click="menuOpen = false" />

    <div class="workspace" :inert="menuOpen">
      <header class="workspace-bar">
        <div class="breadcrumb"><span>运营管理</span><strong>{{ currentLabel }}</strong></div>
        <span class="workspace-role"><i aria-hidden="true"></i>{{ auth.principal?.role === "PLATFORM_ADMIN" ? "平台端" : "场馆端" }}</span>
      </header>
      <main id="main" class="main-content"><RouterView /></main>
    </div>
  </div>
</template>
