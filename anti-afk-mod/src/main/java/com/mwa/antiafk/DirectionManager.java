package com.mwa.antiafk;

import java.util.Random;

/**
 * Gère la rotation intelligente des directions de mouvement.
 * Évite de répéter la même direction deux fois de suite.
 */
public class DirectionManager {

    public enum MoveDirection {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT;

        /** Retourne la direction opposée (pour le micro-ajustement). */
        public MoveDirection opposite() {
            return switch (this) {
                case FORWARD -> BACKWARD;
                case BACKWARD -> FORWARD;
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
            };
        }

        /** Vecteur X de déplacement relatif au regard du joueur. */
        public double offsetX() {
            return switch (this) {
                case FORWARD, BACKWARD -> 0;
                case LEFT -> -1;
                case RIGHT -> 1;
            };
        }

        /** Vecteur Z de déplacement relatif au regard du joueur. */
        public double offsetZ() {
            return switch (this) {
                case FORWARD -> 1;
                case BACKWARD -> -1;
                case LEFT, RIGHT -> 0;
            };
        }
    }

    private static final MoveDirection[] DIRECTIONS = MoveDirection.values();
    private final Random random = new Random();
    private MoveDirection lastDirection = null;

    /**
     * Retourne la prochaine direction, différente de la précédente.
     */
    public MoveDirection next() {
        MoveDirection chosen;
        do {
            chosen = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        } while (chosen == lastDirection);
        lastDirection = chosen;
        return chosen;
    }

    public void reset() {
        lastDirection = null;
    }
}
