package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;
import com.mwa.n0name.pathfinding.PathNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimap-like route heatmap overlay highlighting lane mistakes and dead zones.
 */
public class RouteHeatmapModule {

    private static final int MAX_POINTS = 200;

    private static class ProfileData {
        final Deque<Vec3d> mistakePoints = new ArrayDeque<>();
        final Deque<Vec3d> deadZonePoints = new ArrayDeque<>();
    }

    // Key format: routeName#P{index}, enabling multiple profiles per route name.
    private final Map<String, ProfileData> profileDataByKey = new ConcurrentHashMap<>();
    private Vec3d lastPos = null;
    private int stillTicks = 0;

    public void tick() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isRouteHeatmapEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        ProfileData data = getCurrentProfileData(cfg);
        if (data == null) return;

        Vec3d now = new Vec3d(player.getX(), player.getY(), player.getZ());
        if (lastPos != null) {
            double movedSq = now.squaredDistanceTo(lastPos);
            if (movedSq < 0.0025) {
                stillTicks++;
            } else {
                stillTicks = 0;
            }

            if (stillTicks >= 60) {
                addPoint(data.deadZonePoints, now);
                stillTicks = 0;
            }
        }
        lastPos = now;

        List<PathNode> route = getActiveRoute(cfg);
        if (!route.isEmpty()) {
            double minDistSq = Double.MAX_VALUE;
            for (PathNode node : route) {
                Vec3d nv = node.toVec3dCenter();
                double d2 = now.squaredDistanceTo(nv);
                if (d2 < minDistSq) minDistSq = d2;
            }
            if (minDistSq > 4.0) { // >2 blocks from route considered lane mistake
                addPoint(data.mistakePoints, now);
            }
        }
    }

    public void renderHud(DrawContext ctx) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isRouteHeatmapEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        int x = cfg.getRouteHeatmapX();
        int y = cfg.getRouteHeatmapY();
        int size = cfg.getRouteHeatmapSize();
        int pad = 6;

        ctx.fill(x, y, x + size, y + size, 0xAA101418);
        ctx.fill(x, y, x + size, y + 1, 0xFF40C4FF);
        ctx.fill(x, y + size - 1, x + size, y + size, 0xFF40C4FF);
        ctx.fill(x, y, x + 1, y + size, 0xFF40C4FF);
        ctx.fill(x + size - 1, y, x + size, y + size, 0xFF40C4FF);
        ctx.drawTextWithShadow(client.textRenderer, "Route Heatmap", x + 6, y + 4, 0xFFFFFFFF);

        List<PathNode> route = getActiveRoute(cfg);
        if (route.isEmpty()) {
            ctx.drawTextWithShadow(client.textRenderer, "No lane route set", x + 6, y + 18, 0xFFAAAAAA);
            return;
        }

        ProfileData data = getCurrentProfileData(cfg);
        if (data == null) return;

        Vec3d center = new Vec3d(player.getX(), player.getY(), player.getZ());
        double radius = 24.0;
        int mapX = x + pad;
        int mapY = y + 18;
        int mapSize = size - pad * 2;

        // route points in cyan
        for (PathNode node : route) {
            Vec3d p = node.toVec3dCenter();
            int[] uv = toMap(center, p, radius, mapX, mapY, mapSize);
            if (uv == null) continue;
            ctx.fill(uv[0], uv[1], uv[0] + 2, uv[1] + 2, 0xFF55EEFF);
        }

        // mistakes in red
        for (Vec3d p : data.mistakePoints) {
            int[] uv = toMap(center, p, radius, mapX, mapY, mapSize);
            if (uv == null) continue;
            ctx.fill(uv[0], uv[1], uv[0] + 3, uv[1] + 3, 0xFFFF5555);
        }

        // dead zones in orange
        for (Vec3d p : data.deadZonePoints) {
            int[] uv = toMap(center, p, radius, mapX, mapY, mapSize);
            if (uv == null) continue;
            ctx.fill(uv[0], uv[1], uv[0] + 3, uv[1] + 3, 0xFFFFAA33);
        }

        // player center
        int cx = mapX + mapSize / 2;
        int cy = mapY + mapSize / 2;
        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF);
        ctx.drawTextWithShadow(client.textRenderer,
            "Profile P" + cfg.getRouteHeatmapProfileIndex(),
            x + size - 68, y + 4, 0xFFE0E0E0);
    }

    public void clearCurrentProfile() {
        ModConfig cfg = ModConfig.getInstance();
        ProfileData data = getCurrentProfileData(cfg);
        if (data == null) return;
        data.mistakePoints.clear();
        data.deadZonePoints.clear();
    }

    private List<PathNode> getActiveRoute(ModConfig cfg) {
        String routeName = cfg.getGardenLaneRouteName();
        if (routeName == null || routeName.isBlank()) return java.util.Collections.emptyList();
        List<PathNode> route = cfg.getRoute(routeName);
        return route == null ? java.util.Collections.emptyList() : route;
    }

    private ProfileData getCurrentProfileData(ModConfig cfg) {
        String routeName = cfg.getGardenLaneRouteName();
        if (routeName == null || routeName.isBlank()) return null;
        String key = routeName + "#P" + cfg.getRouteHeatmapProfileIndex();
        return profileDataByKey.computeIfAbsent(key, k -> new ProfileData());
    }

    private void addPoint(Deque<Vec3d> deque, Vec3d p) {
        deque.addLast(p);
        while (deque.size() > MAX_POINTS) deque.removeFirst();
    }

    private int[] toMap(Vec3d center, Vec3d point, double radius, int x, int y, int size) {
        double dx = point.x - center.x;
        double dz = point.z - center.z;
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) return null;
        double nx = (dx / radius) * 0.5 + 0.5;
        double ny = (dz / radius) * 0.5 + 0.5;
        int px = x + (int)(nx * size);
        int py = y + (int)(ny * size);
        if (px < x || py < y || px >= x + size || py >= y + size) return null;
        return new int[]{px, py};
    }
}
