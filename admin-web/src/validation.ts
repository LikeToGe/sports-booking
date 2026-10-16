import type { CourtView } from "./types";

/** 校验已有表单约束，错误由页面原有提示区域展示。 */
export function requireText(value: unknown, label: string, maxLength?: number) {
  if (typeof value !== "string" || !value.trim()) throw new Error(`请填写${label}`);
  if (maxLength && value.trim().length > maxLength) throw new Error(`${label}不能超过 ${maxLength} 个字`);
}

export function requireNumber(value: unknown, label: string, min: number, max = 2147483647, integer = true) {
  if (typeof value !== "number" || !Number.isFinite(value)) throw new Error(`请填写有效的${label}`);
  if (integer && !Number.isInteger(value)) throw new Error(`${label}必须是整数`);
  if (value < min) throw new Error(`${label}不能小于 ${min}`);
  if (value > max) throw new Error(`${label}不能大于 ${max}`);
}

export function requireSelection(values: unknown[], label: string) {
  if (!values.length) throw new Error(`请至少选择一项${label}`);
}

export function validateAmount(value: unknown, label: string) {
  requireNumber(value, label, 0, 21474836.47, false);
  const cents = Number(value) * 100;
  if (Math.abs(cents - Math.round(cents)) > 0.000001) throw new Error(`${label}最多保留两位小数`);
}

export function validateImage(file: File) {
  if (!/\.(jpe?g|png|webp)$/i.test(file.name)) throw new Error("图片格式仅支持 JPG、JPEG、PNG 或 WebP");
  if (file.size === 0) throw new Error("图片文件为空，请重新选择");
  if (file.size > 30 * 1024 * 1024) throw new Error("图片大小不能超过 30 MB");
}

export function validateTimeRange(start: string, end: string) {
  if (!start) throw new Error("请选择开始时间");
  if (!end) throw new Error("请选择结束时间");
  if (start >= end) throw new Error("结束时间必须晚于开始时间，不能跨天");
}

export function validateDateRange(start: string, end: string, maxDays = 90) {
  if (!start) throw new Error("请选择开始日期");
  if (!end) throw new Error("请选择结束日期");
  if (start > end) throw new Error("结束日期不能早于开始日期");
  if ((Date.parse(end) - Date.parse(start)) / 86400000 >= maxDays) {
    throw new Error(`查询日期范围不能超过 ${maxDays} 天`);
  }
}

export function validateSlot(
  form: { courtId: number; sportTypeId: number; date?: string; startTime: string; endTime: string; priceYuan: number },
  courts: CourtView[],
) {
  if (!form.courtId) throw new Error("请选择场地");
  if (!form.sportTypeId) throw new Error("请选择运动类型");
  const court = courts.find(item => item.id === form.courtId);
  if (!court) throw new Error("所选场地不可用，请重新选择场地");
  if (!court.sportIds.split(",").includes(String(form.sportTypeId))) {
    throw new Error("所选场地不支持该运动，请重新选择运动类型");
  }
  if ("date" in form && !form.date) throw new Error("请选择场次日期");
  validateTimeRange(form.startTime, form.endTime);
  validateAmount(form.priceYuan, "场次价格");
}

export function validateVenue(form: {
  name?: string; city?: string; address?: string; phone?: string;
  bookingDaysAhead?: number; minBookingMinutes?: number; cancelCutoffMinutes?: number;
}) {
  requireText(form.name, "场馆名称");
  requireText(form.city, "所在城市");
  requireText(form.address, "详细地址");
  requireText(form.phone, "联系电话");
  requireNumber(form.bookingDaysAhead, "可提前预约天数", 1);
  requireNumber(form.minBookingMinutes, "最晚提前预约分钟数", 0);
  requireNumber(form.cancelCutoffMinutes, "最晚提前取消分钟数", 0);
}

/** 保留浏览器的原生定位与无障碍提示，并在错误中带上字段名称。 */
export function explainInvalidField(event: Event) {
  const field = event.target;
  if (!(field instanceof HTMLInputElement || field instanceof HTMLSelectElement || field instanceof HTMLTextAreaElement)) return;
  field.setCustomValidity("");
  const label = field.labels?.[0]?.querySelector("span")?.textContent?.replace(/\s*\*$/, "").trim() || "此项";
  const validity = field.validity;
  let message = "";
  if (validity.valueMissing) message = `请${field instanceof HTMLSelectElement ? "选择" : "填写"}${label}`;
  else if (validity.rangeUnderflow) message = `${label}不能小于 ${field.getAttribute("min")}`;
  else if (validity.rangeOverflow) message = `${label}不能大于 ${field.getAttribute("max")}`;
  else if (validity.stepMismatch) message = `${label}请按 ${field.getAttribute("step") || "1"} 的步长填写`;
  else if (validity.tooLong) message = `${label}不能超过 ${field.getAttribute("maxlength")} 个字`;
  else if (!validity.valid) message = `${label}格式不正确，请检查后重试`;
  field.setCustomValidity(message);
}
