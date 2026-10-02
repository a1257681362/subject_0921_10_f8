# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

> 交付副本约定路径为根目录 `..\..\docs\schema\evops.sql`（即 `/docs/schema/evops.sql`）。
> 当前环境根目录不可写，副本放在工作区内 `docs/schema/evops.sql`，与 `src/main/resources/schema.sql` 保持一致。

## 考试机构考场与准考证建档工作区（exam-1）

覆盖四步：**报名 → 编排 → 发证 → 查档**。所有接口在 Shiro 保护下，使用 HTTP Basic 认证：
`curl -u bootstrap:bootstrap ...`；经办人通过请求头 `X-Operator` 记录（缺省 `system`）。

> cert-1 起新增**用人单位专用账号** `employer/employer`，只能访问对外验真窗口 `/api/cert-verify`；
> 用它访问其余 `/api/**` 返回 403。内部管理仍用 `bootstrap/bootstrap`（角色 `system`）。

约定：
- 所有响应统一为 `{success, code, message, data}`；失败返回可读状态码与中文原因（含 401/404），不返回空白。
- **批量动作分两步**：先调 `*/preview` 看影响范围（可处理/跳过逐条列出），确认后再调执行接口。
- **阶段结果**（`t_stage_result`）与**最终结论**（`t_final_conclusion`）分表保存。
- **版本可追溯**：快照只追加；版本选择只切当前标记；回退按快照还原并生成新的 ROLLBACK 版本，原版本与事件链（CREATE/SELECT/ROLLBACK）都保留。

### 1. 建档：考点 / 考场 / 批次

- `POST /api/sites`、`GET /api/sites`：考点（编码、名称、位置、联系人）。
- `POST /api/rooms`、`GET /api/rooms?siteId=`：考场（考场号、楼栋、楼层、**容量**、位置描述）。
- `POST /api/batches`、`GET /api/batches`：考试批次（归属考点、科目、考试时间）。

### 2. 报名（名册挂进批次，支持勘误留痕）

- `POST /api/registrations/import/preview` → `POST /api/registrations/import`：批量导入，报名号撞号**整批拒收**。
- `POST /api/registrations/{id}/corrections`：勘误。旧值写入 `t_registration_correction`，不抹历史；主表记新值并累加更正次数。
- `GET /api/registrations/{id}/corrections`：查更正痕迹。
- `GET /api/registrations/search/by-name?name=`：按姓名查名册（包含匹配，重名全返回）。
- `GET /api/registrations?batchId=`：批次名册。

### 3. 编排（按批次生成准考证，一人一条）

- `POST /api/arrange/preview` → `POST /api/arrange`：
  按**同校混编算法**排座（同校按容量比例摊到多个考场、考场内交错入座），证号规则 `ZK + 批次编码 + 4 位流水`。
  证号唯一、批次内（考场+座位）唯一，**撞号直接拒收**；容量不足整批拒收、不落半条；已编排批次不允许重复编排（要改走整批重排）。
- 报名导入可带 `school`（所属学校）；未填学校按"未知"处理，不参与同校相邻判定。学校字段可勘误（fieldName=`SCHOOL`）。

### 4. 发证（打证、发放两步，各留经办与钟点）

- 打证：`POST /api/tickets/print/preview` → `POST /api/tickets/print`（PENDING→PRINTED，记 printed_by/printed_at）。
- 发放：`POST /api/tickets/issue/preview` → `POST /api/tickets/issue`（PRINTED→ISSUED，记 issued_by/issued_at）。
- 目标可用 `{"batchId":...}` 整批，或 `{"ticketNos":[...]}` 指定；状态不能跳步，重复操作逐条跳过并给原因。
- 证书（证面）打印不在本版范围，下版再做。

### 5. 查档（证号、考场、座位一人一条）

- `GET /api/query/by-ticket-no?ticketNo=`：按证号精确查。
- `GET /api/query/by-name?name=`：按姓名查（含重名）。
- `GET /api/query/by-batch?batchId=`：按批次列全部。
- 注意：查询参数含中文时需做 URL 编码（`curl -G --data-urlencode "name=张三"`）。

### 6. 阶段结果 / 最终结论

- `GET  /api/batches/{id}/progress/stages`：各阶段结果。
- `POST /api/batches/{id}/progress/conclusion`、`GET .../conclusion`：生成 / 查看最终结论（每批次一条）。
- `GET  /api/batches/{id}/progress/overview`：阶段结果与最终结论作为两个字段分开返回。

