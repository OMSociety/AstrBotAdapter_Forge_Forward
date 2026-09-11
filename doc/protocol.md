# AstrbotAdaptor v2 Protocol

本文档定义 AstrbotAdaptor Java 插件对外暴露给 AstrBot/Python 侧的 v2 协议。

Java 端实现与本文档是当前协议基准；不描述旧协议兼容行为。本 Forge 版本已移除 Velocity 代理模块，仅保留独立模式（WS/REST）。

## 1. Transport

WebSocket 和 REST 共享同一个 Java 插件监听端口，默认 `8765`。

| 通道 | 地址 | 用途 |
| --- | --- | --- |
| WebSocket | `ws://<host>:<port>/ws?token=<token>` | 实时事件、聊天、消息转发、异步命令结果 |
| REST | `http://<host>:<port>/api/v1/*` | 查询、控制、健康检查、调试集成 |

认证规则：

| 通道 | 认证方式 |
| --- | --- |
| WebSocket | 握手 URL 查询参数 `token=<token>` |
| REST | Header `Authorization: Bearer <token>` |
| `GET /api/v1/health` | 免认证，但仍可能被限流 |

通用数据规则：

| 项 | 规则 |
| --- | --- |
| 编码 | UTF-8 |
| 格式 | JSON |
| 时间戳 | Unix epoch milliseconds |
| Content-Type | `application/json; charset=utf-8` |

## 2. Protocol Metadata

服务端通过 `CONNECTION_ACK`、`/api/v1/health` 和能力敏感 REST 响应暴露协议信息。

```json
{
  "protocolVersion": 2,
  "apiVersion": "v1",
  "features": [
    "rest.servers.v2",
    "rest.health",
    "server.mspt",
    "players.detail",
    "players.offline-cache",
    "command.async-result",
    "command.target-server-id",
    "command.ws-session-reply",
    "ws.disconnect",
    "binding.v1"
  ]
}
```

客户端应优先根据 `protocolVersion` 与 `features` 判断能力，不依赖插件版本号。`binding.v1` 表示本节点提供群友绑定与白名单写入能力（见第 5 节）；**不含该能力时必须优雅降级**，而不是调用后报错。

## 3. REST API

### 3.1 Response Envelope

所有 REST 响应使用同一 envelope。

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1706140800000
}
```

HTTP 状态码与 `code` 同时存在。客户端应先按 HTTP 状态判断请求级结果，再读取 JSON `code/message/data`。

| JSON code | HTTP status | 含义 |
| --- | --- | --- |
| `0` | `200` | 成功 |
| `1001..1003` | `401` | Token 无效、过期或缺失 |
| `2001..2003` | `400` | 参数或请求格式错误 |
| `3001` | `500` | 服务端内部错误 |
| `3002` | `503` 或 `429` | 服务不可用；限流时为 `429` |
| `4001` | `404` | 资源不存在 |
| `4002` | `404` | 玩家不在线 |
| `4003` | `403` | 功能未启用 |
| `4004` | `400` | 尚未绑定 |
| `4005` | `400` | 该游戏 ID 已被其他账号绑定 |
| `4006` | `400` | 游戏 ID 格式不合法 |
| `5001` | `400` | 指令执行失败 |
| `5002` | `403` | 指令被过滤 |
| `5003` | `403` | 无执行权限 |
| `5004` | `400` | 白名单写入失败 |

### 3.2 Endpoint Summary

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/api/v1/health` | no | 健康检查与协议能力探测 |
| `GET` | `/api/v1/server/info` | yes | 服务器基础信息 |
| `GET` | `/api/v1/server/status` | yes | 服务器运行状态 |
| `GET` | `/api/v1/server/tps` | yes | TPS 信息 |
| `GET` | `/api/v1/server/mspt` | yes | MSPT 信息 |
| `GET` | `/api/v1/players` | yes | 玩家列表 |
| `GET` | `/api/v1/players/{identifier}` | yes | 玩家详情，`identifier` 支持名称或 UUID |
| `POST` | `/api/v1/command/execute` | yes | 执行本地或后端路由命令 |
| `GET` | `/api/v1/logs` | yes | 查询日志 |

### 3.3 Health

