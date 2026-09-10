App({
  globalData: {
    apiBase: "http://127.0.0.1:8089/api",
    token: "",
    profile: null,
    loginPromise: null,
  },

  onLaunch() {
    this.globalData.token = wx.getStorageSync("yuechangMiniToken") || "";
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
