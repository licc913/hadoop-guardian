# Hadoop Guardian Skills 安装清单

## 说明

- `hadoop-guardian` 是本地自定义 skill，不通过 `skills.sh` 安装。
- 其余 skill 根据 `find-skills` 与 `skills.sh` 结果整理。
- `github-skill-forge` 当前未确认到稳定公开安装页，先不写标准安装链接。

## 本地自定义 Skill

### hadoop-guardian

- 本地路径：`C:\Users\17393\.codex\skills\hadoop-guardian\SKILL.md`
- 用途：Hadoop Guardian 的主领域 skill，负责产品边界、故障模式、动作守卫、架构设计与数据库参考。

## 建议安装的 Skills

### 1. skill-creator

- 用途：持续维护和升级 `hadoop-guardian`
- 安装命令：`npx skills add https://github.com/anthropics/skills --skill skill-creator`
- 页面：<https://skills.sh/anthropics/skills/skill-creator>

### 2. frontend-design

- 用途：实现事件中心、诊断详情、审批页、复盘页等前端工作台
- 安装命令：`npx skills add https://github.com/anthropics/claude-plugins-official --skill frontend-design`
- 页面：<https://skills.sh/anthropics/claude-plugins-official/frontend-design>

### 3. interaction-design

- 用途：实现审批流、执行反馈、时间线、状态切换等交互细节
- 安装命令：`npx skills add https://github.com/wshobson/agents --skill interaction-design`
- 页面：<https://skills.sh/wshobson/agents/interaction-design>

### 4. vercel-react-best-practices

- 用途：如果前端使用 React/Next.js，用于组件组织、数据获取和性能实践
- 安装命令：`npx skills add https://github.com/vercel-labs/agent-skills --skill vercel-react-best-practices`
- 页面：<https://skills.sh/vercel-labs/agent-skills/vercel-react-best-practices>

### 5. web-design-guidelines

- 用途：前端完成后做 UI/UX 与可用性检查
- 安装命令：`npx skills add https://github.com/vercel-labs/agent-skills --skill web-design-guidelines`
- 页面：<https://skills.sh/vercel-labs/agent-skills/web-design-guidelines>

### 6. find-skills

- 用途：后续补充搜索其它通用 skill
- 安装命令：`npx skills add https://github.com/evgyur/find-skills --skill find-skills`
- 页面：<https://skills.sh/evgyur/find-skills/find-skills>

### 7. ui-ux-pro-max

- 用途：增强企业后台和复杂页面的 UI/UX 设计能力
- 安装命令：`npx skills add nextlevelbuilder/ui-ux-pro-max-skill --skill ui-ux-pro-max`
- 页面：<https://skills.sh/nextlevelbuilder/ui-ux-pro-max-skill>

## 用途分组

- 工程设计：`hadoop-guardian`、`skill-creator`
- 前端设计开发：`frontend-design`、`interaction-design`、`ui-ux-pro-max`
- React 前端实现：`vercel-react-best-practices`
- 前端评审：`web-design-guidelines`
- 技能发现与补充：`find-skills`

## 最小必装组合

- `hadoop-guardian`
- `skill-creator`
- `frontend-design`
- `interaction-design`
- `vercel-react-best-practices`

## 备注

- `github-skill-forge` 建议继续使用当前环境中的本地版本。
- 如果后续确认其公开仓库地址，再补标准安装命令和页面链接。
