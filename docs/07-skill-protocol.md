# skillhub 技能包协议

## 8.1 OpenSkills 互操作边界

skillhub 的目标是客户端可互操作：skillhub CLI 安装的技能可以被 Claude Code / OpenSkills 兼容客户端发现和使用，反之亦然。

### 互操作层（skillhub CLI 必须兼容）

- SKILL.md 格式（frontmatter + markdown body）
- 技能包目录结构约定（SKILL.md + references/ + scripts/ + assets/）
- 四级目录优先级：`.agents/skills` → `~/.agents/skills` → `.claude/skills` → `~/.claude/skills`（与 OpenSkills/Claude 一致）。详见 §8.4。
- 目录名作为 lookup key：安装后的目录名等于 `skill.slug`（即 SKILL.md 的 `name` 字段），客户端通过目录名发现技能
- AGENTS.md `<skill>` 描述块格式：skillhub CLI 生成的 AGENTS.md 索引区块与 OpenSkills 格式兼容

### 服务端职责边界

- 服务端返回技能元数据（name, description, version），不返回 `location`
- `location` 是客户端本地安装路径，由 CLI 根据安装目录计算生成，写入 AGENTS.md
- 服务端不生成、不修改 AGENTS.md，这是客户端职责

### skillhub 私有扩展（不影响互操作）

- `<skills_system>` / `<available_skills>` 区块格式：skillhub CLI 可自定义，但必须保证 `<skill>` 节点格式与 OpenSkills 一致
- progressive disclosure（按需加载技能内容）：skillhub CLI 自行实现
- `.astron/metadata.json`：skillhub 私有元数据，其他客户端可忽略

## 8.2 SKILL.md 规范

服务端必须兼容的格式：

```yaml
---
name: my-skill              # 必需，kebab-case
description: When to use    # 必需，1-2 句话
---

# Markdown 正文（技能指令内容）
```

解析规则：
- `name` 和 `description` 为必需字段，缺失则校验失败
- `name` 映射为 `skill.slug`（首次发布时），后续版本不可变更
- `name` 和 `description` 作为没有根目录 `README.md` 时的展示信息回退值
- frontmatter 完整解析结果存入 `skill_version.parsed_metadata_json`

SkillHub 展示信息扩展：

```markdown
# 中文展示名称

> 一句话中文简介。
```

- 根目录存在 `README.md` 时，首个非空行必须是一级标题，并映射为 `skill.displayName`
- 一级标题后的首个非空行必须是引用，并映射为 `skill.summary`
- 展示名称和简介均不得超过 200 个字符
- `README.md` 存在但不符合以上格式时，发布校验失败
- 没有 `README.md` 的兼容技能继续使用 `SKILL.md` 的 `name` 和 `description`
- 展示信息不改变 `skill.slug`、安装坐标或技能包内的 `SKILL.md`

平台扩展字段（可选，`x-astron-` 前缀）：

```yaml
---
name: my-skill
description: When to use
x-astron-category: code-review
x-astron-runtime: claude-code        # 预留
x-astron-min-version: "1.0"          # 预留
x-astron-compliance:                 # 可选，平台私有合规元数据
  - standard: mitre-attack
    version: "v19.1"
    controlId: T1059
    title: Command and Scripting Interpreter
    evidence:
      - type: packaged-file
        path: references/standards.md
---
```

> 合规元数据先按 SkillHub/Astron 私有扩展实现，字段名采用 `x-astron-compliance`。
> 当前支持发布校验、版本级 `complianceSnapshot` 固化、详情展示、审核 diff 和轻量搜索投影。
> 这些信息表示“技能作者声明的合规映射”，SkillHub 校验证据引用的格式和可追溯性，
> 但不等同于第三方认证或平台背书。设计边界、分阶段实现和 Runtime 职责划分见
> [24-compliance-metadata-design.md](24-compliance-metadata-design.md)。

`x-astron-compliance` 的稳定字段如下：

| 字段 | 必填 | 说明 |
|------|------|------|
| `standard` | 是 | 合规标准、框架或知识库标识，例如 `mitre-attack`、`nist-csf`、`soc2` |
| `version` | 是 | 标准版本或适用版本，例如 `v19.1`、`2.0` |
| `controlId` | 是 | 控制项、技术编号或条款 ID，例如 `T1059`、`PR.AA-01` |
| `title` | 否 | 人类可读的控制项名称 |
| `evidence` | 否 | 证据列表，指向包内文件或外部 URL |

`evidence` 支持两类：

| `type` | 字段 | 说明 |
|--------|------|------|
| `packaged-file` | `path` | 指向技能包内的证据文件。路径必须在包内，不能路径逃逸。 |
| `external-url` | `url` | 指向外部证据材料。URL 必须使用允许的安全 scheme。 |

发布校验规则：

- 没有 `x-astron-compliance` 的旧技能继续正常发布。
- `x-astron-compliance` 存在时必须是数组。
- `standard`、`version`、`controlId` 必填。
- 同一技能版本内不允许重复 `standard + version + controlId`。
- `packaged-file.path` 必须存在于上传包内，且不能使用 `../` 等方式逃逸包目录。
- 合法合规声明会被规范化为版本级 `complianceSnapshot`，并生成稳定 `digest`。

Runtime 集成边界：

- SkillHub 是技能元数据和版本级 `complianceSnapshot` 的权威源。
- Agent Runtime 是执行 trace 的权威源。
- Runtime 如需在执行链路中记录合规上下文，应引用 SkillHub 返回的不可变版本 `id`
  和 `complianceSnapshot.digest`，而不是复制或改写 SkillHub 的声明内容。