### 7. 工作区版本（版本选择 / 状态回退可追溯）

- `POST /api/workspace/versions?label=&remark=`：创建快照（自动成为当前版本）。
- `GET  /api/workspace/versions`、`GET /api/workspace/versions/current`、`GET /api/workspace/versions/{no}`。
- `POST /api/workspace/versions/{no}/select`：版本选择（只切当前标记，业务数据不变）。
- `POST /api/workspace/versions/{no}/rollback?remark=`：按该版本快照还原，生成新的 ROLLBACK 版本，原版本保留。
- `GET  /api/workspace/versions/events?versionNo=`：CREATE/SELECT/ROLLBACK 事件链。

## 出分等级与成绩复核工作区（grade-1）

把"出分定级"和"成绩复核"两段在 evops-base 串成一条线：**卷面分按分数段落等级、规则带版本 → 窗口跟批次配置 → 窗口期提复核 → 结论回写通知、改分留痕**。两个岗共用同一套规则版本与窗口口径，不再打架。本批次先跑助考项目（`projectCode=ZHUKAO`）。

### 8. 批次项目与复核窗口（不写死）

- 建批次 `POST /api/batches` 可带 `projectCode`（缺省 `DEFAULT`）与 `reviewStartAt` / `reviewEndAt`。
- `POST /api/batches/{id}/review-window`：单独配置 / 调整窗口（起止必须成对、起早于止）。
- `GET  /api/batches/{id}/review-window`：窗口起止与当前状态 `NOT_CONFIGURED / NOT_OPENED / OPEN / CLOSED`，附各状态复核计数。

### 9. 等级规则模块（规则 + 版本 + 状态）

- `POST /api/grade-rules`、`GET /api/grade-rules?projectCode=`：规则按项目建档（可带 `subject` 按科目细分，留空为项目通用），`maxScore` 为满分。
- `POST /api/grade-rules/{ruleId}/versions`：建草稿版本，提交分数段 `bands:[{minScore,maxScore,grade}]`（左闭右开；段段相连不重叠不缺口；首段从 0 起、末段到满分止，否则拒收）。
- `GET  /api/grade-rules/{ruleId}/versions`、`GET /api/grade-rules/versions/{versionId}`。
- `POST /api/grade-rules/versions/{versionId}/publish`：**DRAFT→ACTIVE**，发布即冻结分数段，并自动把同规则旧生效版置 **DISABLED**（同规则同时刻仅一版生效）。
- `POST /api/grade-rules/versions/{versionId}/disable`：人工停用生效版。
- `POST /api/grade-rules/versions/{versionId}/trial`：`{"score":72}` 按版本试算等级，不写成绩。
- 定级只认项目当前 ACTIVE 版本（优先科目精确匹配，其次通用规则）；成绩行永久记下 `ruleVersionNo`，规则以后升级不改变历史口径。

### 10. 成绩录入与定级

- `POST /api/scores/import/preview` → `POST /api/scores/import`：按批次批量录卷面分（按报名号定位名册，须属于本批次）。预检逐条列可录入/跳过与定级结果、规则版本；执行时任一不通过**整批拒收、不落半条**，已录成绩不允许重复导入（改分必须走复核）。
- `GET  /api/scores/batch/{batchId}`、`GET /api/scores/{id}`。
- `GET  /api/scores/change-logs?batchId=`、`GET /api/scores/{id}/change-logs`：改分前后留痕（只追加）。

### 11. 成绩复核（窗口校验 / 超窗自动关闭 / 结论回写通知）

- `POST /api/reviews/apply`：`{"scoreId":...}` 或 `{"batchId":...,"regNo":...}` 提交复核。仅窗口 **OPEN** 可提交；未配置 / 未开始 / 已关闭分别返回可读原因（`REVIEW_WINDOW_*`）；同一成绩处理中不可重复提交；成绩置 `REVIEWING`。
- `POST /api/reviews/{id}/conclusion`：`{"approved":true,"newScore":85,"conclusion":"..."}` 复核通过——按**原定级规则版本**重算等级、改分，前后分数/等级写入复核单和改分日志，并发站内通知；`{"approved":false,"conclusion":"..."}` 驳回维持原分，同样通知。
- 超窗自动关闭：窗口（批次配置的 `reviewEndAt`）过后仍 `SUBMITTED` 的申请，定时任务（每分钟，`evops.review.close-cron` 可调）自动置 `CLOSED`、成绩解锁、通知学员；也可 `POST /api/reviews/close-expired` 手动立即执行。
- `GET /api/reviews?status=`、`GET /api/reviews/batch/{batchId}?status=`、`GET /api/reviews/{id}`。
- 通知：`GET /api/notifications?regNo=&unreadOnly=`、`POST /api/notifications/{id}/read`（本版站内落库，短信等外发渠道下版再接）。

