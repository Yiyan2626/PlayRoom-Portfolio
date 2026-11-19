# PlayRoom 开发时间线

以下是从原工程提交和验收记录选出的里程碑。日期为原工作记录对应日期；本页随公开作品集于 2026-11-18 整理。项目每天做的事情不同，表中区分功能、验证和文档，没有把求职活动算作功能开发。

| 日期 | 工作 | 可以查看什么 |
| --- | --- | --- |
| 2026-10-11 | 建立 Java 核心结构并做出可玩的本地 Demo。 | [Demo 代码](../demo/model.mjs) |
| 2026-10-12 | 建立棋盘坐标、棋子记录和基础边界测试。 | [棋盘测试](../backend/playroom-core/src/test/java/dev/playroom/core/BoardTest.java) |
| 2026-10-13 | 实现水平、垂直和两条斜线的连续五子判定。 | [棋规与用例](../backend/playroom-core/src/test/java/dev/playroom/core/GomokuRulesTest.java) |
| 2026-10-14 | 增加房间、座位、加入与成员身份。 | [房间代码](../backend/playroom-core/src/main/java/dev/playroom/core/Room.java) |
| 2026-10-15 | 实现准备、轮流落子、终局和房间状态发布。 | [房间生命周期测试](../backend/playroom-core/src/test/java/dev/playroom/core/RoomLifecycleTest.java) |
| 2026-10-16 | 补足首周完整对局与失败边界的回归。 | [首周验收](../backend/playroom-core/src/test/java/dev/playroom/core/WeekOneAcceptanceTest.java) |
| 2026-10-17 | 增加 MySQL 存储结构与示例查询。 | 完整工程，当前公开架构说明 |
| 2026-10-19 | 接通登录会话和房间 HTTP API，服务端按会话账号识别玩家。 | 完整工程，当前公开架构说明 |
| 2026-10-21 | 落子持久化改为先提交数据库，再发布房间状态；加失败回滚验收。 | [状态发布边界](../backend/playroom-core/src/test/java/dev/playroom/core/PublicationBoundaryTest.java) |
| 2026-10-26 | 增加需要认证且校验成员权限的 WebSocket 快照通知。 | 完整工程，当前公开架构说明 |
| 2026-10-27 | 将真实双人网页接到房间、落子和快照。 | 完整工程；本仓库的 Demo 为独立模拟版 |
| 2026-10-28 | 补充并发、MySQL 事务失败与通知边界测试。 | 完整工程，当前公开架构说明 |
| 2026-10-30 | 增加内容绑定的请求编号和持久回执，重发复用结果。 | 完整工程，当前公开架构说明 |
| 2026-11-01 | 接通自动重连、认输和事务结算；验证重复请求边界。 | [终局状态用例](../backend/playroom-core/src/test/java/dev/playroom/core/GameStateTest.java) |
| 2026-11-02 | 加入历史与逐步回放，拆分只读复盘服务，接入网关与缓存。 | [规则证据](../backend/playroom-review-contract/src/main/java/dev/playroom/review/RuleEvidence.java) |
| 2026-11-04 | 加强只读工具参数、调用关联与超时检查。 | [响应读取上限测试](../backend/playroom-review-contract/src/test/java/dev/playroom/review/BoundedHttpTest.java) |
| 2026-11-06 | 验证复盘故障隔离、处理期限、同键合并和共享缓存。 | 完整工程，本地固定场景验收 |
| 2026-11-07 | 重跑复盘副本下线、熔断、恢复与缓存故障实验。 | 完整工程，本机多进程验收 |
| 2026-11-10 | 增加一步成五及防守分析、只读试落；接入异步赛后统计。 | [棋形分析测试](../backend/playroom-review-contract/src/test/java/dev/playroom/review/BoardAnalysisTest.java) |
| 2026-11-11 | 收尾并复验启动、发布、棋局回放和复盘流程。 | 完整工程，本机端到端验收 |
| 2026-11-16 | 再验 HTTP 会话、棋局访问权限和 WebSocket 连接撤销，整理请求路径。 | 完整工程，身份与授权回归 |
| 2026-11-18 | 建立公开作品集，验证可以独立运行的 Java 模块和浏览器 Demo。 | [源码与复跑命令](../README.md) |
| 2026-11-19 | 棋谱回放支持下载完整落子顺序。 | [导出实现](../demo/replay-export.mjs)、[回归用例](../demo/tests/model.test.mjs) |

后续在公开版 `main` 记录实际可分享的代码、测试和文档更新。里程碑表不会追填没有工作记录的日期。
