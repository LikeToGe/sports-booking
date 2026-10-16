import { auth, clearSession } from "./auth";
import { showOperationFeedback } from "./feedback";

export const API_BASE = import.meta.env.VITE_API_BASE || "http://127.0.0.1:8089/api";

export async function mediaObjectUrl(path: string): Promise<string> {
  const response = await fetch(`${API_BASE}${path}`, { headers: { "X-Admin-Token": auth.token || "" } });
  if (!response.ok) throw new Error("媒体预览加载失败");
  return URL.createObjectURL(await response.blob());
}

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
  const method = (options.method || "GET").toUpperCase();
  const isOperation = !["GET", "HEAD", "OPTIONS"].includes(method);
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (auth.token) {
    headers.set("X-Admin-Token", auth.token);
  }
  try {
    const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    const body = (await response.json().catch(() => ({}))) as Envelope<T>;
    if (!response.ok || body.code !== 0) {
      if (response.status === 401) clearSession();
      const message = body.msg || `请求失败（${response.status}）`;
      if (isOperation) showOperationFeedback("error", message);
      throw new ApiError(message, body.data);
    }
    if (isOperation) showOperationFeedback("success", "操作成功");
    return body.data as T;
  } catch (reason) {
    if (isOperation && !(reason instanceof ApiError)) {
      showOperationFeedback("error", reason instanceof Error ? reason.message : "无法连接服务");
    }
    throw reason;
  }
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
