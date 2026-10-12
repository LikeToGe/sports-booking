const { request } = require("./api");
const reasons = [{ code: "SPAM",name: "广告或垃圾内容" },{ code: "ABUSE",name: "辱骂或骚扰" },
  { code: "PRIVACY",name: "泄露隐私" },{ code: "MISLEADING",name: "不实内容" },{ code: "OTHER",name: "其他" }];

async function reportContent(targetType,targetId) {
  try {
    const selected = await new Promise((resolve,reject) => wx.showActionSheet({
      itemList: reasons.map(item => item.name),success: resolve,fail: reject,
    }));
    const details = await new Promise((resolve,reject) => wx.showModal({
      title: reasons[selected.tapIndex].name,editable: true,placeholderText: "补充说明（可选）",
      confirmText: "提交",success: resolve,fail: reject,
    }));
    if (!details.confirm) return false;
    const query = `targetType=${targetType}&targetId=${targetId}&reasonCode=${reasons[selected.tapIndex].code}&description=${encodeURIComponent(details.content.trim())}`;
    await request(`/mini/content/reports?${query}`,{ method: "POST" });
    return true;
  } catch (error) {
    if (/cancel/.test(error.errMsg || "")) return false;
    throw new Error(error.message || error.errMsg || "举报失败");
  }
}
module.exports = { reportContent };
