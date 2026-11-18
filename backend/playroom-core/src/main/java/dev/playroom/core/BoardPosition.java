package dev.playroom.core;

/** 15×15棋盘的零基坐标：行和列都只能是0–14，构造成功就保证坐标合法。 */
public record BoardPosition(int row, int col) {
    public static final int BOARD_SIZE = 15;

    public BoardPosition {
        // 15是尺寸而非最大下标；任一条件成立都应拒绝，不能把||改成&&。
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            throw new IllegalArgumentException(
                    "Position must be within [0, 14]: (%d, %d)".formatted(row, col));
        }
    }
}
