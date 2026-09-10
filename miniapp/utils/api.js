function messageOf(data, fallback) {
  return data?.msg || fallback;
}

async function request(path, options = {}, retried = false) {
  const app = getApp();
  const token = await app.ensureLogin();
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app.globalData.apiBase}${path}`,
      method: options.method || "GET",
      data: options.data,
      header: { "X-Mini-Token": token },
      success: ({ statusCode, data }) => {
        if (statusCode >= 200 && statusCode < 300 && data.code === 0) {
          resolve(data.data);
          return;
        }
        if (statusCode === 401 && !retried) {
          // Redis 重启或会话过期后重新登录一次，避免用户卡在失效的本地 Token。
          app.globalData.token = "";
          app.globalData.profile = null;
          wx.removeStorageSync("yuechangMiniToken");
          request(path, options, true).then(resolve, reject);
          return;
        }
        reject(new Error(messageOf(data, `请求失败（${statusCode}）`)));
      },
      fail: () => reject(new Error("无法连接本地服务")),
    });
  });
}

function dateInZhengzhou(offsetDays = 0) {
  const date = new Date(Date.now() + 8 * 60 * 60 * 1000 + offsetDays * 24 * 60 * 60 * 1000);
  return date.toISOString().slice(0, 10);
}

function money(fen) {
  return `¥${(Number(fen || 0) / 100).toFixed(0)}`;
}

function time(value) {
  if (!value) return "—";
  const matched = String(value).match(/T(\d{2}:\d{2})/);
  return matched ? matched[1] : value;
}

function assetUrl(path) {
  return path ? `${getApp().globalData.apiBase}${path}` : "";
}

module.exports = { request, dateInZhengzhou, money, time, assetUrl };
