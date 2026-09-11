# Changelog

本项目所有重要更改都会记录在此文件。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.2.0] - 2026-09-12

> ⚠️ 本节 1.2.0 的发布资产已于同日**重新构建并覆盖**：初版存在下面最后一条「基岩版白名单」的缺陷——`fwhitelist` 名字解析失败时只在控制台留一行错、不抛异常也不改白名单，而绑定被当成成功上报，导致基岩玩家永远进不来。版本号不升，资产以最新构建为准。

### ✨ 新增
- 一个外部账号可**同时**持有 Java 版与基岩版两条绑定：两条白名单条目并存、互不覆盖。此前第二次绑定会撤掉前一条，导致同一个人的电脑版与手机版只能进一个。
- 绑定 API 增加 `kind`（`java` / `geyser`）维度：`lookup` 返回 `bindings[]` 完整视图与 `javaBound`/`geyserBound`；`unbind` 支持按 `kind` 定向解除，省略即清空该账号全部绑定。
- 老 `bindings.json` 自动兼容：缺少 `kind` 的记录按原 `floodgate` 标记推断归类，升级不丢绑定。

### ⚙️ 变更
- 改绑的回收范围收窄为**同类**：改 Java 版名字不再影响基岩版那条（反之亦然）。
- `doc/protocol.md` 第 5 节补 `kind`、`bindings[]`、`removed[]` 说明，并注明「只绑基岩版时 `bound` 为 false 但请求成功」。

### 🐛 修复
- **修复离线模式下白名单写入错误 UUID、玩家永远进不来的问题**：此前绑定走 `whitelist add <游戏ID>`，服务端在 usercache 未命中该名字时会向 Mojang 名字 API 查询，把查到的**正版 UUID** 写进白名单；而离线客户端登录用的是本地推导的离线 UUID（`OfflinePlayer:<名字>` 的 v3 UUID），白名单按 UUID 匹配，两者不符即被拒绝（`You are not white-listed on this server!`）。名字越新、越没进过服的玩家越容易踩中。
  现在 `online-mode=false` 时改为直接读写服务器工作目录的 `whitelist.json`（顶层数组，每项只有 `uuid` 与 `name`）并执行一次 `/whitelist reload` 同步内存名单；同名但 UUID 不符的旧条目会被就地改写成正确的离线 UUID，**已中招的玩家重新 `/mc bind` 一次即可修好**。`online-mode=true` 时仍走 `whitelist add`，行为不变。
- 离线模式下解绑与改绑同样按 UUID 直接改白名单文件：`whitelist remove <名字>` 会解析出同一个错误 UUID，删不掉正确条目。
- 基岩版白名单改用 Floodgate 的 `fwhitelist add <名字>`（本版 Floodgate 不接受 UUID 参数，只能用不带 Geyser 前缀的用户名）；未检测到 Floodgate 时退回原指令并在日志中明确警告。
  **该路径改为写后回读校验**：`fwhitelist` 拿名字去查 XUID，公共 API 缓存未命中时只在控制台留一行 `Unable to find user in our cache`、**不抛异常也不改白名单**，而 `executeCommand` 恒返回 `true`（Cloud 指令框架自己吞掉错误），早期版本因此会谎报「已加入白名单」，实际白名单里还是旧的错误条目（例如按名字推导的离线 UUID），基岩玩家依旧被拒绝且重试无效。现在只在 `whitelist.json` 里确实存在该名字、且 UUID 是 Floodgate 生成的（高 64 位为 0 且非零）时才判成功，否则明确判失败并提示「让该玩家先用基岩版登录一次，或在玩家在线时重新绑定」；玩家**在线**时直接用其真实 Floodgate UUID 直写白名单文件，不再依赖那个 API。
- 白名单归属判定收紧：名字已在白名单且 UUID 正确时视为管理员手工添加（`whitelistAdded=false`），绑定照常成功但解绑不会误删该条目。

---

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
