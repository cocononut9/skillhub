# 标签分类筛选（第一版）

资源发现页用「流程标签」「岗位标签」两行按钮筛选，每行单选，可选「全部」或再次点击已选标签取消。两行、关键词、命名空间、资源类型、收藏之间取交集；切换条件回到第一页。排序、翻页、刷新、分享链接保留条件。无结果时可清除标签筛选。

第一版仍是平面标签，不提供查找方式切换或流程层级；这两项留到第二版。

## 首批标签

- 流程：市场洞察、产品研发、成品采购、品质质检、跨境物流、海外仓储、品牌营销、平台运营、多渠道发货、客户服务。
- 岗位：品牌专员、产品经理、采购专员、质检专员、物流专员、仓储专员、平台运营专员、红人运营、社媒运营、客服专员。

一个资源可同时挂载多个流程和岗位标签。上传时分组多选，资源详情页可继续维护挂载。管理员维护标签目录及分类归属。新增标签不会自动挂载到现有资源，需依据实际适用范围补充关联。

## 数据与兼容

- `label_definition.category` 为 `WORKFLOW`、`ROLE` 或 `GENERAL`，与 `RECOMMENDED / PRIVILEGED` 权限类型相互独立。
- V54 在 V53 上增加分类并初始化目录；V50、V51 等已应用迁移保持不变。
- 迁移复用中文名称精确匹配的旧标签，保留 slug、权限、显示设置及资源关联。例如已有「品牌专员」即使 slug 为 `influencer-screening`，仍沿用原标识。
- 无法判断归属的旧标签默认 `GENERAL`，在「其他标签」中继续展示，由管理员调整。
- 新建请求省略 category 时默认 GENERAL；旧客户端更新省略 category 时保留原分类。
- 旧 `?label=...` 链接保持可用。已识别的岗位或流程标签在相应行选中；隐藏、已删除或无法加载的已选标签仍可取消。
- 搜索 API 新增可选 `labelMode=ALL`，使用重复的 `label` 参数要求同时匹配全部标签。缺省仍为 ANY，保持原多标签接口语义；过滤在数据库计数和分页前执行。
- 收藏 API 支持 `include=labels`；发现页沿用现有收藏列表读取方式，读取标签后先做组合筛选，再计算数量和分页。

## 本地验证

```bash
make test-backend-app
make test-frontend
make typecheck-web lint-web
cd web
pnpm exec playwright test -c playwright.label-filters.config.ts
```

Playwright 使用独立的 3104 端口，全部 API 由测试数据拦截，不向业务服务写入数据。若未安装 Playwright Chromium、但已安装 Chrome，可加 `PLAYWRIGHT_CHANNEL=chrome`。测试包含桌面、390px 手机布局、刷新恢复、收藏交集和后台分类修改。

`LabelCategoryMigrationTest` 使用临时 PostgreSQL 容器验证迁移、旧关联保留及实际搜索 SQL。该测试不会修改本机共享数据库。

为避免从其他分支运行中的后端生成错误类型，可先导出本分支测试环境的 OpenAPI：

```bash
JAVA_TOOL_OPTIONS=-Dskillhub.openapi.output=/tmp/skillhub-label-openapi.json make test-backend-app
make generate-api OPENAPI_SOURCE=/tmp/skillhub-label-openapi.json
```

上线前核对目标库迁移历史及标签目录，在获准的发布流程中应用 V54。回退应用版本时保留新增列与标签即可兼容旧版本，不要为回退功能删除已有标签或关联。

## 本次验证记录（2026-09-10）

- 后端测试：1,679 项通过；1 项 Redis 集群测试因未配置 `REDIS_CLUSTER_TEST_NODES` 跳过。包含迁移、真实 PostgreSQL 搜索、分类 API、收藏标签返回与权限回归。
- 前端单元测试：765 项通过；类型检查、ESLint、生产构建通过。
- 隔离浏览器测试：2 项通过，使用测试数据验证桌面/手机筛选、刷新恢复、收藏交集和后台分类保存。
- 未执行生产部署或共享预览库迁移，未批量修改真实资源的标签关联。
