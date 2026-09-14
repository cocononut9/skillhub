# 2026-09-11 上游合并

合并分支：`merge/upstream-20260911`。本地基线为 `ea707224`，上游为 `iflytek/skillhub` 的 `687097ad`（2026-09-10）。本次纳入上游 82 个提交。

保留四类资源（Skill、网站、插件、提示词）、需求广场、流程/岗位标签筛选、上传标签、使用统计移除及上传预检修复，同时纳入上游技能套件、CLI 0.1.12 和界面等更新。

## 冲突处理

- 12 个文件产生文本冲突，涉及资源详情接口、导航、卡片、搜索、翻译和生成类型。资源详情同时返回 `resourceType` 与 `entryForSuites`，导航同时保留需求广场与技能套件。
- 前端保留四类资源的 `ResourceType`，套件发现接口单独使用 `ResourceSearchType`；旧的 `SUITE` 搜索参数不会传入四类资源搜索接口。
- 套件候选和安装可用性只允许 `SKILL`，避免将网站、插件和提示词当成 Skill 安装。
- OpenAPI 类型通过合并后的 Spring 测试应用导出接口定义，再运行 `make generate-api` 生成；未手工拼接生成文件。

## 数据库迁移兼容

合并前只读核对了本地 `skillhub_integration_preview`：已执行 V49–V54。所有已执行脚本及校验和保持原样；未升级该数据库。

| 上游原编号 | 本分支编号 | 迁移 |
| --- | --- | --- |
| V49 | V55 | skill_suites |
| V50 | V56 | typed_review_subjects |
| V51 | V57 | skill_suite_install_operations |
| V52 | V58 | skill_suite_overview |
| V53 | V59 | skill_suite_version_optimistic_lock |

以上仅更改文件编号，SQL 内容与上游一致。既有定制版本数据库通过追加 V55–V59 升级，不使用 Flyway repair 或改写历史校验和。已经采用上游 V49–V53 编号的数据库不能直接切换到本分支。

`ResourceDemandMigrationTest` 在隔离 PostgreSQL 测试容器内验证 V51 → V53 → V54 → V59、重复启动幂等，以及历史校验和、资源、需求和标签关联保留。全新数据库由套件和需求集成测试覆盖。

## 验证方式与边界

- 后端：`make test-backend-app`。
- 前端：在 `web` 执行 `pnpm test`、`pnpm run typecheck`、`pnpm run lint`、`pnpm run build`。
- CLI：在 `cli` 执行 `bun run test`、`bun run typecheck`、`bun run lint`、`bun run build`。
- 标签页面：在 `web` 执行 `pnpm exec playwright test --config playwright.label-filters.config.ts`，使用模拟 API，不写真实业务数据。
- OpenAPI 导出：后端测试附加 `-Dskillhub.openapi.output=/tmp/skillhub-upstream-openapi.json` 并运行 `MergedApiContractTest`；随后 `make generate-api OPENAPI_SOURCE=/tmp/skillhub-upstream-openapi.json`。

合并在独立工作目录完成，原目录、运行中的预览及其数据库未切换。本次不包含生产部署或真实业务验收；容器部署回归需要独立端口和数据库后另行执行。

## 本次验证结果

- 后端共 1,815 项：1,814 通过，1 项 Redis 集群测试因未配置专用环境跳过，无失败。
- 前端 813 项通过，类型检查、lint、生产构建通过；构建仍提示主包大于 500KB。
- CLI 524 项通过，类型检查、lint、构建通过。
- Chrome 无界面浏览器测试 2 项通过，覆盖桌面/移动端标签交集筛选、刷新、收藏筛选和后台分类编辑。使用独立临时浏览器配置与模拟 API。
- V49–V54 与基线逐文件比较一致；顺延的 V55–V59 与对应上游 SQL 内容逐字一致。
- 初次回归发现并修正了搜索接口旧断言、前端重复导入及旧 Suite 查询参数兼容；以上数量来自修正后的复测。
