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
  CircleDollarSign,
  Dumbbell,
  LogOut,
  Menu,
  Settings2,
  ShieldCheck,
  ShieldAlert,
  SlidersHorizontal,
  Tags,
  MessageSquareReply,
  WalletCards,
  UsersRound,
  UserRoundSearch,
  X,
  CircleCheck,
  CircleX,
} from "@lucide/vue";
import { api } from "./api";
import { auth, clearSession, hasPermission } from "./auth";
import { operationFeedback } from "./feedback";

const route = useRoute();
const router = useRouter();
const menuOpen = ref(false);

const isPublic = computed(() => Boolean(route.meta.public));
const platformGroups = [
  { label: "经营", links: [{ to: "/platform/reports", label: "经营总报表", icon: ChartNoAxesCombined }] },
  { label: "平台资金异常", links: [{ to: "/platform/refunds", label: "退款异常", icon: CircleDollarSign }] },
  { label: "内容治理", links: [
    { to: "/platform/moderation", label: "内容治理", icon: ShieldAlert },
    { to: "/platform/content-reports", label: "内容举报", icon: ShieldAlert },
    { to: "/platform/content-appeals", label: "内容申诉", icon: MessageSquareReply },
    { to: "/platform/content-restrictions", label: "账号内容权限", icon: ShieldCheck },
    { to: "/platform/official-posts", label: "官方动态", icon: MessageSquareReply },
    { to: "/platform/content-settings", label: "审核配置", icon: SlidersHorizontal },
    { to: "/platform/review-tags", label: "评价标签", icon: Tags },
  ] },
  { label: "平台资源", links: [
    { to: "/platform/venues", label: "场馆管理", icon: Building2 },
    { to: "/platform/sports", label: "运动类型", icon: Tags },
  ] },
];
const venueGroups = [
  { label: "工作台", links: [
    { to: "/today", label: "今日运营", icon: CalendarDays, permission: "DASHBOARD_VIEW" },
  ] },
  { label: "预约运营", links: [
    { to: "/bookings", label: "预约管理", icon: ClipboardList, permission: "BOOKING_VIEW" },
  ] },
  { label: "会员经营", links: [
    { to: "/customers", label: "顾客与会员", icon: UserRoundSearch, permission: "CUSTOMER_VIEW" },
    { to: "/recharge-plans", label: "充值档位", icon: WalletCards, permission: "RECHARGE_PLAN_EDIT" },
  ] },
  { label: "财务报表", links: [
    { to: "/reports", label: "经营报表", icon: ChartNoAxesCombined, permission: "REPORT_VIEW" },
  ] },
  { label: "内容运营", links: [
    { to: "/venue-reviews", label: "场地评价", icon: MessageSquareReply, permission: "REVIEW_MANAGE" },
    { to: "/official-posts", label: "官方动态", icon: MessageSquareReply, permission: "POST_PUBLISH" },
  ] },
  { label: "场馆设置", links: [
    { to: "/courts", label: "场地管理", icon: Dumbbell, permission: "COURT_VIEW" },
    { to: "/schedule", label: "固定排期", icon: Settings2, permission: "SCHEDULE_VIEW" },
    { to: "/sessions", label: "具体场次", icon: Clock3, permission: "SESSION_VIEW" },
    { to: "/employees", label: "员工管理", icon: UsersRound, permission: "EMPLOYEE_VIEW" },
    { to: "/roles", label: "角色权限", icon: ShieldCheck, permission: "ROLE_MANAGE" },
    { to: "/profile", label: "场馆资料", icon: CircleUserRound, permission: "VENUE_PROFILE_VIEW" },
  ] },
];
const navGroups = computed(() => (auth.principal?.role === "PLATFORM_ADMIN" ? platformGroups : venueGroups)
  .map(group => ({ ...group, links: group.links.filter(link => hasPermission("permission" in link ? link.permission as string : undefined)) }))
  .filter(group => group.links.length));
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
  <div v-if="operationFeedback.visible" :key="operationFeedback.sequence" class="operation-toast" :class="`is-${operationFeedback.tone}`" role="status" aria-live="polite">
    <CircleCheck v-if="operationFeedback.tone === 'success'" :size="18" aria-hidden="true" />
    <CircleX v-else :size="18" aria-hidden="true" />
    <span>{{ operationFeedback.message }}</span>
  </div>
  <RouterView v-if="isPublic" />
  <div v-else class="app-shell">
    <a class="skip-link" href="#main">跳到主要内容</a>
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
          <span>{{ auth.principal?.role === "PLATFORM_ADMIN" ? "平台管理员" : auth.principal?.role === "VENUE_EMPLOYEE" ? "场馆员工" : "场馆超级管理员" }}</span>
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
