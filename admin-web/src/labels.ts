const STATUS_LABELS: Record<string, string> = {
  PENDING_PAYMENT: "待支付",
  CONFIRMED: "待核销",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
  NO_SHOW: "未到场",
  ACTIVE: "启用",
  DISABLED: "停用",
  DRAFT: "草稿",
  SUSPENDED: "已暂停",
  OPEN: "可预约",
  HELD: "支付占位",
  BOOKED: "已预约",
  CLOSED: "已关闭",
  PENDING: "处理中",
  SUCCESS: "成功",
  FAILED: "失败",
  REFUNDED: "已退款",
  MOCK_SENT: "模拟发送成功",
};

export function statusLabel(status: string) {
  return STATUS_LABELS[status] || "未知状态";
}

export function statusTone(status: string) {
  if (["ACTIVE", "OPEN", "COMPLETED"].includes(status)) return "active";
  if (["PENDING_PAYMENT", "PENDING", "CONFIRMED", "HELD", "BOOKED", "DRAFT"].includes(status)) return "warning";
  if (["CANCELLED", "NO_SHOW", "DISABLED", "SUSPENDED", "CLOSED", "FAILED"].includes(status)) return "danger";
  return "";
}