## 同校混编整改工作区（mix-1）

针对"同校学员相邻而坐、抄袭"的监考通报：在现有编排上加同校错开约束，同校按比例摊到多个考场，座次表可打印，重排整批进行不留半新半旧，并给出同校相邻统计供巡考抽查。种子与座次由 **evops-base** 落库保存。

### 12. 名册学校信息

- 报名导入 / 单项中 `school` 为所属学校（可空，空按未知处理，不计同校相邻）；`GET /api/registrations?batchId=` 返回含 `school`。
- 学校可勘误：`POST /api/registrations/{id}/corrections`，`fieldName=SCHOOL`。

### 13. 混编算法模块（模块 + 版本 + 状态）

与等级规则同一套版本模式：算法按项目建档（`projectCode`，缺省 `DEFAULT`），版本 **DRAFT→ACTIVE→DISABLED**，发布即冻结参数 JSON，同模块同时刻仅一版 ACTIVE，停用不删、历史编排保留版本号。

- `POST /api/mix-algorithms`、`GET /api/mix-algorithms?projectCode=`、`GET /api/mix-algorithms/{id}`。
- `POST /api/mix-algorithms/{id}/versions`：建草稿，参数为
  `{"strategy":"SCHOOL_SPREAD","minSchoolGap":1,"spreadRooms":true,"maxSameSchoolPerRoom":null,"tries":40}`
  （间隔须 ≥1、tries 1~1000，非法整批拒收）；`GET .../versions`、`GET /api/mix-algorithms/versions/{versionId}`。
- `POST /api/mix-algorithms/versions/{versionId}/publish`（旧 ACTIVE 自动 DISABLED）、`POST .../disable`。
- 编排取批次所属项目的当前 ACTIVE 版本；项目无生效版本时用内置默认参数（结果中标 `BUILTIN_DEFAULT`）。

### 14. 编排 / 整批重排 / 抽查统计

- 首次编排仍为 `POST /api/arrange/preview` → `POST /api/arrange`，请求可带 `seed`（不传由系统取确定性种子）。预检与执行共用同一方案，返回 `mixVersionNo / seed / roomCount / sameSchoolAdjacentCount / adjacentPairs`。
- **整批重排**：`POST /api/arrange/rearrange/preview` → `POST /api/arrange/rearrange`。
  单一事务内删旧证、按新方案整批重建；要么全新要么整批回滚，**不留半新半旧**。
  证一旦打印 / 发放即锁定座次（`REARRANGE_TICKETS_USED`），防止纸面与座次不符。
- 编排记录（种子 + 座次由 evops-base 落 `t_arrangement_run`，只追加、重排只切 `is_current`）：
  `GET /api/arrange/runs?batchId=`（列表不带座次大字段）、`GET /api/arrange/runs/current?batchId=`、`GET /api/arrange/runs/{id}`（含座次快照与相邻明细 JSON）。
- 同校相邻口径：同一考场内座位号连续且两人同校；返回总对数与逐对明细（考场号、座位 A/B、学校、两人报名号姓名），供巡考抽查。

### 15. 座次表导出打印

- `GET /api/arrange/seating-chart?batchId=`：打印用 HTML，每个考场单独一页（浏览器直接打印 / 另存 PDF），含考场号、座位号、准考证号、报名号、姓名、学校与签到栏，抬头标注混编版本、种子与同校相邻对数。
- `GET /api/arrange/seating-chart.csv?batchId=`：同内容 CSV（UTF-8 BOM，Excel 直开）。
- 未编排批次导出返回 `ARRANGEMENT_RUN_NOT_FOUND`。

> 新表 `t_mix_algorithm / t_mix_algorithm_version / t_arrangement_run` 已纳入工作区快照与回退；schema 版本号 `mix-1`。

## 兼职监考聘任与结算工作区（appoint-1）

