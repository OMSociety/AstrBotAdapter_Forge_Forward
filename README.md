<div align="center">

<img src="https://raw.githubusercontent.com/OMSociety/AstrBotAdapter_NeoForge/main/icon.png" width="120" alt="AstrBotAdapter NeoForge Logo" />

# Astrbot Adapter NeoForge 群服互通适配器

**连接 Minecraft 服务器与 AstrBot** —— 消息互通 · 服务器状态监控 · 远程指令执行 · 游戏内 AI 聊天 · 群友绑定白名单

[![Version](https://img.shields.io/badge/version-1.2.0-blue.svg)](https://github.com/OMSociety/AstrBotAdapter_NeoForge)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%2026.2-orange.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-green.svg)](https://files.minecraftforge.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.2-green.svg)](https://neoforged.net/)
[![License](https://img.shields.io/badge/license-MIT-orange.svg)](LICENSE)
[![Stars](https://img.shields.io/github/stars/OMSociety/AstrBotAdapter_NeoForge)](https://github.com/OMSociety/AstrBotAdapter_NeoForge/stargazers)
[![Issues](https://img.shields.io/github/issues/OMSociety/AstrBotAdapter_NeoForge)](https://github.com/OMSociety/AstrBotAdapter_NeoForge/issues)

[✨ 核心特性](#-核心特性) • [📖 功能概览](#-功能概览) • [🚀 快速开始](#-快速开始) • [🧷 群友绑定与白名单](#-群友绑定与白名单) • [🎮 游戏内指令](#-游戏内指令) • [⚙️ 配置项说明](#️-配置项说明) • [🧩 架构](#-架构) • [📝 更新日志](#-更新日志)

</div>

> 🎨 本项目由 AI 编写 · 移植自 [AstrBotAdapter](https://github.com/Railgun19457/AstrBotAdapter)（原作者 [railgun19457](https://github.com/Railgun19457)）
>
> 本仓库源自 [AstrBotAdapter_Forge](https://github.com/OMSociety/AstrBotAdapter_Forge)（Forge 1.20.1），现为 **NeoForge 26.2** 版本；两者共用同一套平台无关代码，本版本新增「群友绑定 QQ ↔ 游戏 ID → 自动进白名单」。

---

## ✨ 核心特性

| 特性 | 说明 |
|------|------|
| 🧱 **消息互通** | 游戏内聊天 ↔ AstrBot 双向转发，支持发送者信息展示与自定义显示格式 |
| 📊 **服务器状态监控** | 在线人数 / 内存 / 运行时间 / **TPS / MSPT** —— 原生自统计，**无需前置 mod** |
| 🛡️ **远程指令执行** | REST / WebSocket 远程执行服务器指令，黑白名单 + `*` 通配符过滤 |
| 🤖 **游戏内 AI 聊天** | `@` 群聊 / `#` 私聊前缀触发，思考中提示，回复格式可自定义 |
| 🔔 **玩家事件通知** | 玩家加入 / 离开服务器实时推送到 AstrBot |
| 🧷 **群友绑定白名单** | 群友自助绑定 QQ 与游戏 ID，自动写入白名单；**兼容 Geyser + Floodgate 基岩版玩家** |
| ⚙️ **游戏内指令** | `/astrbot` 管理指令：状态查看 / 配置热重载 / token 管理 / 连接数查询 |
| 🔄 **配置热重载** | `/astrbot reload` 即时生效，**含端口等网络配置**（自动重启通信服务） |

---

## 📖 功能概览

### 🧱 消息互通
服务器聊天消息实时转发至 AstrBot，AstrBot 也可向服务器发送消息：
- 💬 游戏内 → AstrBot：玩家聊天自动转发（支持前缀过滤与自定义显示格式）
- 📨 AstrBot → 游戏内：外部消息推送至服务器，显示平台来源与发送者

### 📊 服务器状态监控
实时监测并上报服务器运行状态：
- 👥 **玩家信息** — 在线列表与数量变化
- 📈 **TPS / MSPT** — 基于服务端 tick 事件自统计（1m / 5m / 15m 滑动窗口），无 Bukkit API 依赖
- 🧠 **内存** — JVM 内存使用情况
- ⏱️ **运行时间** — 服务器已运行时长

> Forge / NeoForge 没有 Bukkit 的 `/tps`、`/ping` 命令，本模组通过事件总线自行统计 tick 间隔，**不需要前置 mod**。

### 🛡️ 远程指令执行
通过 REST API 远程执行服务器指令，支持黑白名单过滤：
- 🛡️ **过滤模式** — `NONE` / `BLACKLIST` / `WHITELIST`
- 🔀 **通配符匹配** — 指令列表支持 `*` 通配符

### 🤖 游戏内 AI 聊天
在游戏内直接与 AstrBot 的 AI 对话：
- 👥 **群聊 AI** — 前缀触发（默认 `@`）
- 💬 **私聊 AI** — 前缀触发（默认 `#`），可自定义回显格式
- ⏳ **思考中提示** — 可开关，AI 回复期间显示「思考中...」

### 🔔 玩家事件通知
- 🟢 玩家加入服务器时通知
- 🔴 玩家离开服务器时通知

### 🧷 群友绑定与白名单
群友在聊天群里自助绑定，无需管理员手动加白名单。详见 [群友绑定与白名单](#-群友绑定与白名单)。

---

## 🚀 快速开始

### 兼容性

| 加载器 | Minecraft | NeoForge | 构建插件 | JDK（构建 / 运行） |
|--------|-----------|----------|---------|-------------------|
| **NeoForge** | 26.2 | 26.2.0.80 | ModDevGradle 2 | 25 |

> 仅服务端安装，客户端无需安装（`displayTest = IGNORE_ALL_VERSION`）。

> [!NOTE]
> 本仓库只做 **NeoForge 26.2**。Forge 1.20.1 版本在 [AstrBotAdapter_Forge](https://github.com/OMSociety/AstrBotAdapter_Forge)。
> 两者共用同一套协议与配置格式，MaiBot 插件对两者都兼容。

### 第一步：下载 mod

从 [GitHub Releases](https://github.com/OMSociety/AstrBotAdapter_NeoForge/releases) 下载 `astrbotadapter-1.2.0-mc26.2.jar`。

### 第二步：安装
1. 将 jar 放入服务端 `mods/` 目录
2. 启动服务器，首次启动自动生成配置 `config/astrbotadapter/config.yml`

> 从 Forge 1.20.1 版本迁移过来时，配置文件键名与格式一致，`config/astrbotadapter/config.yml` 可直接复用。

### 第三步：连接 MaiBot

1. 获取认证 token：游戏内执行 `/astrbot token show`，或查看配置文件中的 `auth.token`
2. 在 MaiBot 安装配套插件 [maibot_plugin_minecraft_adapter](https://github.com/OMSociety/maibot_plugin_minecraft_adapter)
3. 在插件中添加服务器，配置地址、端口（默认 `8765`）和认证 token

> 💡 模组开箱即用：默认配置（监听 `0.0.0.0:8765`，WS + REST 双通道）即可连接，无需额外调整。

---

## 🧷 群友绑定与白名单

### 它解决什么问题

默认情况下，每个想进服的群友都要管理员手动 `/whitelist add`。本功能让群友**自助绑定**：

1. 群友进服被白名单拦下（或还没进过服）
2. 在 QQ 群里发 `/mc bind <游戏ID>`
3. 模组把该游戏 ID 写入服务器白名单，群友即可进服

**基岩版玩家**（Geyser + Floodgate）用 `/mc geyserbind <游戏ID>`。

### 两步配置

**① 服务端开启绑定**（`config/astrbotadapter/config.yml`）：

```yaml
binding:
  enabled: true          # 默认 false，需显式开启
```

修改后执行 `/astrbot reload` 生效。

**② 群友在群里绑定**：

| 场景 | 群里发送 |
|------|---------|
| Java 版玩家 | `/mc bind Steve` |
| 基岩版玩家（Floodgate） | `/mc geyserbind Steve` |
| 查看自己的绑定 | `/mc mybind` |
| 解除全部绑定 | `/mc unbind` |
| 只解除基岩版绑定 | `/mc geyserunbind` |

> 一个群友**可以同时持有 Java 版与基岩版两条绑定**，服务器白名单里会出现两个条目（例如 `Steve` 与 `.Steve`），两者互不覆盖、可分别单独更换或解除。这对「同一个人的电脑版和手机版都要进服」的场景是必需的。

### 白名单的 UUID 是怎么定的

白名单按 UUID 匹配，而 `/whitelist add <名字>` 在离线模式下会写出**错的** UUID（服务端查不到 usercache 时会向 Mojang 名字 API 查询，把正版 UUID 写进去，而离线客户端登录用的是本地推导的 UUID）。因此本模组区分处理：

| 服务端设置 | Java 版绑定的写入方式 | 结果 |
|------|------|------|
| `online-mode=true`（正版验证） | `/whitelist add <名字>` | 服务端写入的正是玩家登录用的 UUID |
| `online-mode=false`（离线/盗版服） | 直接读改写工作目录的 `whitelist.json` 并 `/whitelist reload` | 写入本地推导的离线 UUID；同名但 UUID 不符的旧条目会被就地修正 |

基岩版（`kind=geyser`）的 UUID 由 Bedrock XUID 生成，本地推导不出来，改用 Floodgate 的 `fwhitelist add <名字>`（名字不带前缀）；服务器没装 Floodgate 时会退回 `whitelist add` 并在日志里明确警告。

> 💡 若某位群友绑定后仍提示 `You are not white-listed on this server!`，让他**重新发一次 `/mc bind <游戏ID>`** 即可：新版本会把白名单里那条错误 UUID 改成正确的离线 UUID。

### 基岩版（Geyser + Floodgate）兼容说明

Floodgate 的 `use-prefix` 默认为开，基岩版玩家在服务端看到的名字带前缀（默认 `.`）。本模组做了**双向兼容**：

- 校验在线玩家时，会依次尝试「你输入的名字」「前缀 + 名字」「去掉前缀的名字」；
- 玩家不在线时，按 `binding.geyser.prefix` 自动补前缀。

因此 `/mc geyserbind Steve` 与 `/mc geyserbind .Steve` **都能正确绑定**。

> 如果你的 Floodgate 关闭了 `use-prefix`（或改了前缀），请把 `binding.geyser.usePrefix` 设为 `false`，或调整 `binding.geyser.prefix`。

### 行为约定

| 情况 | 行为 |
|------|------|
| 已在线但不在白名单 | 绑定后写白名单，**下次登录生效**（白名单在登录阶段校验，不会把在线玩家踢出） |
| 重复绑同一个名字 | 幂等成功，不产生重复条目 |
| Java 版改名 | 只回收 **Java 版**旧条目再写新的，**基岩版绑定不受影响**（反之亦然） |
| 名字已被别人绑定 | 拒绝，并只回显被占用的游戏 ID（**不显示对方 QQ 号**）；跨 Java/基岩也拦 |
| 解绑 | 只移除「由绑定写入」的条目，**不会误删管理员手工添加的同名条目** |
| 服务器未开白名单 | 绑定仍会成功写入，但需在 `server.properties` 里设置 `white-list=true` 才生效 |
| 绑定功能关闭 | 绑定指令会明确提示功能未启用，不会静默失败 |

> ⚠️ 安全设计：白名单写入走**专用绑定接口**，而不是通用指令执行接口。因此即使机器人 token 泄露，也无法借绑定接口执行任意服务器指令；本功能也与 `commandExecution` 里对 `whitelist *` 的黑名单互不影响。

---

## 🎮 游戏内指令

| 指令 | 说明 |
|------|------|
| `/astrbot help` | 显示帮助信息 |
| `/astrbot reload` | 重载配置文件（含网络配置，自动重启通信服务） |
| `/astrbot status` | 显示 ws/restapi 运行状态 |
| `/astrbot token [show/regen]` | 显示/重新生成认证 token |
| `/astrbot connections` | 显示当前活跃的 ws 连接 |

> 权限：敏感子命令（`reload` / `token` / `connections`）需要 **OP 等级 2**（无 Bukkit 权限系统，按 OP 等级判定）

---

## ⚙️ 配置项说明

配置文件（首次启动自动生成）：`config/astrbotadapter/config.yml`

### 基础设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `general.language` | `zh_CN` | string | 插件语言，支持 `zh_CN` / `en_US` |
| `general.debug` | `false` | boolean | 调试模式，开启后输出详细日志 |

### 认证设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `auth.token` | `""` | string | 认证 Token，留空启动时自动生成 32 位随机 Token |

### 网络服务设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `server.host` | `0.0.0.0` | string | WS/REST 监听地址 |
| `server.port` | `8765` | int | WS/REST 监听端口 |
| `server.websocket.enabled` | `true` | boolean | 是否启用 WebSocket 服务 |
| `server.websocket.heartbeatInterval` | `30` | int(秒) | 心跳间隔 |
| `server.websocket.heartbeatTimeout` | `90` | int(秒) | 心跳超时阈值 |
| `server.restapi.enabled` | `true` | boolean | 是否启用 REST API |
| `server.restapi.rateLimit` | `100` | int(次/分钟) | REST 频率限制，`0` 为不限流 |

### 消息转发设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `messageForward.enabled` | `true` | boolean | 是否启用聊天消息转发 |
| `messageForward.prefix` | `*` | string | 转发触发前缀（留空表示转发所有消息） |
| `messageForward.stripPrefix` | `true` | boolean | 转发时是否移除前缀 |
| `messageForward.incomingFormat` | `§7[§b{platform}§7] §f{username}§7: §f{content}` | string | 外来消息显示格式，支持 `{platform}` `{username}` `{content}` |

### AI 聊天设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `aiChat.group.enabled` | `true` | boolean | 是否启用群聊 AI |
| `aiChat.group.prefix` | `@` | string | 群聊 AI 触发前缀 |
| `aiChat.private.enabled` | `true` | boolean | 是否启用私聊 AI |
| `aiChat.private.prefix` | `#` | string | 私聊 AI 触发前缀 |
| `aiChat.private.echoFormat` | `<{player}> {message}` | string | 私聊回显格式，支持 `{player}` `{message}` |
| `aiChat.responseFormat` | `§7[§dAI§7] §f{content}` | string | AI 回复格式，支持 `{content}` |
| `aiChat.thinkingMessage` | `§7[§dAI§7] §e思考中...` | string | AI 思考中提示文案 |
| `aiChat.showThinking` | `true` | boolean | 是否显示思考中提示 |
| `aiChat.timeout` | `60` | int(秒) | AI 请求超时时间 |

### 玩家通知设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `playerNotification.join.enabled` | `true` | boolean | 是否通知玩家加入 |
| `playerNotification.quit.enabled` | `true` | boolean | 是否通知玩家离开 |

### 指令执行设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `commandExecution.enabled` | `true` | boolean | 是否允许远程执行指令 |
| `commandExecution.filterType` | `BLACKLIST` | enum | `NONE` / `BLACKLIST` / `WHITELIST` |
| `commandExecution.commandList` | 见默认配置 | list[string] | 指令过滤列表，支持 `*` 通配符 |

### 日志查询设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `logQuery.enabled` | `true` | boolean | 是否启用日志查询 |
| `logQuery.maxLines` | `1000` | int | 最大返回行数 |
| `logQuery.logFile` | `""` | string | 日志路径（相对服务器根目录），留空默认 `logs/latest.log` |

### 群友绑定设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `binding.enabled` | `false` | boolean | 是否启用群友绑定（会改动白名单，默认关闭） |
| `binding.applyToWhitelist` | `true` | boolean | 绑定时是否写入白名单；关闭后仅记录绑定关系 |
| `binding.geyser.enabled` | `true` | boolean | 是否启用基岩版绑定兼容 |
| `binding.geyser.prefix` | `.` | string | 基岩版玩家名前缀（与 Floodgate `use-prefix` 对应） |
| `binding.geyser.usePrefix` | `true` | boolean | 玩家离线时是否按前缀补齐名字 |
| `binding.storageFile` | `bindings.json` | string | 绑定数据文件名（位于模组数据目录） |

### 兼容保留设置
| 配置项 | 默认值 | 类型 | 说明 |
|--------|--------|------|------|
| `updateCheck.enabled` | `true` | boolean | 兼容保留项，本版本不生效 |
| `updateCheck.notifyOps` | `true` | boolean | 兼容保留项，本版本不生效 |
| `proxyMode.enabled` | `false` | boolean | 兼容保留项，本版本不支持代理模式，请保持 `false` |
| `proxyMode.secret` | `""` | string | 兼容保留项，本版本不使用 |

### 快速配置模板

参考以下结构（`config/astrbotadapter/config.yml`）：

```yaml
general:
  language: zh_CN
  debug: false

auth:
  token: ""            # 留空启动时自动生成

server:
  host: "0.0.0.0"
  port: 8765
  websocket:
    enabled: true
    heartbeatInterval: 30
    heartbeatTimeout: 90
  restapi:
    enabled: true
    rateLimit: 100

messageForward:
  enabled: true
  prefix: "*"
  stripPrefix: true
  incomingFormat: "§7[§b{platform}§7] §f{username}§7: §f{content}"

aiChat:
  group: { enabled: true, prefix: "@" }
  private: { enabled: true, prefix: "#", echoFormat: "<{player}> {message}" }
  responseFormat: "§7[§dAI§7] §f{content}"
  thinkingMessage: "§7[§dAI§7] §e思考中..."
  showThinking: true
  timeout: 60

playerNotification:
  join: { enabled: true }
  quit: { enabled: true }

commandExecution:
  enabled: true
  filterType: BLACKLIST
  commandList:
    - "op *"
    - "deop *"
    - "stop"
    - "reload"
    - "restart"
    - "ban *"
    - "ban-ip *"
    - "pardon *"
    - "pardon-ip *"
    - "whitelist *"

logQuery:
  enabled: true
  maxLines: 1000
  logFile: ""

binding:
  enabled: false
  applyToWhitelist: true
  geyser:
    enabled: true
    prefix: "."
    usePrefix: true
  storageFile: "bindings.json"
```

---

## 🧩 架构

### 仓库结构

```
common/    平台无关代码：通信层、配置、服务、绑定逻辑（零 Minecraft 依赖）
src/       NeoForge 26.2 专属层：平台适配器 + 事件监听 + neoforge.mods.toml
```

构建时通过 Gradle `sourceSets` 把 `common/` 与 `src/` 一起编译，因此**共用代码只有一份**，
平台差异被压缩在 `src/main/java/.../platform/neoforge` 这一层；
`common/` 因此可以原样复用给其它加载器（Forge、Paper 等）而无需复制源码。

### 通信层（Netty）
基于 Netty 的 WS + REST 双通道服务，与平台完全解耦：
- **WebSocket** — 与机器人插件长连接，Token 鉴权 + 心跳保活
- **REST API** — 状态查询 / 远程指令 / 日志查询 / 绑定
- 运行时缺省依赖（netty-codec-http、snakeyaml）通过 jarJar 打包进 mod

### 平台抽象层（PlatformAdapter）
统一抽象接口隔离平台差异，每个加载器只提供一个实现类：
- 玩家操作 / 聊天消息 / 指令执行 / 服务器信息上报
- 仅需替换实现类即可迁移至其他平台

### 能力协商（features）
`GET /api/v1/health`、`CONNECTION_ACK` 与能力敏感响应会暴露 `protocolVersion` 与 `features[]`，
客户端**据此判断能力，而不是判断模组版本号**。绑定功能对应能力位 **`binding.v1`**：
不含该能力位的旧版模组上，插件会自动提示升级并停用绑定指令，其余功能不受影响。

### 通信协议
与 [maibot_plugin_minecraft_adapter](https://github.com/OMSociety/maibot_plugin_minecraft_adapter) 对接（`PROTOCOL_VERSION = 2`），消息格式详见 [doc/protocol.md](doc/protocol.md)。

---

## 🔧 从源码构建

需要 **JDK 25**（Minecraft 26.2 的运行时要求）：

```bash
./gradlew build          // 产物：build/libs/astrbotadapter-1.2.0+mc26.2.jar
```

首次构建会下载 Gradle 9.2.1、Minecraft 26.2 与 NeoForge，并反编译 Minecraft 源码，耗时较长（约 10 分钟以上）。

**网络需要代理时**，两种写法都要给（缺一不可）：

```bash
# 1) Gradle 自身的依赖解析与插件解析：写在 gradle.properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7897
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7897

# 2) NeoFormRuntime 会另起子进程下载工具依赖，它只认环境变量：
HTTP_PROXY=http://127.0.0.1:7897
HTTPS_PROXY=http://127.0.0.1:7897
```

> 本仓库的 `gradle.properties` 已按本机 Clash 代理（`127.0.0.1:7897`）预置了这两组配置
> （代理同时写进 `org.gradle.jvmargs`，因为 NeoFormRuntime 的工具子进程不继承 Gradle 的 systemProp）。
> **不需要代理时请把 `gradle.properties` 里的 `systemProp.*proxy*` 与 `org.gradle.jvmargs` 中的 `-D*.proxy*` 一并删掉**，否则会连不上。

> ℹ️ **NeoForge 版本已固定在 `26.2.0.80`**，不是最新的 `26.2.0.87`。
> 原因：`26.2.0.87` 的 userdev access transformer 含一条失效条目
> `public net.minecraft.core.HolderSet$1 contents()Ljava/util/List;`（该匿名类在 26.2 中已不存在），
> 会让 `:createMinecraftArtifacts` 的反编译阶段必然失败；`26.2.0.80` 没有这条 AT，可干净构建。
> 该条目在未发布的 26.3 分支上已被删除，等 NeoForge 发布修复版后可以升回去（改 `gradle.properties` 的 `neo_version` 即可）。

---

## 📝 更新日志

> 📋 **[查看完整更新日志 →](CHANGELOG.md)**

---

## 🤝 贡献与反馈

如遇问题请在 [GitHub Issues](https://github.com/OMSociety/AstrBotAdapter_NeoForge/issues) 提交，欢迎 Pull Request！

## 🙏 致谢

- [AstrBot](https://github.com/AstrBotDevs/AstrBot) 开源聊天机器人框架
- [AstrBotAdapter](https://github.com/Railgun19457/AstrBotAdapter) 上游插件（[railgun19457](https://github.com/Railgun19457)）

---

## 📜 许可证

本项目采用 **MIT License** 开源协议（上游 [AstrBotAdapter](https://github.com/Railgun19457/AstrBotAdapter) 同样为 MIT）。

---

## 👤 作者

**railgun19457** — AstrBotAdapter 原作者 [@Railgun19457](https://github.com/Railgun19457)  
**OMSociety** — 多加载器维护 [@OMSociety](https://github.com/OMSociety)
