package dev.playroom.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 第一周纵向验收：通过目录入口贯通规则、状态和发布，首局两种黑白都要通过。 */
class WeekOneAcceptanceTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void playToFiveThenRematchAndResignPreservesEverySnapshot(boolean firstSeatBlack) {
        var host = new Player(new PlayerId(7), "Chen");
        var guest = new Player(new PlayerId(8), "Lin");
        var code = new RoomCode("ABC123");
        var ids = new AtomicInteger();
        var directory = new RoomDirectory(() -> code,
                () -> new UUID(0, ids.incrementAndGet()), () -> firstSeatBlack);

        var created = directory.create(host);
        var joined = directory.join(new RoomCode(" abc123 "), guest);
        rejectWithoutPublishing(directory, code, RoomException.Code.ROOM_FULL,
                () -> directory.join(code, new Player(new PlayerId(9), "Third")));
        rejectWithoutPublishing(directory, code, RoomException.Code.ROOM_NOT_READY,
                () -> directory.startGame(code, host.id()));
        directory.setReady(code, host.id(), true);
        var ready = directory.setReady(code, guest.id(), true);
        var initial = directory.startGame(code, guest.id());
        PlayerId black = firstSeatBlack ? host.id() : guest.id();
        PlayerId white = firstSeatBlack ? guest.id() : host.id();
        assertEquals(black, initial.game().orElseThrow().blackPlayer());

        // 满员且局中仍允许已有ID重入；昵称不更新，快照对象也不变化。
        assertSame(initial, directory.join(code, new Player(new PlayerId(7), "新昵称")));
        assertEquals("Chen", directory.find(code).orElseThrow().members().getFirst().nickname());
        var one = directory.play(code, black, new BoardPosition(0, 0));
        rejectWithoutPublishing(directory, code, RoomException.Code.NOT_YOUR_TURN,
                () -> directory.play(code, black, new BoardPosition(0, 1)));
        rejectWithoutPublishing(directory, code, RoomException.Code.CELL_OCCUPIED,
                () -> directory.play(code, white, new BoardPosition(0, 0)));

        // 白棋始终分散在底边，不意外先赢；黑棋第九步在顶边恰好连成五颗。
        for (int col = 1; col <= 4; col++) {
            directory.play(code, white, new BoardPosition(14, (col - 1) * 2));
            var afterBlack = directory.play(code, black, new BoardPosition(0, col));
            assertEquals(col < 4 ? RoomPhase.PLAYING : RoomPhase.FINISHED, afterBlack.phase());
            assertEquals(col * 2 + 1, afterBlack.game().orElseThrow().board().moveCount());
        }
        var ended = directory.find(code).orElseThrow();
        var first = ended.game().orElseThrow();
        assertEquals(GameEndReason.FIVE_IN_ROW, first.endReason().orElseThrow());
        assertEquals(black, first.winner().orElseThrow());
        assertTrue(first.nextColor().isEmpty());
        rejectWithoutPublishing(directory, code, RoomException.Code.GAME_FINISHED,
                () -> directory.play(code, white, new BoardPosition(14, 8)));

        directory.setReady(code, host.id(), true);
        rejectWithoutPublishing(directory, code, RoomException.Code.ROOM_NOT_READY,
                () -> directory.startGame(code, host.id()));
        directory.setReady(code, guest.id(), true);
        var rematch = directory.startGame(code, host.id());
        var next = rematch.game().orElseThrow();
        assertNotEquals(first.id(), next.id());
        assertEquals(white, next.blackPlayer());
        assertEquals(black, next.whitePlayer());
        assertEquals(0, next.board().moveCount());
        assertTrue(rematch.readyPlayers().isEmpty());
        assertEquals(joined.members(), rematch.members());
        assertEquals(code, rematch.code());

        // 下一局白方可以在黑方尚未落子时认输；不会凭空增加一步。
        var resigned = directory.resign(code, black);
        assertEquals(RoomPhase.FINISHED, resigned.phase());
        assertEquals(GameEndReason.RESIGNATION, resigned.game().orElseThrow().endReason().orElseThrow());
        assertEquals(white, resigned.game().orElseThrow().winner().orElseThrow());
        assertEquals(0, resigned.game().orElseThrow().board().moveCount());
        assertSame(resigned, directory.find(code).orElseThrow());

        // 全流程结束后再检查历史，能发现底层数组共享导致的快照污染。
        assertEquals(1, created.members().size());
        assertEquals(RoomPhase.WAITING, joined.phase());
        assertEquals(RoomPhase.READY, ready.phase());
        assertEquals(0, initial.game().orElseThrow().board().moveCount());
        assertTrue(initial.game().orElseThrow().board().at(new BoardPosition(0, 0)).isEmpty());
        assertEquals(1, one.game().orElseThrow().board().moveCount());
        assertTrue(one.game().orElseThrow().board().at(new BoardPosition(0, 1)).isEmpty());
        assertEquals(9, first.board().moveCount());
        assertEquals(black, first.winner().orElseThrow());
        assertEquals(GameStatus.ACTIVE, next.status());
        assertEquals(1, directory.roomCount());
    }

    private static void rejectWithoutPublishing(RoomDirectory directory, RoomCode code,
                                                RoomException.Code expected, Runnable action) {
        var before = directory.find(code).orElseThrow();
        assertEquals(expected, assertThrows(RoomException.class, action::run).code());
        assertSame(before, directory.find(code).orElseThrow());
    }
}
