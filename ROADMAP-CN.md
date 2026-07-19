# CarbonGate 迭代路线图

CarbonGate 是一个轻量安全骨干，安装适配器和企业组件全部按需选择。后续工作必须
减少安装或接入成本，不能把可选能力重新塞回 Core。

## 产品约束

1. **Core 必须保持小。** 包管理器逻辑、厂商 SDK、DLP 规则全集和容器运行时都不
   进入 Core JAR。
2. **适配器只转换，不重复实现。** Skill、npm、Homebrew 和宿主适配器调用同一套
   CLI，并消费同一份 Release 契约。
3. **安装行为必须明确。** 不在 `postinstall` 下载，不默认启动守护进程，不静默修改
   宿主；下载的 Release 必须校验 SHA-256。
4. **企业深度能力保持可选。** Pack、Provider、Sandbox 由管理员按需安装，使用独立
   进程或独立包。
5. **一次迭代只推进一个边界。** 每个阶段都带独立测试，不依赖后续阶段才能交付。

## 交付计划

| 阶段 | 交付内容 | 边界 | 状态 |
|---|---|---|---|
| D0 | macOS/Linux/Windows 原生包与 Java 21 便携包 | 仅 Release 资产 | v0.3.0 已完成 |
| D1 | 跨平台引导安装器和统一 Release 资产契约 | Shell/PowerShell 分发适配层 | Unreleased 已完成 |
| D2 | `@carbongate/cli` | 零运行时依赖 npm 适配器；显式 `setup`，`postinstall` 不联网 | Unreleased 已完成 |
| D3 | `soya7700/homebrew-tap` | 根据同一契约和校验值生成 Formula | 已规划 |
| D4 | 通用 CLI 宿主适配套件 | 面向 Codex、Claude Code、OpenClaw、Qoder、WorkBuddy、扣子和兼容 MCP 宿主的声明式描述符 | 已规划 |
| E1 | Pack 编写与校验工具 | 只允许声明式规则，不执行 Pack 代码 | 已规划 |
| E2 | Provider SDK 与组件目录 | 进程外 DLP、审批和审计模块 | 已规划 |
| E3 | Sandbox Profile | 可选 Docker/Podman 及后续隔离 Provider | 已规划 |
| E4 | 企业运维能力 | 签名组件仓库、策略分发、可观测和高可用网关适配 | 已规划 |

## 阶段验收门槛

### D1：引导安装器与 Release 契约

- Apple Silicon macOS、x64 Linux 和 x64 Windows 都能一条命令安装原生 CLI，
  用户不需要 Java。
- 引导安装器只下载用户明确选择的 CarbonGate Release，校验 `.sha256` 后调用包内
  已有安装器。
- Release 资产名只在一份纯数据清单中定义，并由测试与打包自动化保持一致。
- 默认只接受 HTTPS；离线企业环境和测试必须显式开启 `file://` 镜像能力。

### D2：npm 适配器

- 公共包名固定为 `@carbongate/cli`，使用 npm 的 `carbongate` scope。
- 只使用 Node 内置模块，安装 npm 包时不下载其他程序。
- `npx @carbongate/cli setup` 由用户显式触发，选择并校验 GitHub Release，再委托
  同一套 CarbonGate CLI。
- npm 发布必须手动执行，并与同版本 GitHub Release 对齐。

### D3：Homebrew 适配器

- 公开 Tap 仓库固定为 `soya7700/homebrew-tap`。
- Formula 使用公开的原生资产和固定 SHA-256，不以其他 Java 基线重新构建项目。
- Formula 更新由 Release 契约生成，经过评审和审计后提交到 Tap 仓库。

### D4 与企业阶段

- 宿主兼容优先使用声明式描述符；确实需要的宿主专用代码也必须留在 Core 外部。
- 企业组件继续实现已有组件协议和 Guard Pipeline，不能成为 Core 的传递依赖。
- 自定义规则池遵循 Pack Schema、大小上限、稳定 ID、测试样例和明确的个人/企业分类。

版本继续遵循语义化版本。一个阶段可以跨越多个补丁版本或次版本；是否完成以验收
门槛为准，不预先绑定某个版本号。
