package com.example.macromod.manager;

import com.example.macromod.MacroModClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects Hypixel SkyBlock fishing hotspots by scanning for invisible ArmorStand
 * entities whose custom name contains "HOTSPOT" (case-insensitive).
 *
 * Updated every client tick. Clearing and rebuilding the list each tick avoids
 * stale entries without needing timestamp-based eviction.
 */
@Environment(EnvType.CLIENT)
public class HotspotManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("macromod");
    private static final HotspotManager INSTANCE = new HotspotManager();

    /** Detection radius in blocks. */
    private static final double DETECTION_RADIUS = 50.0;
    private static final double DETECTION_RADIUS_SQ = DETECTION_RADIUS * DETECTION_RADIUS;

    private final List<Vec3d> hotspots = new ArrayList<>();

    private HotspotManager() {}

    public static HotspotManager getInstance() {
        return INSTANCE;
    }

    /** Called every client tick to scan for hotspot ArmorStands. */
    public void tick() {
        hotspots.clear();

        // Skip scan work unless hotspot ESP is enabled.
        if (!MacroModClient.getConfigManager().getConfig().isHotspotEspEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        double px = client.player.getX();
        double pz = client.player.getZ();

        for (var entity : client.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity ase)) continue;
            if (!ase.isInvisible()) continue;
            if (!ase.hasCustomName()) continue;

            // Use horizontal radius to avoid missing marker stands due to vertical offset.
            double dx = ase.getX() - px;
            double dz = ase.getZ() - pz;
            if ((dx * dx) + (dz * dz) > DETECTION_RADIUS_SQ) continue;

            // Normalize formatting and case before checking the hotspot marker.
            var customNameText = ase.getCustomName();
            if (customNameText == null) continue;
            String customName = StringHelper.stripTextFormat(customNameText.getString());
            if (!customName.toLowerCase(Locale.ROOT).contains("hotspot")) continue;

            hotspots.add(new Vec3d(ase.getX(), ase.getY(), ase.getZ()));
        }
    }

    /** Returns an unmodifiable view of detected hotspot positions this tick. */
    public List<Vec3d> getHotspots() {
        return Collections.unmodifiableList(hotspots);
    }
}