把"聘任和结算"做进 evops-base：**监考随排班生成聘任 → 场次结束签到计津贴（角色不同标准不同）→ 缺勤不计费 → 换人留替换记录 → 月底一键出结算单（人事看全量、本人看自己）→ 确认锁定、要改走调整**。兼职老师对费有据可查，不再和人事窗口各执一本账。聘任记录分**模块（主表）/ 版本（只追加）/ 状态（当前版本）**三段。

视图口径：所有接口仍走 Shiro HTTP Basic；请求头 **`X-Self-Code=<监考人员编码>`** 即"本人视图"（只返回本人聘任/版本/替换/结算明细），不传为人事全量视图；经办人仍由 `X-Operator` 记录。

### 16. 监考人员与津贴标准（角色不同标准不同）

- `POST /api/invigilators`、`GET /api/invigilators`：兼职监考人员（编码、姓名、默认角色 `CHIEF 主考 / INVIGILATOR 监考 / PATROL 巡考`、`staffKind`）。
- `POST /api/invigilators/allowance-rates`、`GET .../allowance-rates?projectCode=&roleCode=`：按**项目 + 角色 + 生效区间**配置单场津贴；区间重叠或到期缺标准在生成聘任时报 `RATE_AMBIGUOUS / RATE_NOT_CONFIGURED`。
- 津贴口径在**生成聘任（或换人接任）时冻结**进聘任版本（rateId/rateCode/金额），以后调价不改历史聘任。

### 17. 监考随排班生成聘任（两步预检）

- `POST /api/appointments/shifts/preview` → `POST /api/appointments/shifts`：请求 `{batchId, shiftStart?, shiftEnd?, assignments:[{roomId, roleCode?, invigilatorCode}]}`。
  按批次把监考排进考场角色场次，**一条在岗排班生成一条聘任**（`t_invigilation_shift` + `t_appointment` + 首版 CREATE `t_appointment_version`）。
  - 预检逐条列可生成 / 跳过与命中标准、金额；同考场可排多名同角色监考；同人同批次重复排班、同人多岗、考场不属于本考点等结构性问题**整批拒收**；已排班场次幂等跳过。
- 聘任号 `AP + 批次编码 + 4 位流水`；排班与聘任 1:1。

### 18. 聘任记录版本 / 状态（只追加）

- 状态机：**APPOINTED 已聘任 → CHECKED_IN 已签到（计费）**；旁路 **ABSENT 缺勤（不计费）/ REPLACED 已换人 / CANCELLED 已取消**。
- `POST /api/appointments/{id}/check-in`（可带 `signInAt`，缺省当前时间）：**场次结束签到后才计津贴**（billable=true），金额沿用聘任时冻结口径，结算周期按签到月归属。
- `POST /api/appointments/{id}/absent?reason=`：未签到记缺勤，**缺勤不计费**；已签到（已计费）不能改记缺勤。
- `POST /api/appointments/{id}/cancel?reason=`：场次取消，不计聘任计费（已签到计费的不能取消）。
- 每次状态变化**追加一版** `t_appointment_version`，主表只切当前版本号与状态；`GET /api/appointments/{id}/versions` 查完整版本链。

### 19. 换人（留替换记录）

- `POST /api/appointments/{id}/replace`：`{toInvigilatorCode, reason}`。
  一次换人：原持聘人追加 **REPLACED 版（不计本场费）**、接任者追加 **APPOINTED 新版**（按接任角色/场次日期重新冻结津贴），写一条 `t_appointment_replacement`（谁→谁、原因、经办、钟点），排班持聘人同步更新；接任者本批次已有在岗聘任则拒收。
- `GET /api/appointments/replacements?batchId=`、`GET /api/appointments/{id}/replacements`：替换记录（本人视图只返回涉及本人者）。

### 20. 月底一键结算单（人事全量 / 本人自己；确认锁定、调整留痕）

- `POST /api/settlements/preview` → `POST /api/settlements`：请求 `{month:"yyyy-MM"（缺省当月）}`。
  按**签到月**汇总区间内已签到（CHECKED_IN / billable）且未锁定的聘任，**一人一行**（场次、金额合计）。先预检看全量口径，确认生成 **DRAFT 草稿**；草稿可重复生成按最新签到重算。
