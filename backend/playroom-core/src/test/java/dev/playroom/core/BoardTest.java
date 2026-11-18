package dev.playroom.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {
    private static final int SIZE = BoardPosition.BOARD_SIZE;

    private void assertSameState(StoneColor[][] expected, Board actual, int count) {
        StoneColor[][] snapshot = actual.snapshot();
        assertEquals(SIZE, snapshot.length);
        for (int row = 0; row < SIZE; row++) {
            assertArrayEquals(expected[row], snapshot[row], "row " + row);
        }
        assertEquals(count, actual.moveCount());
    }

    @Test
    void aNewBoardHas225EmptyCells() {
        var board = new Board();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                assertTrue(board.at(new BoardPosition(row, col)).isEmpty());
            }
        }
        assertEquals(0, board.moveCount());
        assertFalse(board.isFull());
    }

    @ParameterizedTest
    @EnumSource(StoneColor.class)
    void eitherColorChangesOnlyTheRequestedCellInANewBoard(StoneColor color) {
        var original = new Board();
        var position = new BoardPosition(3, 11);
        var candidate = original.place(position, color);
        assertNotSame(original, candidate);
        StoneColor[][] expected = new StoneColor[SIZE][SIZE];
        expected[3][11] = color;
        assertSameState(expected, candidate, 1);
        assertEquals(color, candidate.at(position).orElseThrow());
        assertSameState(new StoneColor[SIZE][SIZE], original, 0);
        assertFalse(candidate.isFull());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,14", "14,0", "14,14", "7,7"})
    void acceptsEveryCornerAndCenter(int row, int col) {
        var position = new BoardPosition(row, col);
        var board = new Board().place(position, StoneColor.BLACK);
        assertEquals(StoneColor.BLACK, board.at(position).orElseThrow());
        assertEquals(1, board.moveCount());
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1", "15,0", "0,15", "-1,-1", "15,15",
            "14,15",
            "-2147483648,0", "2147483647,0", "0,-2147483648", "0,2147483647"})
    void rejectsInvalidCoordinatesWithoutChangingExistingStones(int row, int col) {
        var board = new Board().place(new BoardPosition(7, 7), StoneColor.BLACK);
        var before = board.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> board.place(new BoardPosition(row, col), StoneColor.WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> board.at(new BoardPosition(row, col)));
        assertSameState(before, board, 1);
    }

    @ParameterizedTest
    @CsvSource({"BLACK,BLACK", "BLACK,WHITE", "WHITE,BLACK", "WHITE,WHITE"})
    void rejectsBothSameAndDifferentColorOnAnOccupiedCell(StoneColor first, StoneColor second) {
        var position = new BoardPosition(7, 7);
        var board = new Board().place(position, first);
        var before = board.snapshot();
        var error = assertThrows(IllegalStateException.class, () -> board.place(position, second));
        assertTrue(error.getMessage().contains("occupied"));
        assertSameState(before, board, 1);
    }

    @Test
    void rejectsNullPositionAndColorWithoutChangingTheBoard() {
        var board = new Board().place(new BoardPosition(0, 0), StoneColor.BLACK);
        var before = board.snapshot();
        assertThrows(NullPointerException.class, () -> board.at(null));
        assertThrows(NullPointerException.class, () -> board.place(null, StoneColor.WHITE));
        assertThrows(NullPointerException.class, () -> board.place(new BoardPosition(1, 1), null));
        assertSameState(before, board, 1);
    }

    @Test
    void extendingACandidatePreservesItsEntireHistory() {
        var empty = new Board();
        var first = empty.place(new BoardPosition(2, 3), StoneColor.BLACK);
        var before = first.snapshot();
        var second = first.place(new BoardPosition(3, 2), StoneColor.WHITE);
        StoneColor[][] expected = first.snapshot();
        expected[3][2] = StoneColor.WHITE;
        assertSameState(expected, second, 2);
        assertSameState(before, first, 1);
        assertSameState(new StoneColor[SIZE][SIZE], empty, 0);
    }

    @Test
    void twoCandidatesFromTheSameBaseAreIndependent() {
        var base = new Board();
        var position = new BoardPosition(7, 7);
        var blackCandidate = base.place(position, StoneColor.BLACK);
        var whiteCandidate = base.place(position, StoneColor.WHITE);
        assertEquals(StoneColor.BLACK, blackCandidate.at(position).orElseThrow());
        assertEquals(StoneColor.WHITE, whiteCandidate.at(position).orElseThrow());
        assertTrue(base.at(position).isEmpty());
        assertEquals(1, blackCandidate.moveCount());
        assertEquals(1, whiteCandidate.moveCount());
    }

    @Test
    void changingSnapshotCellsOrRowsCannotChangeAnyBoard() {
        var board = new Board().place(new BoardPosition(4, 9), StoneColor.BLACK);
        var candidate = board.place(new BoardPosition(5, 9), StoneColor.WHITE);
        var before = board.snapshot();
        var candidateBefore = candidate.snapshot();
        var exported = board.snapshot();
        exported[4][9] = StoneColor.WHITE;
        exported[0][0] = StoneColor.BLACK;
        exported[5] = new StoneColor[SIZE];
        exported[4] = null;
        assertSameState(before, board, 1);
        assertSameState(candidateBefore, candidate, 2);
    }

    @Test
    void separatelyCreatedBoardsDoNotShareState() {
        var other = new Board();
        new Board().place(new BoardPosition(0, 0), StoneColor.BLACK);
        assertSameState(new StoneColor[SIZE][SIZE], other, 0);
    }

    @Test
    void fullBoardCountsExactly225MovesAndRejectsFurtherPlacement() {
        var board = new Board();
        StoneColor[][] expected = new StoneColor[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                assertFalse(board.isFull());
                var color = (row + col) % 2 == 0 ? StoneColor.BLACK : StoneColor.WHITE;
                board = board.place(new BoardPosition(row, col), color);
                expected[row][col] = color;
                assertEquals(row * SIZE + col + 1, board.moveCount());
            }
        }
        assertTrue(board.isFull());
        assertSameState(expected, board, 225);
        Board full = board;
        assertThrows(IllegalStateException.class,
                () -> full.place(new BoardPosition(14, 14), StoneColor.WHITE));
        assertSameState(expected, full, 225);
    }
}
