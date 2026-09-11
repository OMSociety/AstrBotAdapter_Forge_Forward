package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;
import io.github.railgun19457.astrbotadapter.service.binding.BindingRecord;
import io.github.railgun19457.astrbotadapter.service.binding.BindingService;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 绑定 API 控制器。
 *
 * <p>专用入口而非复用 {@code /api/v1/command/execute}：本控制器的语义被收窄为
 * 「把某个名字加进白名单」，因此即使机器人凭据泄露，也无法借它执行任意服务器指令。</p>
 */
public class BindingController {

    private final BindingService bindingService;

    public BindingController(BindingService bindingService) {
        this.bindingService = bindingService;
    }

    public void registerRoutes(HttpRequestDispatcher dispatcher) {
        dispatcher.registerRoute("/api/v1/bindings", this::handleBindings);
        dispatcher.registerRoute("/api/v1/bindings/unbind", this::handleUnbind);
        dispatcher.registerRoute("/api/v1/bindings/lookup", this::handleLookup);
    }

    /**
     * {@code POST /api/v1/bindings} 建绑定；{@code GET} 列绑定（排查用）。
     */
    private Response handleBindings(FullHttpRequest request) {
        if (request.method() == HttpMethod.GET) {
            return listBindings();
        }
        if (request.method() != HttpMethod.POST) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持 POST 或 GET");
        }

        JsonObject params = parseBody(request);
        if (params == null) {
            return Response.error(ErrorCode.REQUEST_FORMAT_ERROR, "请求体 JSON 格式错误");
        }

        String platform = JsonUtil.getString(params, "platform", null);
        String userId = JsonUtil.getString(params, "userId", null);
        String gameName = JsonUtil.getString(params, "gameName", null);
        boolean bedrock = JsonUtil.getBoolean(params, "bedrock", false);

        BindingService.Result result = bindingService.bind(platform, userId, gameName, bedrock);
        return toResponse(result);
    }

    /**
     * {@code POST /api/v1/bindings/unbind}
     */
    private Response handleUnbind(FullHttpRequest request) {
        if (request.method() != HttpMethod.POST) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持 POST");
        }

        JsonObject params = parseBody(request);
        if (params == null) {
            return Response.error(ErrorCode.REQUEST_FORMAT_ERROR, "请求体 JSON 格式错误");
        }

        String platform = JsonUtil.getString(params, "platform", null);
        String userId = JsonUtil.getString(params, "userId", null);

        BindingService.Result result = bindingService.unbind(platform, userId);
        if (!result.isSuccess()) {
            return toResponse(result);
        }

        JsonObject data = new JsonObject();
        data.addProperty("gameName", result.getRecord().getGameName());
        data.addProperty("unbound", true);
        data.addProperty("whitelistRemoved", result.isWhitelistChanged());
        return Response.success(data);
    }

    /**
     * {@code GET /api/v1/bindings/lookup?platform=&amp;userId=}
     */
    private Response handleLookup(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持 GET");
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        String platform = firstParam(decoder, "platform");
        String userId = firstParam(decoder, "userId");

        JsonObject data = new JsonObject();
        if (!bindingService.isEnabled()) {
            return Response.error(ErrorCode.FEATURE_DISABLED, "绑定功能未启用");
        }
        if (platform == null || userId == null || platform.isBlank() || userId.isBlank()) {
            return Response.error(ErrorCode.REQUEST_PARAM_MISSING, "缺少 platform 或 userId 参数");
        }

        BindingRecord record = bindingService.lookup(platform, userId);
        data.addProperty("bound", record != null);
        if (record != null) {
            data.addProperty("gameName", record.getGameName());
            data.addProperty("floodgate", record.isFloodgate());
            data.addProperty("whitelistAdded", record.isWhitelistAdded());
        } else {
            data.addProperty("gameName", "");
        }
        return Response.success(data);
    }

    private Response listBindings() {
        if (!bindingService.isEnabled()) {
            return Response.error(ErrorCode.FEATURE_DISABLED, "绑定功能未启用");
        }

        List<JsonObject> items = new ArrayList<>();
        for (BindingRecord record : bindingService.list()) {
            JsonObject item = new JsonObject();
            item.addProperty("platform", record.getSubjectPlatform());
            // userId 属隐私标识：仅在显式排查接口返回，勿写入日志
            item.addProperty("userId", record.getSubjectUserId());
            item.addProperty("gameName", record.getGameName());
            item.addProperty("floodgate", record.isFloodgate());
            item.addProperty("whitelistAdded", record.isWhitelistAdded());
            item.addProperty("createdAt", record.getCreatedAt());
            item.addProperty("updatedAt", record.getUpdatedAt());
            items.add(item);
        }

        JsonObject data = new JsonObject();
        data.addProperty("count", items.size());
        data.add("bindings", JsonUtil.getGson().toJsonTree(items));
        return Response.success(data);
    }

    private Response toResponse(BindingService.Result result) {
        if (result.isSuccess()) {
            JsonObject data = new JsonObject();
            data.addProperty("gameName", result.getRecord().getGameName());
            data.addProperty("floodgate", result.getRecord().isFloodgate());
            data.addProperty("whitelistAdded", result.getRecord().isWhitelistAdded());
            data.addProperty("created", result.getRecord().getCreatedAt()
                    == result.getRecord().getUpdatedAt());
            String uuid = result.getRecord().getJavaUuid();
            data.addProperty("uuid", uuid == null || uuid.isEmpty() ? "" : uuid);
            return Response.success(data);
        }

        ErrorCode code = switch (result.getRejection()) {
            case FEATURE_DISABLED -> ErrorCode.FEATURE_DISABLED;
            case PARAM_MISSING -> ErrorCode.REQUEST_PARAM_MISSING;
            case INVALID_NAME -> ErrorCode.BINDING_INVALID_NAME;
            case NAME_TAKEN -> ErrorCode.BINDING_NAME_TAKEN;
            case NOT_BOUND -> ErrorCode.BINDING_NOT_FOUND;
            case WHITELIST_FAILED -> ErrorCode.BINDING_WHITELIST_FAILED;
        };

        String name = result.getGameName();
        if (result.getRejection() == BindingService.Rejection.NAME_TAKEN && name != null) {
            return Response.error(code, "游戏 ID " + name + " 已被其他账号绑定");
        }
        return Response.error(code);
    }

    private JsonObject parseBody(FullHttpRequest request) {
        String body = request.content().toString(StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonUtil.parseObject(body);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstParam(QueryStringDecoder decoder, String key) {
        var values = decoder.parameters().get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
