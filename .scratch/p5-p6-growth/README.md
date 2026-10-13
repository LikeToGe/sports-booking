# P5/P6 已发布任务索引

父需求：https://github.com/LikeToGe/sports-booking/issues/1

状态更新（2026-10-13）：P5 #2–#8 的实现与开发门禁已通过，证据见 docs/evidence/p5/README.md；P6 #9–#16 已实现且自动化/隔离业务验收通过，#17 最后端侧交互点验待可访问窗口；证据见 docs/evidence/p6/README.md。远端 Issue 均仍开放，未自动关闭。

以下序号是本地草案号，GitHub 编号为链接所示；依赖正文是执行依据，不要把草案序号当作 Issue 编号。

P6 等待 P5 阶段验收是 PRD 明确的门禁。其余边只表示真正功能前置；共享接口、迁移编号和文件编辑仍应协调，不能据此假定可无冲突并行。

阻塞关系已经写入各 Issue 的 Blocked by 正文；原生阻塞或子任务关系未验证/建立。父 Issue 仅引用，正文、标签和关闭状态不改动。

1. **[#2 [P5] 按可用时段和价格查找具体场次](https://github.com/LikeToGe/sports-booking/issues/2)** — 阻塞：无，可开始。本地正文：[任务 01](issues/01-time-slot-search.md)。
2. **[#3 [P5] 维护场馆区县并按区县找空场](https://github.com/LikeToGe/sports-booking/issues/3)** — 阻塞：#2。本地正文：[任务 02](issues/02-venue-district-filter.md)。
3. **[#4 [P5] 收藏场馆和访问最近预约过的场馆](https://github.com/LikeToGe/sports-booking/issues/4)** — 阻塞：无，可开始。本地正文：[任务 03](issues/03-favourite-recent-venues.md)。
4. **[#5 [P5] 从本人历史预约再次预约](https://github.com/LikeToGe/sports-booking/issues/5)** — 阻塞：#2。本地正文：[任务 04](issues/04-book-again.md)。
5. **[#6 [P5] 按时间和场地比较具体场次](https://github.com/LikeToGe/sports-booking/issues/6)** — 阻塞：无，可开始。本地正文：[任务 05](issues/05-court-slot-matrix.md)。
6. **[#7 [P5] 按日期、水平和剩余名额寻找搭子](https://github.com/LikeToGe/sports-booking/issues/7)** — 阻塞：无，可开始。本地正文：[任务 06](issues/06-recruitment-discovery.md)。
7. **[#8 [P5] 验收 P5 订场与找搭子的完整路径](https://github.com/LikeToGe/sports-booking/issues/8)** — 阻塞：#2, #3, #4, #5, #6, #7。本地正文：[任务 07](issues/07-p5-release-gate.md)。
8. **[#9 [P6] 到场提醒直达本人预约并支持可靠 Mock 投递](https://github.com/LikeToGe/sports-booking/issues/9)** — 阻塞：#8。本地正文：[任务 08](issues/08-arrival-reminders.md)。
9. **[#10 [P6] 登记开售提醒并收到可查询的站内消息](https://github.com/LikeToGe/sports-booking/issues/10)** — 阻塞：#8。本地正文：[任务 09](issues/09-opening-subscriptions.md)。
10. **[#11 [P6] 取消、超时和重新开放后发送空位提醒](https://github.com/LikeToGe/sports-booking/issues/11)** — 阻塞：#10。本地正文：[任务 10](issues/10-released-slot-alerts.md)。
11. **[#12 [P6] 按星期和小时查看预约利用率热力图](https://github.com/LikeToGe/sports-booking/issues/12)** — 阻塞：#8。本地正文：[任务 11](issues/11-utilization-heatmap.md)。
12. **[#13 [P6] 比较场地表现并下钻低利用时段](https://github.com/LikeToGe/sports-booking/issues/13)** — 阻塞：#12。本地正文：[任务 12](issues/12-court-performance-drilldown.md)。
13. **[#14 [P6] 查看顾客预约习惯和 30 天未再预约分组](https://github.com/LikeToGe/sports-booking/issues/14)** — 阻塞：#8。本地正文：[任务 13](issues/13-customer-segmentation.md)。
14. **[#15 [P6] 维护场馆顾客标签并控制经营编辑权限](https://github.com/LikeToGe/sports-booking/issues/15)** — 阻塞：#8。本地正文：[任务 14](issues/14-customer-tags-permissions.md)。
15. **[#16 [P6] 从顾客分组创建并处理回访待办](https://github.com/LikeToGe/sports-booking/issues/16)** — 阻塞：#14, #15。本地正文：[任务 15](issues/15-customer-followup-tasks.md)。
16. **[#17 [P6] 验收提醒回流和场馆经营完整闭环](https://github.com/LikeToGe/sports-booking/issues/17)** — 阻塞：#9, #11, #13, #16。本地正文：[任务 16](issues/16-p6-release-gate.md)。

覆盖检查：64 条用户故事及 A1-A18 全部覆盖，依赖无环。
当前可开始的 Issue：#2、#4、#6、#7。按用户本次要求，既有改动先提交推送；下个会话使用 implement 技能实现依赖已完成的任务。
