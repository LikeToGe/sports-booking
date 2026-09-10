import { createRouter, createWebHistory } from "vue-router";
import { auth } from "./auth";
import LoginView from "./views/LoginView.vue";
import PlatformVenuesView from "./views/PlatformVenuesView.vue";
import PlatformSportsView from "./views/PlatformSportsView.vue";
import TodayView from "./views/TodayView.vue";
import BookingsView from "./views/BookingsView.vue";
import CourtsView from "./views/CourtsView.vue";
import ScheduleView from "./views/ScheduleView.vue";
import SessionsView from "./views/SessionsView.vue";
import EmployeesView from "./views/EmployeesView.vue";
import ProfileView from "./views/ProfileView.vue";
import ReportsView from "./views/ReportsView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView, meta: { public: true } },
    { path: "/platform/venues", component: PlatformVenuesView, meta: { role: "PLATFORM_ADMIN" } },
    { path: "/platform/sports", component: PlatformSportsView, meta: { role: "PLATFORM_ADMIN" } },
    { path: "/platform/reports", component: ReportsView, meta: { role: "PLATFORM_ADMIN" } },
    { path: "/today", component: TodayView, meta: { role: "VENUE_ADMIN" } },
    { path: "/bookings", component: BookingsView, meta: { role: "VENUE_ADMIN" } },
    { path: "/courts", component: CourtsView, meta: { role: "VENUE_ADMIN" } },
    { path: "/schedule", component: ScheduleView, meta: { role: "VENUE_ADMIN" } },
    { path: "/sessions", component: SessionsView, meta: { role: "VENUE_ADMIN" } },
    { path: "/employees", component: EmployeesView, meta: { role: "VENUE_ADMIN" } },
    { path: "/profile", component: ProfileView, meta: { role: "VENUE_ADMIN" } },
    { path: "/reports", component: ReportsView, meta: { role: "VENUE_ADMIN" } },
    { path: "/:pathMatch(.*)*", redirect: () => auth.principal?.role === "PLATFORM_ADMIN" ? "/platform/venues" : "/today" },
  ],
});

router.beforeEach((to) => {
  if (to.meta.public) return true;
  if (!auth.token || !auth.principal) return "/login";
  if (to.meta.role && auth.principal.role !== to.meta.role) {
    return auth.principal.role === "PLATFORM_ADMIN" ? "/platform/venues" : "/today";
  }
  return true;
});
