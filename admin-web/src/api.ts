import { auth, clearSession } from "./auth";

const API_BASE = import.meta.env.VITE_API_BASE || "http://127.0.0.1:8089/api";

interface Envelope<T> {
  data?: T;
  code: number;
  msg: string;
}

export class ApiError<T = unknown> extends Error {
  constructor(message: string, public readonly data?: T) {
    super(message);
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (auth.token) {
    headers.set("X-Admin-Token", auth.token);
  }
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const body = (await response.json().catch(() => ({}))) as Envelope<T>;
  if (!response.ok || body.code !== 0) {
    if (response.status === 401) clearSession();
    throw new ApiError(body.msg || `请求失败（${response.status}）`, body.data);
  }
  return body.data as T;
}

export function money(fen: number | null | undefined) {
  return fen == null ? "—" : new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
  }).format(fen / 100);
}

export function preciseYuan(value: string | null | undefined) {
  return value == null ? "—" : `¥${value}`;
}

export function percent(basisPoints: number | null | undefined) {
  return basisPoints == null ? "—" : `${(basisPoints / 100).toFixed(2)}%`;
}

export function localDateTime(value: string | null | undefined) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Shanghai",
  }).format(new Date(value));
}

export function today() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(new Date());
}