`GET /api/v1/health`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ok",
    "protocolVersion": 2,
    "apiVersion": "v1",
    "features": ["rest.servers.v2", "rest.health", "server.mspt", "players.detail", "players.offline-cache", "command.async-result", "command.target-server-id", "command.ws-session-reply", "ws.disconnect"]
  },
  "timestamp": 1706140800000
}
```

该接口只表示 Java 插件 HTTP 服务可达，不代表 Token 有效。

### 3.4 Server APIs

`GET /api/v1/server/info`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "protocolVersion": 2,
    "apiVersion": "v1",
    "features": ["rest.servers.v2", "rest.health", "server.mspt", "players.detail", "players.offline-cache", "command.async-result", "command.target-server-id", "command.ws-session-reply", "ws.disconnect"],
    "servers": [
      {
        "id": "survival",
        "name": "survival",
        "displayName": "survival",
        "platform": "Paper",
        "version": "1.21.1",
        "motd": "A Minecraft Server",
        "onlinePlayers": 15,
        "maxPlayers": 100,
        "port": 25565,
        "scope": "local"
      }
    ],
    "aggregate": {
      "totalOnlinePlayers": 15,
      "totalMaxPlayers": 100,
      "backendCount": 0
    }
  },
  "timestamp": 1706140800000
}
```

`GET /api/v1/server/status`

`data.servers[]` 包含 `id/name/displayName/online/onlinePlayers/maxPlayers/uptime/uptimeFormatted/tps/mspt/memory/scope`，并返回同样的 `aggregate`。

`GET /api/v1/server/tps`

`data.servers[]` 包含 `id/name/displayName/tps/scope`。`tps` 为 `null` 或对象 `{"1m": 19.98, "5m": 19.95, "15m": 19.90}`。

`GET /api/v1/server/mspt`

`data.servers[]` 包含 `id/name/displayName/mspt/scope`。`mspt` 为 number 或 `null`。

服务器字段语义：

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | 稳定路由 ID。命令路由必须使用该值作为 `targetServerId` |
| `name` | string | 当前实现中的服务器名称 |
| `displayName` | string | 展示名称 |
| `scope` | string | `local`、`proxy` 或 `backend` |
| `aggregate.backendCount` | number | 已认证后端数量，独立服通常为 `0` |

Velocity 代理模式下，`servers[]` 会包含代理自身和已认证后端；后端 `id` 等于 Velocity 注册的 server name。

### 3.5 Player APIs

`GET /api/v1/players?detail=false&includeOffline=false`

Query 参数：

| Param | Type | Default | Meaning |
| --- | --- | --- | --- |
| `detail` | boolean | `false` | `false` 返回摘要字段，`true` 返回详情字段 |
| `includeOffline` | boolean | `false` | Velocity 代理模式下追加缓存的离线玩家 |

布尔参数接受 `true/false`、`1/0`、`yes/no`、`y/n`。

摘要响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "count": 1,
    "players": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Steve",
        "displayName": "Steve",
        "online": true,
        "server": "survival",
        "ping": 42,
        "dataSource": "live"
      }
    ]
  },
  "timestamp": 1706140800000
}
```

`GET /api/v1/players/{identifier}`

`identifier` 支持玩家名或 UUID，路径参数需要 URL encode。

详情字段：

| Field | Meaning |
| --- | --- |
| `uuid/name/displayName` | 玩家身份 |
| `online` | 是否在线 |
| `server/lastKnownServer` | 当前或最后已知服务器 ID |
| `ping/world/gameMode` | 网络与世界状态 |
| `health/maxHealth/level/foodLevel/exp/totalExp` | 玩家游戏状态，未知时为 `null` |
| `isOp/isFlying` | 权限与飞行状态，未知时为 `null` |
| `firstPlayed/lastPlayed/onlineTime/onlineTimeFormatted` | 持久化或缓存时间字段 |
| `location` | `{world,x,y,z}` 或 `null` |
| `dataSource` | `live`、`cache` 或 `persisted` |

数据来源优先级：在线实时数据 `live`，代理缓存 `cache`，后端持久字段 `persisted`。

### 3.6 Command API

`POST /api/v1/command/execute`

Request body：

```json
{
  "command": "say Hello World",
  "targetServerId": "survival",
  "executor": "CONSOLE",
  "playerUuid": null,
  "async": true
}
```

| Field | Type | Required | Default | Meaning |
| --- | --- | --- | --- | --- |
| `command` | string | yes | - | 要执行的命令，不带 `/` |
| `targetServerId` | string/null | no | `null` | 后端路由 ID。为空时在当前节点本地执行 |
| `executor` | string | no | `CONSOLE` | `CONSOLE` 或 `PLAYER` |
| `playerUuid` | string/null | conditional | `null` | `executor=PLAYER` 时指定玩家 UUID |
| `async` | boolean | no | `false` | 仅本地执行时控制是否异步排队 |

本地同步成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "command": "say Hello World",
    "success": true,
    "status": "DONE",
    "route": "local",
    "async": false,
    "executor": "CONSOLE",
    "playerUuid": null,
    "targetServerId": null,
    "executionTime": 5,
    "output": "Command executed"
  },
  "timestamp": 1706140800000
}
```

