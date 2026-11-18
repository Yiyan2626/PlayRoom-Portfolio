package dev.playroom.core;

import java.util.Objects;
import java.util.Optional;

/**
 * 不可变15×15棋盘；落子返回候选新盘，不修改旧盘。
 * 本类只管空位和棋子，身份、回合和终局由D05的GameState校验。
 */
public final class Board {
    private final StoneColor[][] cells;
    private final int moveCount;

    public Board() {
        this(new StoneColor[BoardPosition.BOARD_SIZE][BoardPosition.BOARD_SIZE], 0);
    }

    // 只接收本类新建且独占的数组；不保留调用方可修改的数组引用。
    private Board(StoneColor[][] cells, int moveCount) {
        this.cells = cells;
        this.moveCount = moveCount;
    }

    public Optional<StoneColor> at(BoardPosition position) {
        Objects.requireNonNull(position, "position");
        return Optional.ofNullable(cells[position.row()][position.col()]);
    }

    public Board place(BoardPosition position, StoneColor color) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(color, "color");
        if (cells[position.row()][position.col()] != null) {
            throw new IllegalStateException("Cell is already occupied: " + position);
        }
        // 先复制再写入：创建新盘失败或调用方保留旧盘时，旧棋盘都不受影响。
        StoneColor[][] candidate = copyCells();
        candidate[position.row()][position.col()] = color;
        return new Board(candidate, moveCount + 1);
    }

    public int moveCount() {
        return moveCount;
    }

    /** 满盘只表示没有空格；是否平局还要由规则层先排除最后一手获胜。 */
    public boolean isFull() {
        return moveCount == BoardPosition.BOARD_SIZE * BoardPosition.BOARD_SIZE;
    }

    /** 返回可修改但与内部数组隔离的副本；副本中的null表示空格。 */
    public StoneColor[][] snapshot() {
        return copyCells();
    }

    private StoneColor[][] copyCells() {
        StoneColor[][] copy = new StoneColor[BoardPosition.BOARD_SIZE][];
        for (int row = 0; row < copy.length; row++) {
            // 二维数组的每一行也是对象，必须逐行复制，只有外层clone仍会共享行。
            copy[row] = cells[row].clone();
        }
        return copy;
    }
}
