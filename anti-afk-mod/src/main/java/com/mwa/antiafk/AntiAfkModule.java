package com.mwa.antiafk;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * Module principal anti-AFK.
 * Machine à états : IDLE → MOVING → MICRO_ADJUST → PAUSE → ...
 * Simule des mouvements réalistes avec détection d'obstacles.
 */
public class AntiAfkModule {

    private static final Logger LOGGER = LoggerFactory.getLogger("AntiAFK");

    private enum State {
        IDLE,
        MOVING,
        MICRO_ADJUST,
        PAUSE
    }

    private final DirectionManager directionManager = new DirectionManager();
    private final BlockAnalyzer blockAnalyzer = new BlockAnalyzer();
    private final BlockHighlighter highlighter;
    private final Random random = new Random();

    private boolean active = false;
    private State state = State.IDLE;
    private int tickCounter = 0;
    private int targetTicks = 0;
    private DirectionManager.MoveDirection currentDirection = null;
    private boolean shouldJump = false;

    public AntiAfkModule(BlockHighlighter highlighter) {
        this.highlighter = highlighter;
    }

    /** Active ou désactive le module. */
    public void toggle() {
        active = !active;
        if (active) {
            LOGGER.info("[AntiAFK] Activé");
            state = State.IDLE;
            tickCounter = 0;
            directionManager.reset();
        } else {
            LOGGER.info("[AntiAFK] Désactivé");
            releaseAllKeys();
            highlighter.clear();
        }
    }

    public boolean isActive() {
        return active;
    }

    /** Appelé chaque client tick. */
    public void tick() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        highlighter.tick();

        switch (state) {
            case IDLE -> startNewMovement(client);
            case MOVING -> handleMoving(client);
            case MICRO_ADJUST -> handleMicroAdjust(client);
            case PAUSE -> handlePause();
        }
    }

    /** Choisit une nouvelle direction et commence le mouvement. */
    private void startNewMovement(MinecraftClient client) {
        // Essayer jusqu'à 4 directions pour éviter le vide
        for (int attempts = 0; attempts < 4; attempts++) {
            DirectionManager.MoveDirection dir = directionManager.next();
            BlockAnalyzer.AnalysisResult result = blockAnalyzer.analyze(dir);

            // Enregistrer les blocs détectés pour le rendu
            List<BlockAnalyzer.DetectedBlock> detected = blockAnalyzer.getLastDetectedBlocks();
            for (BlockAnalyzer.DetectedBlock block : detected) {
                if (block.type == BlockAnalyzer.AnalysisResult.BLOCKED) {
                    highlighter.addBlocked(block.pos);
                } else if (block.type == BlockAnalyzer.AnalysisResult.VOID) {
                    highlighter.addVoid(block.pos);
                }
            }

            if (result == BlockAnalyzer.AnalysisResult.VOID) {
                LOGGER.debug("[AntiAFK] Vide détecté direction {}, rotation...", dir);
                continue;
            }

            // Direction choisie
            currentDirection = dir;
            shouldJump = (result == BlockAnalyzer.AnalysisResult.BLOCKED);

            if (shouldJump) {
                LOGGER.debug("[AntiAFK] Obstacle détecté direction {}, saut activé", dir);
            }

            // Durée du mouvement : 10-20 ticks (0.5-1 seconde)
            targetTicks = 10 + random.nextInt(11);
            tickCounter = 0;
            state = State.MOVING;

            pressMovementKey(client, currentDirection, true);
            if (shouldJump) {
                client.options.jumpKey.setPressed(true);
            }
            return;
        }

        // Toutes les directions mènent au vide — pause de sécurité
        LOGGER.warn("[AntiAFK] Aucune direction sûre trouvée, pause");
        state = State.PAUSE;
        targetTicks = 20 + random.nextInt(20);
        tickCounter = 0;
    }

    /** Gère la phase de mouvement actif. */
    private void handleMoving(MinecraftClient client) {
        tickCounter++;

        // Ré-analyser périodiquement pendant le mouvement
        if (tickCounter % 5 == 0 && currentDirection != null) {
            BlockAnalyzer.AnalysisResult result = blockAnalyzer.analyze(currentDirection);
            if (result == BlockAnalyzer.AnalysisResult.VOID) {
                LOGGER.debug("[AntiAFK] Vide détecté en mouvement, arrêt immédiat");
                List<BlockAnalyzer.DetectedBlock> detected = blockAnalyzer.getLastDetectedBlocks();
                for (BlockAnalyzer.DetectedBlock block : detected) {
                    highlighter.addVoid(block.pos);
                }
                releaseAllKeys();
                state = State.IDLE;
                return;
            }
        }

        if (tickCounter >= targetTicks) {
            // Fin du mouvement, passer au micro-ajustement
            releaseAllKeys();
            state = State.MICRO_ADJUST;
            targetTicks = 2 + random.nextInt(3); // 2-4 ticks
            tickCounter = 0;

            // Presser brièvement la direction opposée
            if (currentDirection != null) {
                pressMovementKey(client, currentDirection.opposite(), true);
            }
        }
    }

    /** Gère le micro-ajustement (direction opposée brève). */
    private void handleMicroAdjust(MinecraftClient client) {
        tickCounter++;
        if (tickCounter >= targetTicks) {
            releaseAllKeys();
            state = State.PAUSE;
            // Pause humanisée : 5-15 ticks (0.25-0.75 sec)
            targetTicks = 5 + random.nextInt(11);
            tickCounter = 0;
        }
    }

    /** Gère la pause entre les mouvements. */
    private void handlePause() {
        tickCounter++;
        if (tickCounter >= targetTicks) {
            state = State.IDLE;
        }
    }

    /** Active ou désactive la touche de mouvement correspondant à une direction. */
    private void pressMovementKey(MinecraftClient client, DirectionManager.MoveDirection dir, boolean pressed) {
        GameOptions options = client.options;
        KeyBinding key = switch (dir) {
            case FORWARD -> options.forwardKey;
            case BACKWARD -> options.backKey;
            case LEFT -> options.leftKey;
            case RIGHT -> options.rightKey;
        };
        key.setPressed(pressed);
    }

    /** Relâche toutes les touches de mouvement. */
    private void releaseAllKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        GameOptions options = client.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.jumpKey.setPressed(false);
    }
}