后端路由成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "command": "say Hello World",
    "success": true,
    "status": "QUEUED",
    "route": "backend",
    "async": true,
    "taskId": "task-id",
    "replyTo": "task-id",
    "executor": "CONSOLE",
    "playerUuid": null,
    "targetServerId": "survival",
    "output": "Command sent to backend: survival"
  },
  "timestamp": 1706140800000
}
```

本地异步响应同样返回 `status=QUEUED`，但 `route=local`、`targetServerId=null`，并且不会产生后续 WebSocket 最终结果。

REST 后端路由只返回排队确认，不通过 WebSocket 广播最终结果。需要最终命令结果的客户端应使用 WebSocket `COMMAND_REQUEST`。

### 3.7 Log API

`GET /api/v1/logs?lines=100&level=INFO&keyword=Steve&startTime=0&endTime=0`

| Param | Type | Default | Meaning |
| --- | --- | --- | --- |
| `lines` | number | `100` | 最近日志行数，受配置最大值限制 |
| `startTime` | number | `0` | 起始毫秒时间戳；需与 `endTime` 同时有效 |
| `endTime` | number | `0` | 结束毫秒时间戳 |
| `level` | string | `null` | 文本过滤，如 `INFO`、`WARN`、`ERROR` |
| `keyword` | string | `null` | 大小写不敏感关键字过滤 |

Response `data`：

```json
{
  "count": 1,
  "logs": [
    {
      "server": "survival",
      "scope": "local",
      "message": "[INFO] Server started"
    }
  ]
}
```

## 4. WebSocket

### 4.1 Connection ACK

成功握手后服务端立即发送：

```json
{
  "type": "CONNECTION_ACK",
  "id": "message-id",
  "payload": {
    "protocolVersion": 2,
    "apiVersion": "v1",
    "features": ["rest.servers.v2", "rest.health", "server.mspt", "players.detail", "players.offline-cache", "command.async-result", "command.target-server-id", "command.ws-session-reply", "ws.disconnect"],
    "sessionId": "ws-session-id",
    "serverInfo": {
      "name": "survival",
      "platform": "Paper",
      "version": "1.21.1"
    }
  },
  "timestamp": 1706140800000
}
```

### 4.2 Message Envelope

```json
{
  "type": "MESSAGE_TYPE",
  "id": "unique-message-id",
  "replyTo": "original-message-id",
  "source": {
    "type": "PLAYER",
    "server": {
      "name": "survival",
      "platform": "Paper",
      "version": "1.21.1"
    },
    "player": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Steve",
      "displayName": "Steve"
    }
  },
  "target": {
    "type": "PLAYER",
    "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
    "playerName": "Steve"
  },
  "payload": {},
  "timestamp": 1706140800000
}
```

`source`、`target`、`replyTo` 按消息类型可省略。客户端发送需要响应的消息时必须提供稳定 `id`。

### 4.3 Message Types

方向以 Java 插件为中心。

| Type | Direction | Meaning |
| --- | --- | --- |
| `HEARTBEAT` | client -> Java | 心跳 |
| `HEARTBEAT_ACK` | Java -> client | 心跳响应，`id` 等于请求 `id` |
| `CONNECTION_ACK` | Java -> client | 连接确认 |
| `CHAT_REQUEST` | Java -> client | 玩家触发 AI 聊天 |
| `CHAT_RESPONSE` | client -> Java | AI 聊天回复 |
| `MESSAGE_FORWARD` | Java -> client | Minecraft 消息转发到外部 |
| `MESSAGE_INCOMING` | client -> Java | 外部消息进入 Minecraft |
| `PLAYER_JOIN` | Java -> client | 玩家加入通知 |
| `PLAYER_QUIT` | Java -> client | 玩家离开通知 |
| `COMMAND_REQUEST` | client -> Java | 执行命令 |
| `COMMAND_RESPONSE` | Java -> client | 命令执行结果 |
| `STATUS_UPDATE` | Java -> client | 状态更新推送 |
| `ERROR` | both | 错误消息 |
| `DISCONNECT` | Java -> client | 服务端主动断开通知 |

### 4.4 Heartbeat

Client sends：

```json
{"type":"HEARTBEAT","id":"heartbeat-id","timestamp":1706140800000}
```

Java replies：

```json
{"type":"HEARTBEAT_ACK","id":"heartbeat-id","timestamp":1706140800000}
```

客户端建议 30 秒发送一次心跳；90 秒无心跳可能被服务端清理。

### 4.5 AI Chat

Java sends `CHAT_REQUEST`：

```json
{
  "type": "CHAT_REQUEST",
  "id": "chat-request-id",
  "source": {
    "type": "PLAYER",
    "server": {"name":"survival","platform":"Paper","version":"1.21.1"},
    "player": {"uuid":"550e8400-e29b-41d4-a716-446655440000","name":"Steve","displayName":"Steve"}
  },
  "payload": {
    "chatMode": "GROUP",
    "content": "你好",
    "context": {"sessionId":"group-survival"}
  },
  "timestamp": 1706140800000
}
```

Client replies with `CHAT_RESPONSE`：

```json
{
  "type": "CHAT_RESPONSE",
  "id": "chat-response-id",
  "replyTo": "chat-request-id",
  "target": {"type":"BROADCAST"},
  "payload": {
    "content": "你好，我在。",
    "status": "SUCCESS",
    "errorMessage": null
  },
  "timestamp": 1706140800000
}
```

`target.type=PLAYER` 时可提供 `playerUuid` 或 `playerName`。若无 `target`，Java 会用 `replyTo` 查找原始聊天请求决定广播或私聊。

### 4.6 Message Forwarding

Java sends `MESSAGE_FORWARD`：

```json
{
  "type": "MESSAGE_FORWARD",
  "id": "forward-id",
  "source": {
    "type": "PLAYER",
    "server": {"name":"survival","platform":"Paper","version":"1.21.1"},
    "player": {"uuid":"550e8400-e29b-41d4-a716-446655440000","name":"Steve","displayName":"Steve"}
  },
  "payload": {"content":"hello external"},
  "timestamp": 1706140800000
}
```

Client sends `MESSAGE_INCOMING`：

```json
{
  "type": "MESSAGE_INCOMING",
  "id": "incoming-id",
  "payload": {
    "source": {"platform":"QQ","userName":"Alice"},
    "content": "hello minecraft"
  },
  "timestamp": 1706140800000
}
```

### 4.7 Player And Status Events

`PLAYER_JOIN` / `PLAYER_QUIT` payload：

```json
{
  "action": "join",
  "onlineCount": 15,
  "maxPlayers": 100,
  "reason": "QUIT"
}
```

`reason` 仅离开通知可能存在。

`STATUS_UPDATE` payload：

```json
{
  "onlinePlayers": 15,
  "maxPlayers": 100,
  "uptime": 3600000
}
```

### 4.8 Command Request And Response

Client sends `COMMAND_REQUEST`：

```json
{
  "type": "COMMAND_REQUEST",
  "id": "command-request-id",
  "payload": {
    "command": "say Hello World",
    "targetServerId": "survival",
    "executor": "CONSOLE",
    "playerUuid": null
  },
  "timestamp": 1706140800000
}
```

后端路由结果会以 `COMMAND_RESPONSE` 返回到同一个 WebSocket session：

```json
{
  "type": "COMMAND_RESPONSE",
  "id": "command-response-id",
  "replyTo": "command-request-id",
  "payload": {
    "success": true,
    "command": "say Hello World",
    "route": "backend",
    "serverId": "survival",
    "output": "Command executed",
    "executionTime": 5,
    "logs": ["Command executed"],
    "errorCode": null,
    "errorMessage": null
  },
  "timestamp": 1706140800000
}
```

Rules：

| Field | Meaning |
| --- | --- |
| `targetServerId` | 为空或缺省时本地执行；非空时路由到 REST `servers[].id` 对应后端 |
| `executor` | `CONSOLE` 或 `PLAYER` |
| `playerUuid` | `executor=PLAYER` 时必需 |
| `replyTo` | 始终等于请求 `id` |
| `route` | 后端路由结果中为 `backend`；本地执行结果当前可省略该字段 |
| `serverId` | 后端路由结果中的实际执行后端 ID；本地执行结果当前可省略该字段 |

后端路由的 `COMMAND_RESPONSE` 和相关 `ERROR` 只发送给发起该请求的 WebSocket session，不广播给其它客户端。

### 4.9 Error And Disconnect

`ERROR`：

```json
{
  "type": "ERROR",
  "id": "error-id",
  "replyTo": "original-message-id",
  "payload": {
    "code": 5001,
    "message": "指令执行失败",
    "detail": "后端服务器未连接或无在线玩家"
  },
  "timestamp": 1706140800000
}
```

`DISCONNECT`：

```json
{
  "type": "DISCONNECT",
  "id": "disconnect-id",
  "payload": {
    "reason": "SERVER_SHUTDOWN",
    "message": "Server is shutting down"
  },
  "timestamp": 1706140800000
}
```

当前实现会在 Java 通信服务器停止时发送 `SERVER_SHUTDOWN`。

## 5. Binding API（`binding.v1`）

把外部账号（QQ 等）与游戏 ID 绑定，并把结果写入服务器白名单。

**为什么单列一个 API 而不是复用 `command/execute`**：默认配置把 `whitelist *` 列在外部指令黑名单里。
若复用通用指令通道实现绑定，等于给机器人开放白名单权限。本 API 的语义被收窄为
「把某个名字加进白名单」，即使凭据泄露也无法用它执行任意服务器指令。

是否支持本 API 由能力位 `binding.v1` 声明；<strong>客户端必须在探测到该能力位后才调用</strong>，
否则应停用绑定功能并提示升级。此外服务端配置 `binding.enabled` 必须为 `true`，否则返回 `4003`。

### 5.1 REST

`POST /api/v1/bindings`

```json
{
  "platform": "qq",
  "userId": "123456",
  "gameName": "Steve",
  "bedrock": false
}
```

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `platform` | string | yes | 外部平台名 |
| `userId` | string | yes | 外部平台内的用户 ID（大小写敏感） |
| `gameName` | string | yes | 游戏 ID，允许字母/数字/下划线/点号/中文，长度 1–32 |
| `bedrock` | boolean | no | 是否为基岩版（Geyser/Floodgate），默认 `false`。为真时按 `binding.geyser` 规则处理前缀 |

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "gameName": ".Steve",
    "floodgate": true,
    "whitelistAdded": true,
    "created": true,
    "uuid": ""
  },
  "timestamp": 1706140800000
}
```

