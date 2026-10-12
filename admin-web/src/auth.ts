import { computed, reactive } from "vue";
import type { AdminPrincipal } from "./types";

const storedPrincipal = localStorage.getItem("yuechangAdminPrincipal");

export const auth = reactive<{
  token: string;
  principal: AdminPrincipal | null;
}>({
  token: localStorage.getItem("yuechangAdminToken") || "",
  principal: storedPrincipal ? JSON.parse(storedPrincipal) : null,
});

export const isAuthenticated = computed(() => Boolean(auth.token && auth.principal));

const venueRoutes: Array<[string, string]> = [
  ["/today", "DASHBOARD_VIEW"],
  ["/bookings", "BOOKING_VIEW"],
  ["/customers", "CUSTOMER_VIEW"],
  ["/recharge-plans", "RECHARGE_PLAN_EDIT"],
  ["/reports", "REPORT_VIEW"],
  ["/courts", "COURT_VIEW"],
  ["/schedule", "SCHEDULE_VIEW"],
  ["/sessions", "SESSION_VIEW"],
  ["/employees", "EMPLOYEE_VIEW"],
  ["/roles", "ROLE_MANAGE"],
  ["/profile", "VENUE_PROFILE_VIEW"],
  ["/venue-reviews", "REVIEW_MANAGE"],
];

export function hasPermission(permission?: string) {
  if (!permission || auth.principal?.role === "PLATFORM_ADMIN") return true;
  return auth.principal?.role === "VENUE_ADMIN" || auth.principal?.permissions?.includes(permission);
}

export function defaultRoute() {
  if (auth.principal?.role === "PLATFORM_ADMIN") return "/platform/venues";
  return venueRoutes.find(([, permission]) => hasPermission(permission))?.[0] || "/login";
}

export function setSession(token: string, principal: AdminPrincipal) {
  auth.token = token;
  auth.principal = { ...principal, permissions: principal.permissions || [] };
  localStorage.setItem("yuechangAdminToken", token);
  localStorage.setItem("yuechangAdminPrincipal", JSON.stringify(auth.principal));
}

export function clearSession() {
  auth.token = "";
  auth.principal = null;
  localStorage.removeItem("yuechangAdminToken");
  localStorage.removeItem("yuechangAdminPrincipal");
}