- SkillHub 当前不记录 Agent 执行输入输出、Runtime trace 或实际调用结果。

## 8.3 技能包目录结构

```
my-skill/
├── SKILL.md              # 主入口文件（必需）
├── README.md             # SkillHub 展示文档（推荐）
├── references/           # 参考资料（可选）
├── scripts/              # 脚本（可选）
└── assets/               # 静态资源（可选）
```

校验规则：
- 根目录必须包含规范入口文件 `SKILL.md`；上传时服务端兼容 `skill.md`、`Skill.md` 等大小写变体，并在内部归一化为 `SKILL.md`
- 文件类型白名单：`.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.js`, `.cjs`, `.mjs`, `.ts`, `.py`, `.sh`, `.png`, `.jpg`, `.svg`
- 单文件大小限制：1MB（可配置）
- 总包大小限制：10MB（可配置）
- 文件数量限制：100 个（可配置）

## 8.4 客户端安装目录约定

skillhub CLI 遵循以下目录优先级，与 OpenSkills/Claude 保持互操作：

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1 | `./.agents/skills/` | 项目级，universal 模式 |
| 2 | `~/.agents/skills/` | 全局级，universal 模式 |
| 3 | `./.claude/skills/` | 项目级，Claude 默认 |
| 4 | `~/.claude/skills/` | 全局级，Claude 默认 |

安装后目录名等于 `skill.slug`（SKILL.md 的 `name` 字段），确保其他兼容客户端可通过目录名发现。

## 8.5 与 AGENTS.md 的关系

- skillhub CLI 安装技能后，通过 `sync` 命令在 AGENTS.md 中生成 `<skill>` 描述块
- `<skill>` 块包含 `name`、`description`、`location`（本地安装路径），格式与 OpenSkills 一致
- `location` 由 CLI 根据实际安装路径计算，不由服务端提供
- 服务端不直接生成或修改 AGENTS.md，这是客户端职责

## 8.6 客户端本地元数据文件（skillhub 私有实现）

以下为 skillhub CLI 的私有实现细节，不属于互操作协议的一部分。其他客户端可忽略此文件。

CLI 安装后在本地写入 `.astron/metadata.json`：

```json
{
  "source": "skillhub",
  "sourceType": "registry",
  "registryUrl": "https://skills.example.com",
  "namespace": "@ai-platform-team",
  "skillSlug": "code-review",
  "version": "1.2.0",
  "installedAt": "2026-03-11T10:00:00Z",
  "sha256": "abc123..."
}
```

## 8.7 版本解析规则

skillhub 自有 CLI 支持完整 namespace 坐标：

```
install @team/my-skill              → 最新已发布版本（实现上通常由 `latest_version_id` / published pointer 解析）
install @team/my-skill@1.2.0        → 精确版本
install @team/my-skill@latest        → 等同于不带版本号（系统保留标签，只读）
install @team/my-skill@beta          → beta 标签（自定义标签）
install my-skill                     → 等同于 @global/my-skill
```

ClawHub CLI 通过兼容层使用 canonical slug：

```
clawhub install my-skill             → @global/my-skill 的最新版本
clawhub install team-name--my-skill  → @team-name/my-skill 的最新版本
clawhub install my-skill@1.2.0       → @global/my-skill 的精确版本
```

## 8.8 坐标映射与 ClawHub CLI 兼容

skillhub 内部使用 `@{namespace_slug}/{skill_slug}` 坐标，ClawHub CLI 使用单一 slug。映射规则详见 `00-product-direction.md` 1.1 节。

安装后的本地目录名始终使用 `skill.slug`（不含 namespace 前缀），确保与 OpenSkills/Claude 兼容客户端的互操作性。

| skillhub 坐标 | ClawHub canonical slug | 本地安装目录名 |
|---|---|---|
| `@global/my-skill` | `my-skill` | `my-skill/` |
| `@team-name/my-skill` | `team-name--my-skill` | `my-skill/` |

注意：不同 namespace 下同名 skill 安装到本地时会产生目录冲突。skillhub CLI 应在安装时检测冲突并提示用户选择安装目录或使用别名。

## 网页资源包

网页资源沿用 ZIP 上传、命名空间权限和审核流程。仅收录已部署的网址，不托管网站源码，也不抓取目标网站。
ZIP 根目录只需 `README.md`，也支持一层包裹目录；网页包不能包含 `SKILL.md`。

```markdown
# 营销文案助手

> 输入产品卖点，生成营销文案。

资源类型：网页
使用入口：https://example.com/tool
资源标识：marketing-helper
版本：1.0.0

## 使用方法
使用公司账号登录后填写产品信息。
```

- 类型和网址必填，在第一个二级标题之前填写，不允许重复字段；标题和简介沿用 README 展示规范。
- 网址仅允许完整 HTTP/HTTPS 地址，不允许包含账号密码，最长 2048 字符；支持公司内网域名。
- 资源标识可选，缺省根据标题生成；更新同一网页时保持标识一致，建议首次提交就填写固定标识。
- 版本可选，缺省使用自动版本号。同一标识不能在 Skill 和网页之间改变类型。
- 网址保存在版本元数据中，审核通过后才显示“打开工具”；点击在新标签页访问网址。
- 网页资源不提供 Skill 安装命令，不出现在 CLI 技能搜索和同步列表中，也不能通过安装解析/下载接口取得技能包。
- 安全扫描仅检查提交的文档与附件，不验证链接网站的运行安全。扫描适配器会为临时副本添加 SKILL.md，以使用现有扫描器；存储的原始包不变。
- 原有 Skill 包及旧版本继续按照原流程发布和安装。