`gameName` 为**实际生效**的游戏 ID（可能已按前缀规则补齐）；`whitelistAdded` 表示本次是否写入了白名单；
`uuid` 在玩家在线时给出其真实 UUID，否则为空字符串。

`kind` 为本次绑定落在哪一类：`java`（`bedrock=false`）或 `geyser`（`bedrock=true`）。
**同一账号可以同时持有这两类各一条绑定**，两条白名单条目并存：`/api/v1/bindings` 只影响
与 `bedrock` 对应的那一类，另一类保持不变。

`POST /api/v1/bindings/unbind`

```json
{ "platform": "qq", "userId": "123456", "kind": "geyser" }
```

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `platform` | string | yes | 外部平台名 |
| `userId` | string | yes | 外部平台用户 ID |
| `kind` | string | no | `java` / `geyser` 只解该类；省略或 `all` 表示**清空该账号全部绑定** |

成功响应 `data`：`{"unbound": true, "whitelistRemoved": true, "gameName": ".Steve",
"removed": [{"kind": "geyser", "gameName": ".Steve", "whitelistRemoved": true}]}`。
`removed` 列出本次实际移除的每一条（清空时可能两条）；`gameName` 为兼容旧客户端保留，取首条。
单条的 `whitelistRemoved` 仅在「该白名单条目由绑定写入」时为 `true`——管理员手工添加的同名条目不会被移除。

