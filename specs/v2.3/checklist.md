# SupportPilot V2.3 · 验收清单

## 数据与公共
- [x] schema-mysql.sql 以 `CREATE TABLE IF NOT EXISTS` 幂等创建 10 张新表，新列通过 information_schema 动态 ALTER 补齐
- [x] Ticket / TicketMessage / Draft 新字段已映射实体，TicketVO / TicketMessageVO 已暴露新增字段
- [x] NotifyType 新增 STORM_ALERT / SENTIMENT_ALERT / HEALTH_ALERT 并有中文标签

## M1 相似归组
- [x] 建单后自动检测相似工单并按 bigram Jaccard 归组，列表返回相似数
- [x] 相似列表、批量回复（逐单事务）、合并、阈值配置接口可用
- [x] 冒烟：两条同标题工单返回 count=2、score=1.0，批量回复 sent=2

## M2 故障风暴
- [x] 每分钟扫描 30 分钟内同分类 ≥5 单，生成/升级/恢复/忽略风暴
- [x] 升级风暴创建事件单并关联组内工单（冒烟：#41316，related=7）
- [x] 恢复 / 忽略接口生效

## M3 AI 评测
- [x] 驳回原因限定枚举，「其他」必须填写备注（校验不通过返回 400）
- [x] 采纳率统计、用例沉淀、用例列表、低分聚类接口可用
- [x] 回归评测用例不足 30 条返回 400；达标时返回 total/passed/passRate/delta
- [x] Draft 审批/驳回写入 confidence、modelVersion、promptVersion、kbVersion

## M4 L0 自助应答
- [x] 开启 L0 必须显式 confirm=true，否则 400
- [x] 退款/赔偿/投诉/合同/法务等拦截词命中直接转人工，不自动回复
- [x] 自动应答工单带 72 小时确认 token，支持确认 / 重开（计次）/ 转人工
- [x] L0 报表返回分类维度的总量/确认/重开/转人工

## M5 情绪预警
- [x] 新消息写入后基于词典与程度副词计算情绪并落库
- [x] 负面强度 ≥0.8 或累计 ≥2 次触发告警并升级 P2（已为 P1/P2 不降级）
- [x] 客户情绪趋势按 90 天返回

## M6 会话摘要
- [x] 消息不足 10 条返回 insufficient=true，达到后生成五段式摘要
- [x] 人工修正保存保留原版，工单详情 VO 带回摘要

## M7 客户健康分
- [x] 每日 02:00 按频率/SLA/CSAT/情绪加权计算并落快照
- [x] 低于 60 分首次穿越或周降 ≥15 分触发告警
- [x] 健康分详情、趋势与权重配置接口可用

## 前端
- [x] 工单列表展示相似数 / AI 自助徽标与风暴横幅，支持查看相似组并批量回复
- [x] 工单详情含摘要、L0 状态、客户情绪、相似工单卡片及操作按钮
- [x] 客户详情展示健康分环图、分项与情绪趋势
- [x] 看板展示 L0 四项指标与分类采纳率
- [x] 智能服务配置页覆盖风暴处置、相似阈值、健康权重、L0 白名单、评测回归、低分聚类、L0 报表、用例列表
- [x] `/settings/smart` 路由与侧边栏入口可用

## 构建与运行
- [x] 后端 `mvn -DskipTests package` 与 `mvn test` 通过
- [x] 前端 `npm run build` 通过
- [x] 8081 实例完成接口冒烟（M1–M7 主链路）
