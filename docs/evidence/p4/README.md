# P4 开发证据（V8 评价、V9 动态首个使用点与 V10 审核门禁）

日期：2026-09-27。分支：`feature/operations-membership`。P4 业务实现与隔离运行态已完成；本文仍不是物理真机上线许可。

## 2026-10-12 提交前复验

- `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true ./mvn-project package`：biz 111 项、server 19 项，共 130 项测试通过。
- 管理后台 `npm run typecheck && npm run build` 通过。
- `node --test miniapp/tests/*.test.js`：5 个测试文件通过；原生编译验证 21 个 WXML、22 个 WXSS 通过。
- `zsh backend/scripts/verify-p4-v8-runtime.zsh` 最终完整通过，使用临时 MySQL/Redis 和临时媒体目录；验证评价/回复/版本治理、媒体/评论/点赞/拉黑、找搭子容量与隐私、消息、账号注销、限权申诉、协议重签和固定频率限制。空库 V1-V10 迁移在该次运行中成功。临时环境已按脚本清理。
- 首次隔离运行因同一场馆两片羽毛球场都命名为“1 号场”而启动失败。新增 `DemoDataRunnerTest` 回归先失败再通过，修正同运动第二片场地的序号；重跑打包通过。
- 随后发现运行脚本的评价标签与绑定预约运动不一致，招募请求也固定使用旧运动编号。脚本改为从实际场地/预约读取运动与场馆事实，未放宽业务校验；修正后完整运行通过。
- 本次未重跑 V7 升级路径、全部检测异常模式、真实微信渠道、开发者工具交互或物理真机；这些不能以本次自动化验证替代。
- 代码基线提交：`b244711`（P4 社区治理与双端界面调整）。P5/P6 仅完成需求和任务发布，尚未实施。

## 已验证

- 后端 `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true ./mvn-project package`：biz 111 项、server 18 项，共 129 项测试通过。macOS 本机不设 headless 时，POI 测试触发 AWT 原生进程崩溃。
- `zsh backend/scripts/verify-p4-v8-migrations.zsh`：MySQL 8.4 空库 V1-V8、V7 升级 V8，以及一预约一评价、评分范围、最多三个标签/图片、单条回复约束通过。
- `P4_OPENAPI_OUTPUT=docs/openapi.yaml zsh backend/scripts/verify-p4-v8-runtime.zsh`：临时 MySQL/Redis 中验证标签、评价/回复/动态的版本审核、过期与重复处置拒绝、审核开关只影响新动态、旧公开版保留、隐藏/恢复/删除及审计；隐藏动态的新版本通过后仍隐藏，删除的官方回复不能由场馆再次编辑。临时容器已清理，运行态 Springdoc 已同步到 `docs/openapi.yaml`。
- `zsh backend/scripts/verify-p4-v9-migrations.zsh`：MySQL 8.4 空库 V1-V9，验证动态、评论版本、媒体、点赞、找搭子和拉黑约束。
- `zsh backend/scripts/verify-p4-v10-migrations.zsh`：MySQL 8.4 空库 V1-V10，验证单行三类审核开关默认开启，以及动态/评价媒体清理事实列。
- 管理后台 `npm run typecheck && npm run build` 通过；小程序媒体/评论页面语法检查及现有 3 项 Node 测试通过。
- `zsh miniapp/scripts/verify-native-compile.zsh`：微信自带 WXML/WXSS 编译器验证 21 个 WXML、22 个 WXSS 文件。
- `node --test miniapp/tests/*.test.js`：4 项小程序行为测试通过，覆盖预约绑定、AA 金额、待审评论、失效落地页、UGC 开关 Tab 和公开接口免登录。
- `P4_DEMO_PROOF=true zsh backend/scripts/verify-p4-v8-runtime.zsh`：显式 `demo` profile 生成三家场馆评价、用户/官方动态、评论、举报、申诉和找搭子样本。
- `P4_DETECTION_OUTCOME=REVIEW|RISK|TIMEOUT|FAIL zsh backend/scripts/verify-p4-v8-runtime.zsh`：检测异常保持新版本待审，人工处置后才可公开。
- `P4_UGC_ENABLED=false zsh backend/scripts/verify-p4-v8-runtime.zsh`：UGC 关闭时用户公开读写被拒绝，平台治理历史入口仍可访问。
- V10 迁移脚本已验证治理表、协议表、媒体暂存/检测表、清理事实和举报/申诉唯一约束；后端打包通过（server 17 项测试通过）。

## 未完成

- 微信开发者工具 CLI 因 IDE 服务端口关闭无法进行模拟器交互；WXML/WXSS 原生编译已通过。物理真机仍按用户决定留到上线前。
- 已尝试通过 `cli engine build` 补开发者工具编译；IDE 服务端口仍返回关闭状态，native WXML/WXSS 编译和 Node 行为测试作为可交付证据。
- 治理页已展示评价、官方回复和动态的五种处置操作；评论及未来内容类型仍待接入，页面尚未完成浏览器多视口人工验收。
- 管理后台多视口、小程序开发者工具模拟器和物理真机验收；真机按用户决定留到上线前。