- `POST /api/settlements/{id}/confirm`：**确认后锁定**——计费聘任置 `settled` 并挂结算单（之后不能再签到/换人/取消），已确认月份不能重新生成结算单。
- 查询：`GET /api/settlements[?month=]`；`GET /api/settlements/{id}`（人事返回全部明细，带 `X-Self-Code` 仅返回本人那一行，响应标 `view=HR_ALL/SELF`）；`GET /api/settlements/my-items[?month=]` 本人跨单只看自己；`GET /api/appointments[?batchId=&status=&invigilatorCode=]` 本人视图强制只看本人。
- **要改走调整**：`POST /api/settlements/{id}/adjustments` `{invigilatorCode, adjustAmount(可正可负), reason}`，只追加 `t_settlement_adjustment`，本人明细与结算单应付额累加变动；`GET /api/settlements/{id}/adjustments` 查台账。未确认草稿不接受调整（直接重算即可）。

> 新表 `t_invigilator / t_invigilator_allowance_rate / t_invigilation_shift / t_appointment / t_appointment_version / t_appointment_replacement / t_settlement / t_settlement_item / t_settlement_adjustment` 已纳入工作区快照与回退；schema 版本号 `appoint-1`。

## 保密室双人复核登记链工作区（vault-1）

把"双人双锁"从自觉变成系统约束：**进出刷证 → 第二人复核确认才算生效 → 在室清单实时可查 → 卷箱出入跟人员进出挂钩 → 异常时段自动标记给保卫**。登记写进去就不能改，纠错一律走补充说明（只追加）；三个月纸面存量两步导入。出入登记分**模块（主表）/ 版本（只追加）/ 状态（当前版本）**三段，与聘任记录同一套版本模式。

### 21. 准入人员 / 卷箱 / 允许进出时段

- `POST /api/vault/persons`、`GET /api/vault/persons?status=`：准入人员（编码、姓名、**刷证卡号**、职务）；`POST /api/vault/persons/{id}/disable|enable`：停用后不能登记也不能当复核人，历史登记保留。
- `POST /api/vault/boxes`、`GET /api/vault/boxes`：卷箱建档（箱号唯一），箱号先建档再登记出入。
- `GET /api/vault/access-window`、`POST /api/vault/access-window`：允许进出时段（`{"start":"07:00","end":"19:00"}`，起止成对、起早于止；缺省 07:00–19:00）。落在窗口外的登记自动标记 `abnormal=true`。

### 22. 出入登记模块（刷证 → 待复核 → 第二人确认生效）

- `POST /api/vault/access/swipe`：刷证登记 `{"cardNo":"CARD-001"（或 personCode）,"direction":"IN|OUT","occurredAt":"可空缺省当前","boxes":["BX-001"],"remark":""}`，落库 **PENDING 待复核**，不生效、不计入在室清单；同人同向同钟点防重（`ACCESS_DUPLICATE`），未来时间拒收。
- `POST /api/vault/access/{id}/review`：`{"reviewerCode":"K002","approved":true,"comment":""}` 第二人复核——复核人须为**登记者本人以外**的在册启用人员（`REVIEWER_SAME_AS_PERSON`），确认才 **CONFIRMED 生效**；`approved=false` 驳回必须写意见，**REJECTED 永不生效**；已复核的登记不能再复核。
- 状态机：**PENDING 待复核 → CONFIRMED 已生效 / REJECTED 已驳回**；每次流转追加一版 `t_vault_access_version`（CREATE/CONFIRM/REJECT/IMPORT），主表只切当前版本号与状态。
- `GET /api/vault/access?status=&direction=&personCode=&from=&to=`、`GET /api/vault/access/{id}`、`GET /api/vault/access/{id}/versions`（版本链）、`GET /api/vault/access/{id}/boxes`（挂钩卷箱明细）。

### 23. 在室清单（实时可查）

- `GET /api/vault/access/in-room`：在室人员——只按**已生效**登记推导，每人最后一条生效方向为 IN 即在室，带进室时间与登记号。
- `GET /api/vault/boxes/in-room`：在室卷箱——按已生效登记挂钩的卷箱明细推导，带经手人（谁带进来的）。

### 24. 卷箱出入跟人员进出挂钩

- 卷箱不能单独登记出入，必须挂在一条人员出入登记上（`boxes:[...]`），方向与人一致，随登记复核生效。
- 位置校验按已生效台账：带进要求箱当前不在室（`BOX_ALREADY_IN_ROOM`），带出要求箱当前在室（`BOX_NOT_IN_ROOM`）；刷证时校一次，**确认生效前再校一次**（等待期间台账可能已变）。

### 25. 异常时段自动标记给保卫

