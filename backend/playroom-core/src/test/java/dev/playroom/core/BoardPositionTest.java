package dev.playroom.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardPositionTest {
    @ParameterizedTest
    @CsvSource({"0,0", "0,14", "14,0", "14,14", "7,7"})
    void acceptsCornersAndCenter(int row, int col) {
        var position = new BoardPosition(row, col);
        assertEquals(row, position.row());
        assertEquals(col, position.col());
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1", "15,0", "0,15", "-1,-1", "15,15"})
    void rejectsPositionsBeyondEveryEdge(int row, int col) {
        assertThrows(IllegalArgumentException.class, () -> new BoardPosition(row, col));
    }

    @Test
    void positionsWithTheSameCoordinatesAreTheSameMapKey() {
        var positions = new java.util.HashMap<BoardPosition, String>();
        positions.put(new BoardPosition(7, 7), "black");
        assertEquals("black", positions.get(new BoardPosition(7, 7)));
        positions.put(new BoardPosition(7, 7), "white");
        assertEquals(1, positions.size());
        assertEquals("white", positions.get(new BoardPosition(7, 7)));
    }
}