`GET /api/v1/bindings/lookup?platform=qq&userId=123456`

成功响应 `data` 同时提供两类视图：

```json
{
  "bound": true,
  "gameName": "Steve",
  "floodgate": false,
  "whitelistAdded": true,
  "javaBound": true,
  "geyserBound": true,
  "bindings": [
    {"kind": "java", "gameName": "Steve", "floodgate": false, "whitelistAdded": true},
    {"kind": "geyser", "gameName": ".Steve", "floodgate": true, "whitelistAdded": true}
  ]
}
```

`bound`/`gameName`/`floodgate`/`whitelistAdded` 为兼容旧客户端保留，含义是**Java 版绑定**；
`bindings` 是完整视图（Java 在前）。**该账号一条绑定都没有**时才返回错误码 `4004`；
只绑了基岩版时 `bound` 为 `false` 但请求成功，客户端应据 `bindings` 判断，不要只看 `bound`。

`GET /api/v1/bindings?page=1&size=100`

列出全部绑定，`data`：`{"count": 2, "bindings": [{"platform","userId","kind","gameName","floodgate","whitelistAdded","createdAt","updatedAt"}]}`。用于排查冲突，`userId` 属隐私标识，请勿写入日志。

### 5.2 WebSocket（可选通道）

需要与其它实时事件共用长连接时，可发送 `BIND_REQUEST`：