- 发生时间落在允许窗口外的登记自动 `abnormal=true`；**生效时**（复核确认或导入生效）自动生成 `OFF_HOURS` 告警给保卫，未生效前不产生告警。
- `GET /api/vault/alerts?status=NEW|ACKED`：保卫查看；`POST /api/vault/alerts/{id}/ack?note=`：签收留痕（签收人取 `X-Operator`），重复签收拒收。

### 26. 登记不可改，纠错走补充说明

- 登记内容（人/方向/钟点/卷箱）写入后不改，没有更新/删除接口；纠错一律 `POST /api/vault/access/{id}/notes` `{"noteType":"CORRECTION|SUPPLEMENT","content":"..."}`，只追加、不覆盖历史；`GET /api/vault/access/{id}/notes` 查痕迹。

### 27. 三个月纸面存量导入（两步）

- `POST /api/vault/access/import/preview` → `POST /api/vault/access/import`：条目 `{personCode, direction, occurredAt:"yyyy-MM-dd HH:mm", boxes?, remark?}`。
- 预检逐条列可导入/跳过；人员或卷箱未建档、方向/时间非法、未来时间、文件内重复等结构性问题**整批拒收、不落半条**；与库内同键（人+方向+钟点）的逐条幂等跳过，重复导入安全。
- 历史既成事实：落库即 **CONFIRMED**（版本 IMPORT，单版生效），卷箱位置按纸面原样记录；异常时段同样自动标记并告警保卫。

> 新表 `t_vault_person / t_vault_box / t_vault_access / t_vault_access_version / t_vault_box_move / t_vault_access_note / t_vault_alert / t_vault_config` 已纳入工作区快照与回退；schema 版本号 `vault-1`。

## 缺考裁定与成绩快照工作区（absence-1）

针对"同一个缺考、两个窗口两种答复"的投诉：缺考裁定收口到 evops-base 单一权威来源——**按签到和违纪记录自动标缺考 → 双人复核出最终裁定 → 裁定决定成绩有没有效 → 有效进合格名单、无效写原因衔接下次安排 → 统计只认最终裁定，裁定有变更就重算**。缺考标记分**模块（主表）/ 版本（只追加）/ 状态（当前版本）**三段，与聘任、出入登记同一套版本模式。

### 28. 考生签到（现场签到即生效；补录走审批）

- `POST /api/absence/checkins`：`{batchId, regNo, checkInAt?, remark?}` 现场签到，登记即生效（CONFIRMED）；一人一批次一条生效签到（`CHECKIN_EXISTS`）。
- `POST /api/absence/checkins/supplement`：`{batchId, regNo, checkInAt, reason}` 补录申请，落库 **PENDING 待审批**，不生效；已有生效签到或待审批申请时拒收。
- `POST /api/absence/checkins/{id}/approve`：`{approved, comment?}` 审批——审批人取 `X-Operator`，**须非补录提交人**（`SUPPLEMENT_SELF_APPROVE`）；批准才 CONFIRMED 生效，驳回必须写意见（REJECTED 永不生效）。批准生效前再校一次是否已有生效签到。
- **补录批准的连锁**：对该生已生效的缺考（ABSENT）无效裁定**自动发起变更**（回到待复核，拟恢复成绩有效，仍须双人复核确认）。
- `GET /api/absence/checkins?batchId=&status=&regNo=`、`GET /api/absence/checkins/{id}`。

### 29. 违纪记录（自动标缺考的输入）

- `POST /api/absence/violations`：`{batchId, regNo, violationType, description?, occurredAt?}` 直接登记；类型 `CHEAT 作弊 / IMPERSONATE 替考 / PROHIBITED 携带违禁品 / DISORDER 扰乱秩序 / OTHER 其他`。
- `GET /api/absence/violations?batchId=&regNo=`。

### 30. 缺考标记模块（自动扫描 + 手动标记，登记即待复核）

- `POST /api/absence/scan/preview` → `POST /api/absence/scan`：`{batchId}` 两步扫描——名册中**无生效签到**的自动拟标 `ABSENT 缺考`、**有违纪记录**的自动拟标 `VIOLATION 违纪`，均落 PENDING；已有在办/生效同类型标记的逐条跳过（幂等，重复扫描安全）。
- `POST /api/absence/marks`：`{batchId, regNo, markType, reason, nextArrangement?, nextBatchId?}` 手动标记——**弃考（ABANDONED）走这里**；缺考/弃考/违纪三类分清（`markType=ABSENT/ABANDONED/VIOLATION`）。
- 裁定号 `QK + 批次编码 + 4 位流水`；**一人一裁定**：同一学员仅一条在办/生效裁定（不限类型，`MARK_EXISTS`），要调整走复核或变更，从根上杜绝"两个窗口两种答复"。

