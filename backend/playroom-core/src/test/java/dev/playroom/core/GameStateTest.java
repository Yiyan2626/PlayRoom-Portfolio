package dev.playroom.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 通过真实交替落子到终局，验证GameState确实把身份、回合和D03规则串在一起。 */
class GameStateTest {
    private static final PlayerId BLACK = new PlayerId(7);
    private static final PlayerId WHITE = new PlayerId(8);
    private static final PlayerId OUTSIDER = new PlayerId(9);
    private static final UUID ID = new UUID(0, 1);

    private static GameState start() { return GameState.start(ID, BLACK, WHITE); }

    @Test
    void startsWithEmptyBoardBlackTurnAndFixedParticipants() {
        var game = start();
        assertEquals(ID, game.id());
        assertEquals(BLACK, game.blackPlayer());
        assertEquals(WHITE, game.whitePlayer());
        assertEquals(0, game.board().moveCount());
        assertEquals(GameStatus.ACTIVE, game.status());
        assertEquals(StoneColor.BLACK, game.nextColor().orElseThrow());
        assertEquals(StoneColor.BLACK, game.colorOf(new PlayerId(7)).orElseThrow());
        assertEquals(StoneColor.WHITE, game.colorOf(WHITE).orElseThrow());
        assertTrue(game.colorOf(OUTSIDER).isEmpty());
        assertTrue(game.endReason().isEmpty());
        assertTrue(game.winner().isEmpty());
    }

    @Test
    void acceptedMovesAlternateTurnsAndPreservePreviousSnapshots() {
        var empty = start();
        var one = empty.play(BLACK, new BoardPosition(7, 7));
        var two = one.play(WHITE, new BoardPosition(7, 8));
        assertEquals(0, empty.board().moveCount());
        assertEquals(1, one.board().moveCount());
        assertEquals(2, two.board().moveCount());
        assertEquals(StoneColor.WHITE, one.nextColor().orElseThrow());
        assertEquals(StoneColor.BLACK, two.nextColor().orElseThrow());
        assertEquals(StoneColor.BLACK, two.board().at(new BoardPosition(7, 7)).orElseThrow());
        assertEquals(StoneColor.WHITE, two.board().at(new BoardPosition(7, 8)).orElseThrow());
        assertTrue(one.board().at(new BoardPosition(7, 8)).isEmpty());
        assertEquals(ID, two.id());
    }

