package dev.playroom.core;

/** 区分规则获胜、满盘平局和主动认输，不把认输冒充棋盘上出现五连。 */
public enum GameEndReason {
    FIVE_IN_ROW,
    BOARD_FULL,
    RESIGNATION
}