### 31. 双人复核与最终裁定（结论决定成绩有效性）

- `POST /api/absence/marks/{id}/review`：`{approved, comment?, markType?, scoreValid?, invalidReason?, nextArrangement?, nextBatchId?}`——复核人取 `X-Operator`，**须非标记经办人**（`REVIEWER_SAME_AS_OPERATOR`）；可按需修正类型与拟定结论；驳回必须写意见。
- **确认生效（CONFIRMED 即最终裁定）**，同一事务内：回写成绩 `t_exam_score.valid` → 逐人逐笔写**成绩快照**（`t_score_snapshot`，前后状态只追加）→ **重算批次统计** → 站内通知本人（`ABSENCE_RULING`）。
- 结论为成绩无效时**必须写明原因并衔接下次安排**（`SCORE_INVALID_DETAIL_REQUIRED`）；确认缺考无效前若该生已有生效签到则拒收（`CHECKIN_EXISTS`，两个窗口同一口径）。
- **驳回**：新标记驳回即终结（REJECTED 永不生效）；变更轮驳回自动**恢复原裁定结论**（版本留痕）。

### 32. 变更裁定（已生效裁定回到待复核）

- `POST /api/absence/marks/{id}/change`：`{scoreValid, markType?, invalidReason?, nextArrangement?, nextBatchId?, reason}`——仅 CONFIRMED 可变更；可一并纠正类型（缺考/弃考分清）；变更回到 PENDING 再走双人复核，确认后成绩有效性翻转、写快照、**重算统计**、通知本人。
- `GET /api/absence/marks?batchId=&status=&markType=`、`GET /api/absence/marks/{id}`、`GET /api/absence/marks/{id}/versions`（版本链全程留痕）。

### 33. 合格名单 / 统计 / 成绩快照

- `GET /api/absence/pass-list?batchId=`：**成绩有效即进名单**（无生效的无效化裁定），每行带等级与规则版本，按等级汇总计数；合不合格由等级规则表达，裁定只管有效/无效。
- `GET /api/absence/stats?batchId=`：批次统计——报名/签到/补录待审/待复核标记/**缺考/弃考/违纪分类型计数**/成绩有效无效/名单人数/等级分布；**只认最终裁定**（每个标记最新一条 CONFIRMED 版本），每批次一行当前值，`generation` 随重算递增。
- `POST /api/absence/stats/recompute`：手动重算（平时由裁定确认、签到生效、补录批准自动重算）。
- `GET /api/absence/score-snapshots?batchId=&regNo=`：成绩快照——每笔裁定确认时该生成绩的前后状态（分数/等级/规则版本/有效标记 before→after、关联裁定号），只追加，两个窗口查同一份。
- 通知：`GET /api/notifications?regNo=` 可查裁定结论通知（与成绩复核通知同一处）。

> 新表 `t_examinee_checkin / t_violation_record / t_absence_mark / t_absence_mark_version / t_score_snapshot / t_absence_stat` 已纳入工作区快照与回退；`t_exam_score` 加列 `valid`（裁定生效回写，成绩导入时若已有生效无效裁定则落库即无效）；schema 版本号 `absence-1`。

## 证书编号与验真工作区（cert-1）

解决"编号手工排出重号、发出的证收不回、验真没有的事"：**编号规则收口（年度+批次+流水、末位校验码）→ 系统发号唯一 → 发证只读、作废留记录 → 对外凭编号+姓名验真回有效性与批次**。编号规则与等级规则、混编算法同一套**模块 / 版本 / 状态**三段式，规则调整只管新证。用人单位下周开始接入，先小流量。

视图口径：内部管理走 `bootstrap/bootstrap`；对外验真窗口走专用账号 `employer/employer`（只能访问 `/api/cert-verify`）。

### 34. 编号规则模块（模块 + 版本 + 状态）

