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

export function setSession(token: string, principal: AdminPrincipal) {
  auth.token = token;
  auth.principal = principal;
  localStorage.setItem("yuechangAdminToken", token);
  localStorage.setItem("yuechangAdminPrincipal", JSON.stringify(principal));
}

export function clearSession() {
  auth.token = "";
  auth.principal = null;
  localStorage.removeItem("yuechangAdminToken");
  localStorage.removeItem("yuechangAdminPrincipal");
}
