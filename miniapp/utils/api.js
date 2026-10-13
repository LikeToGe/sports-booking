function messageOf(data, fallback) {
  return data?.msg || fallback;
}

function operationFeedback(success, message) {
  const app = getApp();
  if (typeof app.showOperationFeedback === "function") app.showOperationFeedback(success, message);
}

async function request(path, options = {}, retried = false) {
  const app = getApp();
  const publicRead = path.startsWith("/public/");
  const method = String(options.method || "GET").toUpperCase();
  const isOperation = !["GET", "HEAD", "OPTIONS"].includes(method);
  const token = publicRead ? "" : await app.ensureLogin();
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app.globalData.apiBase}${path}`,
      method: options.method || "GET",
      data: options.data,
      header: { "X-Mini-Token": token },
      success: ({ statusCode, data }) => {
        if (statusCode >= 200 && statusCode < 300 && data.code === 0) {
          if (isOperation) operationFeedback(true);
          resolve(data.data);
          return;
        }
        if (statusCode === 401 && !retried && !publicRead) {
          // Redis 重启或会话过期后重新登录一次，避免用户卡在失效的本地 Token。
          app.globalData.token = "";
          app.globalData.profile = null;
          wx.removeStorageSync("yuechangMiniToken");
          request(path, options, true).then(resolve, reject);
          return;
        }
        const message = messageOf(data, `请求失败（${statusCode}）`);
        if (isOperation) operationFeedback(false, message);
        reject(new Error(message));
      },
      fail: () => {
        const message = "无法连接本地服务";
        if (isOperation) operationFeedback(false, message);
        reject(new Error(message));
      },
    });
  });
}

async function uploadFile(filePath, durationSeconds, retried = false) {
  const app = getApp();
  const token = await app.ensureLogin();
  return new Promise((resolve, reject) => wx.uploadFile({
    url: `${app.globalData.apiBase}/mini/content/uploads`,
    filePath,
    name: "file",
    formData: durationSeconds == null ? {} : { durationSeconds: String(durationSeconds) },
    header: { "X-Mini-Token": token },
    success: ({ statusCode, data }) => {
      let body;
      try { body = JSON.parse(data); } catch (_) { body = {}; }
      if (statusCode >= 200 && statusCode < 300 && body.code === 0) {
        operationFeedback(true);
        return resolve(body.data);
      }
      if (statusCode === 401 && !retried) {
        app.globalData.token = "";
        wx.removeStorageSync("yuechangMiniToken");
        return uploadFile(filePath, durationSeconds, true).then(resolve, reject);
      }
      const message = messageOf(body, `上传失败（${statusCode}）`);
      operationFeedback(false, message);
      reject(new Error(message));
    },
    fail: () => {
      const message = "无法上传媒体";
      operationFeedback(false, message);
      reject(new Error(message));
    },
  }));
}

async function downloadMedia(path, retried = false) {
  const app = getApp();
  const token = await app.ensureLogin();
  return new Promise((resolve, reject) => wx.downloadFile({
    url: `${app.globalData.apiBase}${path}`,
    header: { "X-Mini-Token": token },
    success: ({ statusCode, tempFilePath }) => {
      if (statusCode >= 200 && statusCode < 300) return resolve(tempFilePath);
      if (statusCode === 401 && !retried) {
        app.globalData.token = "";
        wx.removeStorageSync("yuechangMiniToken");
        return downloadMedia(path, true).then(resolve, reject);
      }
      reject(new Error(`媒体加载失败（${statusCode}）`));
    },
    fail: () => reject(new Error("无法加载媒体")),
  }));
}

function dateInZhengzhou(offsetDays = 0) {
  const date = new Date(Date.now() + 8 * 60 * 60 * 1000 + offsetDays * 24 * 60 * 60 * 1000);
  return date.toISOString().slice(0, 10);
}

function money(fen) {
  return `¥${(Number(fen || 0) / 100).toFixed(2)}`;
}

function localValue(value) {
  if (!value) return null;
  const text = String(value);
  if (!/(Z|[+-]\d{2}:\d{2})$/.test(text)) return text;
  const parsed = new Date(text);
  return Number.isNaN(parsed.getTime()) ? null : new Date(parsed.getTime() + 8 * 60 * 60 * 1000).toISOString();
}

function date(value) {
  const local = localValue(value);
  return local ? local.slice(0, 10) : "—";
}

function time(value) {
  const local = localValue(value);
  if (!local) return "—";
  const matched = local.match(/T(\d{2}:\d{2})/);
  return matched ? matched[1] : value;
}

function dateTime(value) {
  const local = localValue(value);
  return local ? local.slice(0, 16).replace("T", " ") : "—";
}

function assetUrl(path) {
  return path ? `${getApp().globalData.apiBase}${path}` : "";
}

module.exports = { request, uploadFile, downloadMedia, dateInZhengzhou, money, date, time, dateTime, assetUrl };
