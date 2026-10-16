/** 提交前说明缺失或越界的字段，由页面原有错误区域展示。 */
function requireText(value, label, maxLength) {
  if (typeof value !== "string" || !value.trim()) throw new Error(`请填写${label}`);
  if (maxLength && value.trim().length > maxLength) throw new Error(`${label}不能超过 ${maxLength} 个字`);
}

function requireNumber(value, label, min, max) {
  if (!Number.isInteger(value)) throw new Error(`${label}必须是整数`);
  if (value < min || value > max) throw new Error(`${label}须在 ${min} 至 ${max} 之间`);
}

function validateContact(name, phone) {
  requireText(name, "联系人姓名");
  requireText(phone, "手机号");
  if (!/^\d{11}$/.test(phone.trim())) throw new Error("请填写完整的 11 位手机号");
}

function validateTimeRange(start, end) {
  if (!start) throw new Error("请选择开始时间");
  if (!end) throw new Error("请选择结束时间");
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(start)) throw new Error("开始时间格式应为 HH:mm，精确到分钟");
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(end)) throw new Error("结束时间格式应为 HH:mm，精确到分钟");
  if (start >= end) throw new Error("结束时间必须晚于开始时间，不能跨天");
}

module.exports = { requireText, requireNumber, validateContact, validateTimeRange };
