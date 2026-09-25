import { createRouter, createWebHistory } from "vue-router";
import { auth, defaultRoute, hasPermission } from "./auth";
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
import CustomersView from "./views/CustomersView.vue";
import PlatformRefundsView from "./views/PlatformRefundsView.vue";
import RechargePlansView from "./views/RechargePlansView.vue";
import RolesView from "./views/RolesView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView, meta: { public: true } },
    { path: "/platform/venues", component: PlatformVenuesView, meta: { role: "PLATFORM_ADMIN" } },
    { path: "/platform/sports", component: PlatformSportsView, meta: { role: "PLATFORM_ADMIN" } },
    { path: "/platform/reports", component: ReportsView, meta: { role: "PLATFORM_ADMIN" } },
    { path: "/platform/refunds", component: PlatformRefundsView, meta: { role: "PLATFORM_ADMIN" } },
    { path: "/today", component: TodayView, meta: { role: "VENUE_ADMIN", permission: "DASHBOARD_VIEW" } },
    { path: "/bookings", component: BookingsView, meta: { role: "VENUE_ADMIN", permission: "BOOKING_VIEW" } },
    { path: "/customers", component: CustomersView, meta: { role: "VENUE_ADMIN", permission: "CUSTOMER_VIEW" } },
    { path: "/recharge-plans", component: RechargePlansView, meta: { role: "VENUE_ADMIN", permission: "RECHARGE_PLAN_EDIT" } },
    { path: "/courts", component: CourtsView, meta: { role: "VENUE_ADMIN", permission: "COURT_VIEW" } },
    { path: "/schedule", component: ScheduleView, meta: { role: "VENUE_ADMIN", permission: "SCHEDULE_VIEW" } },
    { path: "/sessions", component: SessionsView, meta: { role: "VENUE_ADMIN", permission: "SESSION_VIEW" } },
    { path: "/employees", component: EmployeesView, meta: { role: "VENUE_ADMIN", permission: "EMPLOYEE_VIEW" } },
    { path: "/roles", component: RolesView, meta: { role: "VENUE_ADMIN", permission: "ROLE_MANAGE" } },
    { path: "/profile", component: ProfileView, meta: { role: "VENUE_ADMIN", permission: "VENUE_PROFILE_VIEW" } },
    { path: "/reports", component: ReportsView, meta: { role: "VENUE_ADMIN", permission: "REPORT_VIEW" } },
    { path: "/:pathMatch(.*)*", redirect: defaultRoute },
  ],
});

router.beforeEach((to) => {
  if (to.meta.public) return true;
  if (!auth.token || !auth.principal) return "/login";
  const venueRoleMatches = to.meta.role === "VENUE_ADMIN" && auth.principal.role === "VENUE_EMPLOYEE";
  if (to.meta.role && auth.principal.role !== to.meta.role && !venueRoleMatches) {
    return defaultRoute();
  }
  if (!hasPermission(to.meta.permission as string | undefined)) return defaultRoute();
  return true;
});
