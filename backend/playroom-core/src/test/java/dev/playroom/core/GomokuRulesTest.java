package dev.playroom.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GomokuRulesTest {
    private static final int SIZE = BoardPosition.BOARD_SIZE;

    static Stream<Arguments> lines() {
        // start row/column, step row/column, name: all four axes touch an edge.
        int[][] axes = {{0, 0, 0, 1}, {0, 14, 1, 0}, {0, 0, 1, 1}, {0, 14, 1, -1}};
        String[] names = {"horizontal", "vertical", "diagonal", "anti-diagonal"};
        var cases = new ArrayList<Arguments>();
        for (int index = 0; index < axes.length; index++) {
            for (StoneColor color : StoneColor.values()) {
                cases.add(Arguments.of(axes[index][0], axes[index][1],
                        axes[index][2], axes[index][3], color, names[index]));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{5} {4}: final stone at either end or the middle")
    @MethodSource("lines")
    void fiveWinsAtEdgesWithLastStoneAtEitherEndOrMiddle(
            int row, int col, int dr, int dc, StoneColor color, String name) {
        for (int lastIndex : new int[]{0, 2, 4}) {
            Board before = line(row, col, dr, dc, 5, lastIndex, color);
            assertFalse(hasAnyFive(before), "Fixture must not already contain a winner");
            var last = position(row, col, dr, dc, lastIndex);
            assertEquals(winner(color), GomokuRules.evaluate(before.place(last, color), last));
            assertTrue(before.at(last).isEmpty());
        }
    }

    @ParameterizedTest(name = "{5} {4}: four is not enough")
    @MethodSource("lines")
    void exactlyFourDoesNotWin(int row, int col, int dr, int dc, StoneColor color, String name) {
        var last = position(row, col, dr, dc, 3);
        var board = line(row, col, dr, dc, 4, -1, color);
        assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board, last));
    }

    @ParameterizedTest(name = "{5} {4}: six is permitted")
    @MethodSource("lines")
    void joiningTwoShortSegmentsIntoSixWins(
            int row, int col, int dr, int dc, StoneColor color, String name) {
        var before = line(row, col, dr, dc, 6, 3, color);
        assertFalse(hasAnyFive(before));
        var last = position(row, col, dr, dc, 3);
        assertEquals(winner(color), GomokuRules.evaluate(before.place(last, color), last));
    }

    @ParameterizedTest(name = "{5} {4}: gap or opponent breaks the chain")
    @MethodSource("lines")
    void fiveSameColorStonesSeparatedByEmptyCellOrOpponentDoNotWin(
            int row, int col, int dr, int dc, StoneColor color, String name) {
        // Six positions: own, own, GAP, own, own, own (last at index 3).
        for (boolean opponentInGap : new boolean[]{false, true}) {
            var board = new Board();
            for (int index : new int[]{0, 1, 4, 5}) {
                board = board.place(position(row, col, dr, dc, index), color);
            }
            if (opponentInGap) {
                StoneColor opponent = color == StoneColor.BLACK ? StoneColor.WHITE : StoneColor.BLACK;
                board = board.place(position(row, col, dr, dc, 2), opponent);
            }
            var last = position(row, col, dr, dc, 3);
            board = board.place(last, color);
            assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board, last));
        }
    }

    @Test
    void doesNotAddCountsFromDifferentAxes() {
        var board = new Board();
        for (var pos : List.of(new BoardPosition(7, 6), new BoardPosition(7, 8),
                new BoardPosition(6, 7), new BoardPosition(8, 7))) {
            board = board.place(pos, StoneColor.BLACK);
        }
        var last = new BoardPosition(7, 7);
        assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board.place(last, StoneColor.BLACK), last));
    }

    @Test
    void originIsCountedOnceAndSingleCornerStoneIsOngoing() {
        for (var pos : List.of(new BoardPosition(0, 0), new BoardPosition(0, 14),
                new BoardPosition(14, 0), new BoardPosition(14, 14))) {
            for (var color : StoneColor.values()) {
                assertEquals(GameOutcome.ONGOING,
                        GomokuRules.evaluate(new Board().place(pos, color), pos));
            }
        }
    }

    @Test
    void evaluationLeavesCellsAndMoveCountUnchanged() {
        var last = new BoardPosition(7, 7);
        var board = new Board().place(last, StoneColor.WHITE);
        var cells = board.snapshot();
        assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board, last));
        assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board, last));
        assertTrue(Arrays.deepEquals(cells, board.snapshot()));
        assertEquals(1, board.moveCount());
    }

    @Test
    void rejectsEmptyLastCellAndNullArguments() {
        var board = new Board();
        var position = new BoardPosition(7, 7);
        assertThrows(IllegalArgumentException.class, () -> GomokuRules.evaluate(board, position));
        assertThrows(NullPointerException.class, () -> GomokuRules.evaluate(null, position));
        assertThrows(NullPointerException.class, () -> GomokuRules.evaluate(board, null));
        assertEquals(0, board.moveCount());
    }

    @Test
    void alternatedLegalPlacementsOnlyDrawOnThe225thMove() {
        var last = new BoardPosition(14, 13); // BLACK in this pattern.
        var before = fillAlternating(drawPattern(), last);
        assertEquals(224, before.moveCount());
        assertFalse(before.isFull());
        assertFalse(hasAnyFive(before));
        var full = before.place(last, StoneColor.BLACK);
        assertTrue(full.isFull());
        assertFalse(hasAnyFive(full), "Independent five-cell-window oracle validates the draw fixture");
        assertEquals(GameOutcome.DRAW, GomokuRules.evaluate(full, last));
    }

    @Test
    void legal225thMoveThatWinsTakesPrecedenceOverDraw() {
        var target = drawPattern();
        // Form a five on row 7, but leave its middle for the final black move.
        for (int col = 5; col <= 9; col++) {
            target[7][col] = StoneColor.BLACK;
        }
        // Preserve 113 black / 112 white so the full game can alternate normally.
        target[0][0] = StoneColor.WHITE;
        target[0][2] = StoneColor.WHITE;
        var last = new BoardPosition(7, 7);
        var before = fillAlternating(target, last);
        assertEquals(224, before.moveCount());
        assertFalse(hasAnyFive(before), "No historical winner is hidden in this fixture");
        var full = before.place(last, StoneColor.BLACK);
        assertTrue(full.isFull());
        assertTrue(hasAnyFive(full));
        assertEquals(GameOutcome.BLACK_WINS, GomokuRules.evaluate(full, last));
    }

    private static Board line(int row, int col, int dr, int dc, int length,
                              int omittedIndex, StoneColor color) {
        var board = new Board();
        for (int index = 0; index < length; index++) {
            if (index != omittedIndex) {
                board = board.place(position(row, col, dr, dc, index), color);
            }
        }
        return board;
    }

    private static BoardPosition position(int row, int col, int dr, int dc, int index) {
        return new BoardPosition(row + index * dr, col + index * dc);
    }

    private static GameOutcome winner(StoneColor color) {
        return color == StoneColor.BLACK ? GameOutcome.BLACK_WINS : GameOutcome.WHITE_WINS;
    }

    private static StoneColor[][] drawPattern() {
        StoneColor[][] cells = new StoneColor[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                cells[row][col] = (row + 2 * col) % 4 < 2 ? StoneColor.BLACK : StoneColor.WHITE;
            }
        }
        return cells;
    }

    private static Board fillAlternating(StoneColor[][] target, BoardPosition omittedLast) {
        var black = new ArrayList<BoardPosition>();
        var white = new ArrayList<BoardPosition>();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                var position = new BoardPosition(row, col);
                if (!position.equals(omittedLast)) {
                    (target[row][col] == StoneColor.BLACK ? black : white).add(position);
                }
            }
        }
        assertEquals(112, black.size());
        assertEquals(112, white.size());
        var board = new Board();
        for (int index = 0; index < 112; index++) {
            board = board.place(black.get(index), StoneColor.BLACK);
            assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board, black.get(index)));
            board = board.place(white.get(index), StoneColor.WHITE);
            assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board, white.get(index)));
        }
        return board;
    }

    // Independent oracle: enumerate every five-cell window on the whole board.
    // Unlike production, it neither uses the last move nor counts two rays.
    private static boolean hasAnyFive(Board board) {
        var cells = board.snapshot();
        int[][] steps = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (cells[row][col] == null) {
                    continue;
                }
                for (int[] step : steps) {
                    int endRow = row + 4 * step[0];
                    int endCol = col + 4 * step[1];
                    if (endRow < 0 || endRow >= SIZE || endCol < 0 || endCol >= SIZE) {
                        continue;
                    }
                    boolean match = true;
                    for (int offset = 1; offset < 5; offset++) {
                        match &= cells[row + offset * step[0]][col + offset * step[1]] == cells[row][col];
                    }
                    if (match) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void gapBetweenStonesDoesNotWin() {
        Board board = new Board();
        for (int col : new int[]{3, 4, 7, 8}) {
            board = board.place(new BoardPosition(7, col), StoneColor.BLACK);
        }
        BoardPosition last = new BoardPosition(7, 6);
        board = board.place(last, StoneColor.BLACK);
        assertEquals(GameOutcome.ONGOING, GomokuRules.evaluate(board, last));
    }
}
