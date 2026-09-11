package io.github.railgun19457.astrbotadapter.platform.forge;

import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import net.minecraft.server.MinecraftServer;

/**
 * Forge 平台服务器信息实现
 */
public class ForgeServer implements CommonServer {

    private final MinecraftServer server;
    private final ForgeTpsTracker tpsTracker;
    private final long startTime;

    public ForgeServer(MinecraftServer server, ForgeTpsTracker tpsTracker) {
        this.server = server;
        this.tpsTracker = tpsTracker;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public String getName() {
        return "Forge";
    }

    @Override
    public String getMotd() {
        return server.getMotd();
    }

    @Override
    public String getVersion() {
        return server.getServerVersion();
    }

    @Override
    public int getMaxPlayers() {
        return server.getMaxPlayers();
    }

    @Override
    public int getOnlinePlayerCount() {
        return server.getPlayerCount();
    }

    @Override
    public double[] getTps() {
        // Forge 无 Bukkit getTPS()，由 ForgeTpsTracker 基于 ServerTickEvent 自统计
        return tpsTracker.getTps();
    }

    @Override
    public Double getMspt() {
        return tpsTracker.getMspt();
    }

    @Override
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public int getPort() {
        return server.getPort();
    }

    @Override
    public String getIp() {
        String ip = server.getLocalIp();
        return ip == null || ip.isEmpty() ? "0.0.0.0" : ip;
    }
}
