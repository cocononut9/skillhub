# 2026-09-14 上游合并

定制基线：`986254f7`，已先推送至 `origin/merge/upstream-20260911`。本次合入 `iflytek/skillhub` 的 `upstream/main`：`36de54157bff59c18c5eff255d2c158df45a3e2a`，相对上次 `687097ad` 新增 8 个提交。合并在独立目录 `skillhub-upstream-merge-20260914`、临时分支 `merge/upstream-20260914` 完成；验证后快进同步回正式集成分支 `merge/upstream-20260911`。

## 冲突与保留项

自动合并产生 3 处文本冲突：

- `web/src/app/layout.tsx`：保留已确认的首页、发布、需求广场、控制台、我的技能五项导航；首页继续包含搜索，不恢复单独搜索或套件顶栏。
- `web/e2e/theme-toggle.spec.ts`：保留旧 `/search` 重定向到 `/` 的验证和中文界面断言。
- `web/src/pages/landing.tsx`：纳入上游手机竖排布局与箭头旋转，同时保留容器宽度和溢出保护。

纳入上游控制台和用户菜单中的套件入口、手机布局修复、Vitest/js-yaml 等锁文件安全更新、MinIO 镜像来源切换到 Quay、staging 套件审核写入开关及第 37 周文档。新增控制台入口不改变已有顶栏。

自动合并的手机首页测试仍引用英文旧首页标题，已调整为当前中文首页；控制台回归也改为中文，并通过模拟账号验证，不再为导航测试创建真实账号。前端架构文档同步记录首页与旧搜索入口的当前关系。

与 `986254f7` 逐文件核对：全站配色、公司标志、资源标签行、标签请求参数、首页路由与搜索页代码保持不变。`server/` 和 `cli/` 无变化；数据库迁移文件无变化，未访问或修改数据库迁移历史。

## 验证

在新合并目录的 `web` 执行：

```sh
pnpm install --frozen-lockfile
pnpm test
pnpm run lint
pnpm build
PLAYWRIGHT_CHANNEL=chrome pnpm exec playwright test -c playwright.ui.config.ts
```

- 全部 211 个前端测试文件、834 项测试通过；完整 ESLint 与构建（含 TypeScript 编译）通过。
- 13 项浏览器回归通过：中文导航、首页搜索及旧链接、登录表单校验、匿名发布保护、桌面/手机标签筛选、收藏交集、后台分类编辑、深浅色切换和控制台套件入口。
- 浏览器为独立无头 Chrome；涉及登录角色和编辑的场景使用模拟接口，其余页面查询为只读，没有创建真实账号或写入业务数据。
- `git diff --check`、修改的安装 smoke 脚本 `bash -n`、开发 Compose 与开发+staging Compose 的 `config --quiet` 均通过。Compose 使用 `--env-file /dev/null`，未读取本地 `.env`。

后端和 CLI 没有本次改动，因此未重复运行它们的测试。未启动部署容器、未验证 MinIO 实际镜像拉取、未执行完整 staging 或生产部署。构建仍有项目既有的 runtime-config、静态/动态导入和大分包提示；单元测试有不影响通过的 jsdom navigation 提示。
