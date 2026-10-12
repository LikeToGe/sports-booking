const DEFAULT_API_BASE = "http://127.0.0.1:8089/api";

function normalizeApiBase(value) {
  const normalized = String(value || "").trim().replace(/\/+$/, "");
  if (!/^https?:\/\/.+\/api$/.test(normalized)) throw new Error("请输入以 /api 结尾的 HTTP 地址");
  return normalized;
}

App({
  globalData: {
    apiBase: DEFAULT_API_BASE,
    token: "",
    profile: null,
    loginPromise: null,
    operationFeedback: { version: 0, visible: false, tone: "success", message: "" },
  },

  onLaunch() {
    const configuredApiBase = wx.getStorageSync("yuechangApiBase");
    if (configuredApiBase) {
      try {
        this.globalData.apiBase = normalizeApiBase(configuredApiBase);
      } catch (_) {
        wx.removeStorageSync("yuechangApiBase");
      }
    }
    this.globalData.token = wx.getStorageSync("yuechangMiniToken") || "";
  },

  setApiBase(value) {
    const apiBase = normalizeApiBase(value);
    this.globalData.apiBase = apiBase;
    this.globalData.token = "";
    this.globalData.profile = null;
    this.globalData.loginPromise = null;
    wx.setStorageSync("yuechangApiBase", apiBase);
    wx.removeStorageSync("yuechangMiniToken");
    return apiBase;
  },

  showOperationFeedback(success, message) {
    const current = this.globalData.operationFeedback;
    this.globalData.operationFeedback = {
      version: current.version + 1,
      visible: true,
      tone: success ? "success" : "error",
      message: success ? "操作成功" : message,
    };
    if (this.operationFeedbackTimer) clearTimeout(this.operationFeedbackTimer);
    this.operationFeedbackTimer = setTimeout(() => {
      this.globalData.operationFeedback = { ...this.globalData.operationFeedback, visible: false };
    }, success ? 1800 : 5000);
  },

  ensureLogin() {
    if (this.globalData.token) return Promise.resolve(this.globalData.token);
    if (this.globalData.loginPromise) return this.globalData.loginPromise;
    this.globalData.loginPromise = new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.apiBase}/auth/mini/mock-login`,
        method: "POST",
        data: { code: "local-demo-user" },
        success: ({ statusCode, data }) => {
          if (statusCode >= 200 && statusCode < 300 && data.code === 0) {
            this.globalData.token = data.data.token;
            this.globalData.profile = data.data.profile;
            wx.setStorageSync("yuechangMiniToken", data.data.token);
            resolve(data.data.token);
          } else {
            reject(new Error(data?.msg || "登录失败"));
          }
        },
        fail: reject,
        complete: () => { this.globalData.loginPromise = null; },
      });
    });
    return this.globalData.loginPromise;
  },
});