    @Test
    void outsidersAndWrongTurnsCannotMoveOrChangeTheBoard() {
        var game = start();
        assertCode(RoomException.Code.NOT_MEMBER, () -> game.play(OUTSIDER, new BoardPosition(0, 0)));
        assertCode(RoomException.Code.NOT_YOUR_TURN, () -> game.play(WHITE, new BoardPosition(0, 0)));
        assertEquals(0, game.board().moveCount());
        var one = game.play(BLACK, new BoardPosition(0, 0));
        assertCode(RoomException.Code.NOT_YOUR_TURN, () -> one.play(BLACK, new BoardPosition(0, 1)));
        assertCode(RoomException.Code.CELL_OCCUPIED, () -> one.play(WHITE, new BoardPosition(0, 0)));
        // 拒绝占格后仍然轮到白方，合法操作还可以继续。
        assertEquals(2, one.play(WHITE, new BoardPosition(0, 1)).board().moveCount());
        assertEquals(1, one.board().moveCount());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,1", "0,0,1,0", "0,0,1,1", "0,14,1,-1"})
    void bothColorsCanWinOnAllFourLines(int row, int col, int dr, int dc) {
        for (var winnerColor : StoneColor.values()) {
            var game = start();
            for (int index = 0; index < 5; index++) {
                var winningCell = new BoardPosition(row + index * dr, col + index * dc);
                var decoy = new BoardPosition(14, index * 2);
                if (winnerColor == StoneColor.BLACK) {
                    game = game.play(BLACK, winningCell);
                    if (index < 4) game = game.play(WHITE, decoy);
                } else {
                    game = game.play(BLACK, decoy).play(WHITE, winningCell);
                }
            }
            PlayerId expected = winnerColor == StoneColor.BLACK ? BLACK : WHITE;
            assertEquals(GameStatus.FINISHED, game.status());
            assertEquals(GameEndReason.FIVE_IN_ROW, game.endReason().orElseThrow());
            assertEquals(expected, game.winner().orElseThrow());
            assertTrue(game.nextColor().isEmpty());
            assertEquals(winnerColor == StoneColor.BLACK ? 9 : 10, game.board().moveCount());
            var finished = game;
            assertCode(RoomException.Code.GAME_FINISHED, () -> finished.play(WHITE, new BoardPosition(6, 6)));
            assertCode(RoomException.Code.GAME_FINISHED, () -> finished.resign(BLACK));
        }
    }

    @Test
    void joiningTwoSegmentsIntoAnOverlineStillWins() {
        var game = start();
        int[] columns = {0, 1, 3, 4, 5};
        for (int index = 0; index < columns.length; index++) {
            game = game.play(BLACK, new BoardPosition(7, columns[index]))
                    .play(WHITE, new BoardPosition(14, index * 2));
        }
        assertEquals(GameStatus.ACTIVE, game.status());
        game = game.play(BLACK, new BoardPosition(7, 2));
        assertEquals(BLACK, game.winner().orElseThrow());
        assertEquals(GameEndReason.FIVE_IN_ROW, game.endReason().orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(StoneColor.class)
    void eitherPlayerCanResignEvenOutOfTurnWithoutAddingAStone(StoneColor color) {
        var before = start().play(BLACK, new BoardPosition(7, 7));
        PlayerId loser = color == StoneColor.BLACK ? BLACK : WHITE;
        PlayerId winner = color == StoneColor.BLACK ? WHITE : BLACK;
        var ended = before.resign(loser);
        assertEquals(GameStatus.ACTIVE, before.status());
        assertEquals(GameStatus.FINISHED, ended.status());
        assertEquals(GameEndReason.RESIGNATION, ended.endReason().orElseThrow());
        assertEquals(winner, ended.winner().orElseThrow());
        assertSame(before.board(), ended.board());
        assertEquals(1, before.version());
        assertEquals(2, ended.version()); // 认输是第二次状态变化，棋子仍只有一颗。
        assertEquals(1, ended.board().moveCount());
        assertTrue(ended.nextColor().isEmpty());
        assertCode(RoomException.Code.GAME_FINISHED, () -> ended.resign(loser));
        assertCode(RoomException.Code.GAME_FINISHED, () -> ended.play(winner, new BoardPosition(7, 8)));
    }

    @Test
    void outsiderCannotResignForEitherPlayer() {
        var game = start();
        assertCode(RoomException.Code.NOT_MEMBER, () -> game.resign(OUTSIDER));
        assertEquals(GameStatus.ACTIVE, game.status());
    }

    @Test
    void fullBoardWithoutFiveEndsAsDrawWithNoWinner() {
        var game = fill224(false);
        assertEquals(224, game.board().moveCount());
        game = game.play(BLACK, new BoardPosition(14, 13));
        assertEquals(225, game.board().moveCount());
        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameEndReason.BOARD_FULL, game.endReason().orElseThrow());
        assertTrue(game.winner().isEmpty());
        assertTrue(game.nextColor().isEmpty());
        var ended = game;
        assertCode(RoomException.Code.GAME_FINISHED, () -> ended.resign(WHITE));
    }

    @Test
    void winning225thMoveIsAWinRatherThanADraw() {
        var before = fill224(true);
        var game = before.play(BLACK, new BoardPosition(7, 7));
        assertTrue(game.board().isFull());
        assertEquals(GameEndReason.FIVE_IN_ROW, game.endReason().orElseThrow());
        assertEquals(BLACK, game.winner().orElseThrow());
        assertEquals(GameStatus.ACTIVE, before.status());
    }

    // D03已用独立全盘窗口oracle核对该图案；这里真实走完224步以检查回合集成。
    private static GameState fill224(boolean lastMoveWins) {
        var black = new ArrayList<BoardPosition>();
        var white = new ArrayList<BoardPosition>();
        var last = lastMoveWins ? new BoardPosition(7, 7) : new BoardPosition(14, 13);
        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                var position = new BoardPosition(row, col);
                boolean isBlack = (row + 2 * col) % 4 < 2;
                if (lastMoveWins && row == 7 && col >= 5 && col <= 9) isBlack = true;
                if (lastMoveWins && row == 0 && (col == 0 || col == 2)) isBlack = false;
                if (!position.equals(last)) (isBlack ? black : white).add(position);
            }
        }
        assertEquals(112, black.size());
        assertEquals(112, white.size());
        var game = start();
        for (int index = 0; index < 112; index++) {
            game = game.play(BLACK, black.get(index));
            assertEquals(GameStatus.ACTIVE, game.status());
            game = game.play(WHITE, white.get(index));
            assertEquals(GameStatus.ACTIVE, game.status());
        }
        return game;
    }

    @Test
    void rejectsNullAndDuplicateParticipants() {
        assertThrows(NullPointerException.class, () -> GameState.start(null, BLACK, WHITE));
        assertThrows(NullPointerException.class, () -> GameState.start(ID, null, WHITE));
        assertThrows(NullPointerException.class, () -> GameState.start(ID, BLACK, null));
        assertThrows(IllegalArgumentException.class, () -> GameState.start(ID, BLACK, new PlayerId(7)));
        var game = start();
        assertThrows(NullPointerException.class, () -> game.play(BLACK, null));
        assertThrows(NullPointerException.class, () -> game.play(null, new BoardPosition(0, 0)));
        assertThrows(NullPointerException.class, () -> game.resign(null));
    }

    private static void assertCode(RoomException.Code code, Runnable operation) {
        assertEquals(code, assertThrows(RoomException.class, operation::run).code());
    }
}
