package dev.playroom.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 不依赖数据库：证明回调失败不能污染已发布房间，成功回调前目录仍可见旧快照。 */
class PublicationBoundaryTest {
    @Test void startAndMovePublishOnlyAfterCallbackReturns() {
        var rooms = new RoomDirectory();
        var a = new Player(new PlayerId(1), "甲"); var b = new Player(new PlayerId(2), "乙");
        var code = rooms.create(a).code(); rooms.join(code, b);
        rooms.setReady(code, a.id(), true); var ready = rooms.setReady(code, b.id(), true);
        assertThrows(IllegalStateException.class, () -> rooms.startGame(code, a.id(), candidate -> {
            assertSame(ready, rooms.find(code).orElseThrow()); throw new IllegalStateException("模拟提交失败");
        }));
        assertSame(ready, rooms.find(code).orElseThrow());
        var started = rooms.startGame(code, a.id(), candidate -> assertSame(ready, rooms.find(code).orElseThrow()));
        var black = started.game().orElseThrow().blackPlayer();
        assertThrows(IllegalStateException.class, () -> rooms.play(code, black, new BoardPosition(7, 7), candidate -> {
            assertEquals(1, candidate.game().orElseThrow().board().moveCount());
            throw new IllegalStateException("模拟回滚");
        }));
        assertSame(started, rooms.find(code).orElseThrow());
        var moved = rooms.play(code, black, new BoardPosition(7, 7), candidate -> assertSame(started, rooms.find(code).orElseThrow()));
        assertEquals(1, moved.game().orElseThrow().board().moveCount());
    }
}