- `POST /api/cert-rules`、`GET /api/cert-rules?projectCode=`、`GET /api/cert-rules/{id}`：规则按项目建档（缺省 `DEFAULT`，助考传 `ZHUKAO`）。
- `POST /api/cert-rules/{ruleId}/versions`：建草稿版本，提交发号参数
  `{"prefix":"ZS","yearDigits":4,"batchDigits":2,"seqDigits":5,"checkEnabled":true}`
  （前缀 1~6 位大写字母/数字；年度 2/4 位；批次 1~4 位；流水 3~8 位，非法拒收）。
  编号形态 = `前缀 + 年度 + 批次 + 流水 + 末位校验码`，如 `ZS20260100001X`。
- 末位校验码采用 **ISO 7064 MOD 11-2**（数字字母扩展取值，余数 10 记为 `X`），可机读挡住抄错一位与邻位颠倒。
- `GET .../versions`、`GET /api/cert-rules/versions/{versionId}`。
- `POST /api/cert-rules/versions/{versionId}/publish`：**DRAFT→ACTIVE**，发布即冻结发号参数，同规则旧生效版自动 **DISABLED**（同规则同时刻仅一版生效）；`POST .../disable` 人工停用。
- **规则调整只管新证**：每张证永久记下 `rule_version_no` 与冻结参数；规则升级、流水加宽后老证编号不变。

### 35. 发证（系统发号，两步预检；流水重起不撞旧号）

- `POST /api/certificates/issue/preview` → `POST /api/certificates/issue`：
  请求 `{projectCode?, batchId?, certYear?(缺省今年), certBatch?(可由批次编码数字部分推导), items:[{holderName,idNo?,subject?,grade?}]}`。
  - 发号按 `规则+年度+批次` 维度发流水，`t_cert_seq` **行锁取号**，并发不重号；并以**库内已用最大流水兜底起跳**（`MAX(current_seq, 已用最大流水)`），序列重起 / 手工旧号补登后都不撞旧号。
  - 预检逐条列可发 / 跳过（同年度批次同人跳过、清单内重复跳过）与**拟发编号**；执行实发编号逐条带回。
  - 流水段用尽报 `CERT_SEQ_EXHAUSTED`（不落半条），提示发布更宽流水的新规则版本。
- **手工旧号补登** `POST /api/certificates/legacy`：旧证（收不回的）原样登记 `cert_no`（不重发）；能辨认年度/批次/流水时自动把该序列抬过旧号，之后发号跳过旧号段。
- **发证后只读**：证书无修改 / 删除接口；持证人信息、编号、规则版本永久冻结。

### 36. 作废（留记录，不删除）

- `POST /api/certificates/{id}/revoke`、`POST /api/certificates/by-no/{certNo}/revoke`：`{reason}`（必填）。
  ISSUED→REVOKED，只切状态并**追加一条** `t_certificate_revoke_record`（原因、经办、钟点），原证保留、信息仍只读；重复作废报 `CERT_ALREADY_REVOKED`。
- `GET /api/certificates/{id}/revoke-records`：查作废留痕。
- 查询：`GET /api/certificates?batchId=&projectCode=&status=&holderName=`、`GET /api/certificates/{id}`、`GET /api/certificates/by-no/{certNo}`（内部视图，含留档字段）。

### 37. 对外验真窗口（凭编号+姓名，回有效性与批次，不带隐私）

- `GET /api/cert-verify?certNo=&name=`（专用账号 `employer/employer`）：
  - 只回**证面公开项**：`found / valid / status(ISSUED|REVOKED|NOT_FOUND) / message / certNo / holderName / batchCode / batchName / subject / grade / projectCode / issuedAt`，**不含证件号等隐私字段**（响应 DTO 无此类字段）。
  - 编号不存在、编号抄错、**姓名不匹配统一 `found=false`**，不暴露编号是否存在，防枚举探测。
  - **作废证给明确提示**：`valid=false,status=REVOKED`，message「该证书已作废，不再有效（作废原因：…）」，与"查无此证"清楚区分。
- **小流量灰度**：`GET /api/cert-verify-config`、`PUT /api/cert-verify-config/{key}?value=`（内部账号）。
  - `VERIFY_ENABLED`（true/false）窗口总开关，关闭返回 `CERT_VERIFY_DISABLED`；
  - `VERIFY_RATE_PER_MINUTE` 每分钟上限（缺省 60，0 不限），超限返回 `CERT_VERIFY_RATE_LIMITED`。下周接入先小流量，观察后再调大。

> 新表 `t_cert_number_rule / t_cert_number_rule_version / t_cert_seq / t_certificate / t_certificate_revoke_record / t_cert_verify_config` 已纳入工作区快照与回退；schema 版本号 `cert-1`。