```json
{
  "type": "BIND_REQUEST",
  "id": "bind-request-id",
  "payload": {
    "action": "bind",
    "platform": "qq",
    "userId": "123456",
    "gameName": "Steve",
    "bedrock": false
  },
  "timestamp": 1706140800000
}
```

`action` 可为 `bind`（默认）或 `unbind`。`unbind` 时可在 `payload` 里带 `kind`，语义与 REST 相同
（`java` / `geyser` 只解该类；省略或 `all` 清空全部）。结果以 `BIND_RESPONSE` 返回（`replyTo` 等于请求 `id`），
校验与白名单写入逻辑与 REST 完全一致：

```json
{
  "type": "BIND_RESPONSE",
  "id": "bind-response-id",
  "replyTo": "bind-request-id",
  "payload": {
    "success": true,
    "action": "bind",
    "kind": "geyser",
    "gameName": ".Steve",
    "floodgate": true,
    "whitelistAdded": true,
    "whitelistRemoved": false
  },
  "timestamp": 1706140800000
}
```

`unbind` 的响应额外带 `removed` 数组（与 REST 同结构），逐条列出本次移除的绑定。

失败时返回 `ERROR`（`replyTo` 等于请求 `id`），`payload.code` 为 5.3 中的错误码。

### 5.3 错误码

| JSON code | 含义 |
| --- | --- |
| `4003` | 绑定功能未启用（`binding.enabled=false`） |
| `4004` | 尚未绑定 |
| `4005` | 该游戏 ID 已被其他账号绑定 |
| `4006` | 游戏 ID 格式不合法 |
| `5004` | 白名单写入失败（如需检查服务器是否启用白名单） |

### 5.4 行为约定

- 白名单写入**不受 `commandExecution` 过滤器影响**，具体路径取决于服务器是否开启正版验证：
  - `online-mode=true`：以控制台身份执行 `whitelist add <gameName>`，服务端写入的正是玩家登录用的正版 UUID。
  - `online-mode=false`（离线模式）：直接读改写服务器工作目录的 `whitelist.json`（顶层数组，每项只有 `uuid` 与 `name`），
    写入 `OfflinePlayer:<名字>` 推导出的离线 UUID，随后执行 `/whitelist reload` 同步内存名单。
    这条路径不能用 `whitelist add <名字>` 代替：服务端查不到 usercache 时会向 Mojang 名字 API 查询，把正版 UUID 写进白名单，
    与离线客户端登录用的 UUID 不符，该玩家会被永久拒之门外。同名但 UUID 不符的旧条目会被就地改写为正确的离线 UUID。
  - 基岩版（`kind=geyser`）：UUID 由 Bedrock XUID 生成、本地无法推导，改用 Floodgate 的 `fwhitelist add <名字>`（名字不带 Geyser 前缀）。
- 白名单在登录阶段校验，因此绑定不会影响已在线玩家，仅在其下次登录时生效。
- 玩家不在线时也允许绑定：按 `binding.geyser` 规则推导最终名字后直接写入白名单。
- 服务器上本来就有同名白名单条目且 UUID 正确时，绑定照常成功但 `whitelistAdded` 为 `false`，解绑不会移除它（只有绑定自己写入的条目才回收）。

## 6. Operational Notes

命令安全：Java 配置中的命令过滤仍在服务端强制执行，客户端不能绕过。

Velocity 路由：所有外部路由字段使用 `targetServerId`，其值必须来自 REST `servers[].id`。

REST 与 WS 职责：REST 用于查询与一次性控制；需要实时事件或异步最终结果时使用 WebSocket。

Proxy 模式：后端 `proxyMode.enabled=true` 时后端不直接暴露 REST/WS，由 Velocity 统一暴露外部协议。
