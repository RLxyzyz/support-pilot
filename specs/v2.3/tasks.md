# SupportPilot V2.3 · 任务清单

> 共享文件（schema、TicketService、types、client、App、Shell）串行修改。

## T1 数据层
- [x] 1.1 schema-mysql.sql 末尾追加 10 张新表 + ticket/ticket_message/draft 新列幂等迁移
- [x] 1.2 新增 10 个实体类（entity 包）
- [x] 1.3 新增对应 Repository（含所需 finder）
- [x] 1.4 Ticket / TicketMessage / Draft 实体补字段

## T2 公共
- [x] 2.1 NotifyType 增加 STORM_ALERT / SENTIMENT_ALERT / HEALTH_ALERT 与 LABELS
- [x] 2.2 TicketVO / TicketMessageVO 扩展字段

## T3 M1 相似归组
- [x] 3.1 SimilarityService（bigram Jaccard、检测、归组、批量回复、合并、配置）
- [x] 3.2 SimilarityController（4 接口）
- [x] 3.3 TicketService.create 挂载检测

## T4 M2 风暴
- [x] 4.1 StormService（定时扫描、升级/恢复/忽略）
- [x] 4.2 StormController

## T5 M3 评测
- [x] 5.1 EvalService（采纳统计、驳回原因校验、沉淀、运行、聚类）
- [x] 5.2 EvalController
- [x] 5.3 DraftService 接入驳回原因与版本字段

## T6 M4 L0
- [x] 6.1 L0Service（配置、自动应答、确认/重开/转人工、报表）
- [x] 6.2 L0Controller
- [x] 6.3 TicketService.create 异步 L0 尝试

## T7 M5 情绪
- [x] 7.1 SentimentService（词典分析、预警、升级、趋势）
- [x] 7.2 SentimentController / 并入 Ticket、Customer
- [x] 7.3 TicketService.addMessage 挂载

## T8 M6 摘要
- [x] 8.1 SummaryService + SummaryController
- [x] 8.2 TicketVO.summary 填充

## T9 M7 健康分
- [x] 9.1 HealthService（定时计算、预警、查询、配置）
- [x] 9.2 HealthController

## T10 前端
- [x] 10.1 types/index.ts 类型
- [x] 10.2 api/client.ts 方法
- [x] 10.3 TicketList 徽标/抽屉/风暴横幅
- [x] 10.4 TicketDetail 摘要/情绪/L0/相似
- [x] 10.5 CustomerDetail 健康分
- [x] 10.6 Board 采纳率 + L0 指标
- [x] 10.7 SmartSettings 页面 + 路由 + Shell 导航

## T11 验证
- [x] 11.1 后端编译 + 测试
- [x] 11.2 前端构建
- [x] 11.3 新接口冒烟
