package dev.playroom.core;

import java.util.Objects;

/** 自由五子棋：任一颜色连续至少五颗即获胜，长连也算胜利。 */
public final class GomokuRules {
    // 行列增量：横、竖、右下斜线和左下斜线，每条线还要检查反方向。
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private GomokuRules() {
    }

    /**
     * 在一次落子后增量判定：要求旧盘尚无胜者，且只有lastMove这一格变化。
     * 因而新出现的连线必经过最后落子；不能用此方法校验任意导入棋盘的全部历史。
     */
    public static GameOutcome evaluate(Board board, BoardPosition lastMove) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(lastMove, "lastMove");
        StoneColor color = board.at(lastMove).orElseThrow(
                () -> new IllegalArgumentException("Last move must contain a stone"));

        for (int[] direction : DIRECTIONS) {
            int dr = direction[0];
            int dc = direction[1];
            // 原点只算一次，向两侧遇到空格、异色或边界就停止，不能跨空格累加。
            int count = 1
                    + countOneSide(board, lastMove, color, dr, dc)
                    + countOneSide(board, lastMove, color, -dr, -dc);
            if (count >= 5) {
                return color == StoneColor.BLACK ? GameOutcome.BLACK_WINS : GameOutcome.WHITE_WINS;
            }
        }
        // 先检查胜利，再判断满盘；最后一格成五连时仍是获胜，不能误判平局。
        return board.isFull() ? GameOutcome.DRAW : GameOutcome.ONGOING;
    }

    private static int countOneSide(Board board, BoardPosition origin, StoneColor color,
                                    int dr, int dc) {
        int row = origin.row() + dr;
        int col = origin.col() + dc;
        int count = 0;
        // &&从左到右短路；先判边界，再构造坐标/访问数组，边缘不会越界。
        while (isInside(row, col)
                && board.at(new BoardPosition(row, col)).orElse(null) == color) {
            count++;
            row += dr;
            col += dc;
        }
        return count;
    }

    private static boolean isInside(int row, int col) {
        return row >= 0 && row < BoardPosition.BOARD_SIZE
                && col >= 0 && col < BoardPosition.BOARD_SIZE;
    }
}
