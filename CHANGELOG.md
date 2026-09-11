# Changelog

本项目所有重要更改都会记录在此文件。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.0] - 2026-09-11

### ✨ 新增
- 群友绑定：群友可将 QQ 账号与游戏 ID 自助绑定，绑定后自动写入服务器白名单（Java 版 `/mc bind`，基岩版 `/mc geyserbind`）。
- 基岩版兼容：兼容 Geyser + Floodgate，玩家名带/不带前缀两种写法都可正确绑定；前缀与是否补齐可在 `binding.geyser` 中配置。
- 解绑：`/mc unbind` 只移除由绑定写入的白名单条目，不会误删管理员手工添加的同名条目。
- 绑定 API：新增 `POST /api/v1/bindings`、`POST /api/v1/bindings/unbind`、`GET /api/v1/bindings/lookup`、`GET /api/v1/bindings`，并新增能力位 `binding.v1` 供客户端探测。

### ⚙️ 变更
- **加载器改为 NeoForge 26.2**：本仓库现在只提供 NeoForge 26.2 版本（Minecraft 26.2，需 JDK 25）；Forge 1.20.1 版本保留在 [AstrBotAdapter_Forge](https://github.com/OMSociety/AstrBotAdapter_Forge)。Mod ID 与 Java 包名不变，配置文件键名与格式一致，可从 Forge 版直接迁移。
- 仓库结构：平台无关代码集中在 `common/`，NeoForge 专属层在 `src/`，构建时一起编译，共用代码保持单一副本。
- 老版本配置文件在加载时会自动补齐新增的 `binding` 配置节（保持原有设置不变）。

### 🐛 修复
- 修复配置文件缺少新增配置节时新功能静默不可用的问题：现在会按默认模板补齐缺失的顶层配置节并写回。
