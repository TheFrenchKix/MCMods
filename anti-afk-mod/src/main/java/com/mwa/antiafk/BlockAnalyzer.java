package com.mwa.antiafk;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyse les blocs autour du joueur pour détecter les obstacles et le vide.
 * Retourne un résultat d'analyse pour une direction donnée.
 */
public class BlockAnalyzer {

    public enum AnalysisResult {
        CLEAR,    // Voie libre
        BLOCKED,  // Mur ou obstacle solide
        VOID      // Pas de sol sous la position cible
    }

    /** Info sur un bloc détecté (pour le highlighter). */
    public static class DetectedBlock {
        public final BlockPos pos;
        public final AnalysisResult type;

        public DetectedBlock(BlockPos pos, AnalysisResult type) {
            this.pos = pos;
            this.type = type;
        }
    }

    private final List<DetectedBlock> lastDetectedBlocks = new ArrayList<>();

    /**
     * Analyse la direction donnée par rapport à la position et l'orientation du joueur.
     * Vérifie les blocs au niveau des pieds, de la tête, et le sol devant.
     */
    public AnalysisResult analyze(DirectionManager.MoveDirection direction) {
        lastDetectedBlocks.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return AnalysisResult.CLEAR;

        World world = client.world;

        // Calculer le vecteur de déplacement en coordonnées monde
        float yawRad = player.getYaw() * (float) (Math.PI / 180.0);
        double sinYaw = MathHelper.sin(yawRad);
        double cosYaw = MathHelper.cos(yawRad);

        // direction.offsetZ = avant/arrière, direction.offsetX = gauche/droite
        double dz = direction.offsetZ();
        double dx = direction.offsetX();

        // Transformation en coordonnées monde (relatif au yaw du joueur)
        double worldDx = dx * cosYaw - dz * sinYaw;
        double worldDz = dx * sinYaw + dz * cosYaw;

        Vec3d playerPos = player.getPos();
        // Position cible à ~1.2 blocs devant dans la direction choisie
        double targetX = playerPos.x + worldDx * 1.2;
        double targetZ = playerPos.z + worldDz * 1.2;
        double targetY = playerPos.y;

        BlockPos feetTarget = BlockPos.ofFloored(targetX, targetY, targetZ);
        BlockPos headTarget = feetTarget.up();
        BlockPos groundTarget = feetTarget.down();

        // Vérification du vide : pas de bloc solide sous la position cible
        BlockState groundState = world.getBlockState(groundTarget);
        if (!groundState.isSolidBlock(world, groundTarget)) {
            // Vérifier un bloc encore plus bas (cas d'une marche)
            BlockPos deepGround = groundTarget.down();
            BlockState deepGroundState = world.getBlockState(deepGround);
            if (!deepGroundState.isSolidBlock(world, deepGround)) {
                lastDetectedBlocks.add(new DetectedBlock(groundTarget, AnalysisResult.VOID));
                return AnalysisResult.VOID;
            }
        }

        // Vérification obstacle : bloc solide au niveau des pieds ou de la tête
        BlockState feetState = world.getBlockState(feetTarget);
        BlockState headState = world.getBlockState(headTarget);

        boolean feetBlocked = feetState.isSolidBlock(world, feetTarget);
        boolean headBlocked = headState.isSolidBlock(world, headTarget);

        if (feetBlocked) {
            lastDetectedBlocks.add(new DetectedBlock(feetTarget, AnalysisResult.BLOCKED));
        }
        if (headBlocked) {
            lastDetectedBlocks.add(new DetectedBlock(headTarget, AnalysisResult.BLOCKED));
        }

        if (feetBlocked || headBlocked) {
            return AnalysisResult.BLOCKED;
        }

        return AnalysisResult.CLEAR;
    }

    /** Retourne les blocs détectés lors de la dernière analyse. */
    public List<DetectedBlock> getLastDetectedBlocks() {
        return new ArrayList<>(lastDetectedBlocks);
    }
}
