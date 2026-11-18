package dev.playroom.core;

/** Rule result only; room lifecycle, resignations and persistence are separate. */
public enum GameOutcome {
    ONGOING,
    BLACK_WINS,
    WHITE_WINS,
    DRAW
}
