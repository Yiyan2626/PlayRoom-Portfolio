package dev.playroom.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 目录集成测试确认新状态被保存，失败操作不会发布候选，也不影响其他房间。 */
class RoomDirectoryLifecycleTest {
    private final Player host = new Player(new PlayerId(7), "Chen");
    private final Player guest = new Player(new PlayerId(8), "Lin");
    private final RoomCode code = new RoomCode("ABC123");
    private final UUID id = new UUID(0, 1);

    private Room prepare(RoomDirectory directory) {
        directory.create(host);
        directory.join(code, guest);
        directory.setReady(code, host.id(), true);
        return directory.setReady(code, guest.id(), true);
    }

    @Test
    void completeDirectoryFlowPublishesSnapshotsAndSwapsRematchWithoutAnotherRandomChoice() {
        var ids = new ArrayDeque<>(List.of(id, id, new UUID(0, 2)));
        var colorCalls = new AtomicInteger();
        var directory = new RoomDirectory(() -> code, ids::remove, () -> {
            colorCalls.incrementAndGet();
            return false;
        });
        var ready = prepare(directory);
        var initial = directory.startGame(code, host.id());
        assertSame(initial, directory.find(code).orElseThrow());
        assertEquals(guest.id(), initial.game().orElseThrow().blackPlayer());
        var one = directory.play(code, guest.id(), new BoardPosition(0, 0));
        assertEquals(1, one.game().orElseThrow().board().moveCount());
        assertEquals(0, initial.game().orElseThrow().board().moveCount());
        var ended = directory.resign(code, host.id());
        assertSame(ended, directory.find(code).orElseThrow());
        directory.setReady(code, host.id(), true);
        directory.setReady(code, guest.id(), true);
        var next = directory.startGame(code, guest.id());
        assertEquals(new UUID(0, 2), next.game().orElseThrow().id());
        assertEquals(host.id(), next.game().orElseThrow().blackPlayer());
        assertEquals(1, colorCalls.get());
        assertEquals(0, next.game().orElseThrow().board().moveCount());
        assertEquals(RoomPhase.READY, ready.phase());
        assertTrue(ids.isEmpty());
        assertEquals(1, directory.roomCount());
    }

    @Test
    void everyRejectedActionPreservesPublishedSnapshotAndValidPlayCanContinue() {
        var directory = new RoomDirectory(() -> code, () -> id, () -> true);
        var ready = prepare(directory);
        assertCode(RoomException.Code.NOT_MEMBER, () -> directory.setReady(code, new PlayerId(9), true));
        assertSame(ready, directory.find(code).orElseThrow());
        var playing = directory.startGame(code, host.id());
        assertCode(RoomException.Code.NOT_YOUR_TURN, () -> directory.play(code, guest.id(), new BoardPosition(0, 0)));
        assertCode(RoomException.Code.NOT_MEMBER, () -> directory.resign(code, new PlayerId(9)));
        assertCode(RoomException.Code.GAME_IN_PROGRESS, () -> directory.setReady(code, host.id(), true));
        assertCode(RoomException.Code.GAME_IN_PROGRESS, () -> directory.startGame(code, host.id()));
        assertSame(playing, directory.find(code).orElseThrow());
        // 越界在坐标构造阶段即被拒绝，既没有进入业务方法，也不会改动目录。
        assertThrows(IllegalArgumentException.class,
                () -> directory.play(code, host.id(), new BoardPosition(15, 0)));
        assertThrows(NullPointerException.class, () -> directory.play(code, host.id(), null));
        assertSame(playing, directory.find(code).orElseThrow());
        var one = directory.play(code, host.id(), new BoardPosition(0, 0));
        assertCode(RoomException.Code.CELL_OCCUPIED, () -> directory.play(code, guest.id(), new BoardPosition(0, 0)));
        assertSame(one, directory.find(code).orElseThrow());
        var ended = directory.resign(code, guest.id());
        assertCode(RoomException.Code.GAME_FINISHED, () -> directory.play(code, host.id(), new BoardPosition(0, 1)));
        assertCode(RoomException.Code.GAME_FINISHED, () -> directory.resign(code, host.id()));
        assertSame(ended, directory.find(code).orElseThrow());
    }

    @Test
    void invalidStartDoesNotConsumeIdOrColorAndDoesNotClearReadiness() {
        var calls = new AtomicInteger();
        var directory = new RoomDirectory(() -> code, () -> {
            calls.incrementAndGet();
            return id;
        }, () -> { fail("Invalid start must not choose a color"); return true; });
        var hostOnly = directory.create(host);
        assertCode(RoomException.Code.ROOM_NOT_READY, () -> directory.startGame(code, host.id()));
        assertCode(RoomException.Code.NOT_MEMBER, () -> directory.startGame(code, guest.id()));
        assertSame(hostOnly, directory.find(code).orElseThrow());
        assertEquals(0, calls.get());
    }

    @Test
    void repeatedIdsExhaustBoundedRetryAndKeepRoomReady() {
        var calls = new AtomicInteger();
        var directory = new RoomDirectory(() -> code, () -> {
            calls.incrementAndGet();
            return id;
        }, () -> true);
        prepare(directory);
        directory.startGame(code, host.id());
        directory.resign(code, guest.id());
        directory.setReady(code, host.id(), true);
        var ready = directory.setReady(code, guest.id(), true);
        assertCode(RoomException.Code.GAME_ID_EXHAUSTED, () -> directory.startGame(code, host.id()));
        assertEquals(1 + RoomDirectory.MAX_GAME_ID_ATTEMPTS, calls.get());
        assertSame(ready, directory.find(code).orElseThrow());
        assertEquals(RoomPhase.READY, ready.phase());
    }

    @Test
    void gameIdsAreNotReusedAcrossRoomsOrOlderRematches() {
        var codes = new ArrayDeque<>(List.of(code, new RoomCode("DEF456")));
        var ids = new ArrayDeque<>(List.of(id, id, new UUID(0, 2), id, new UUID(0, 2), new UUID(0, 3)));
        var directory = new RoomDirectory(codes::remove, ids::remove, () -> true);
        prepare(directory);
        var first = directory.startGame(code, host.id());
        var otherCode = directory.create(host).code();
        directory.join(otherCode, guest);
        directory.setReady(otherCode, host.id(), true);
        directory.setReady(otherCode, guest.id(), true);
        var other = directory.startGame(otherCode, host.id());
        assertEquals(new UUID(0, 2), other.game().orElseThrow().id());
        directory.resign(code, guest.id());
        directory.setReady(code, host.id(), true);
        directory.setReady(code, guest.id(), true);
        var next = directory.startGame(code, host.id());
        assertEquals(new UUID(0, 3), next.game().orElseThrow().id());
        assertSame(other, directory.find(otherCode).orElseThrow());
        assertEquals(GameStatus.ACTIVE, first.game().orElseThrow().status());
        assertTrue(ids.isEmpty());
    }

    @Test
    void generatorFailureDoesNotPublishOrReserveTheId() {
        var colorCalls = new AtomicInteger();
        var directory = new RoomDirectory(() -> code, () -> id, () -> {
            if (colorCalls.incrementAndGet() == 1) throw new IllegalStateException("random failed");
            return true;
        });
        var ready = prepare(directory);
        assertThrows(IllegalStateException.class, () -> directory.startGame(code, host.id()));
        assertSame(ready, directory.find(code).orElseThrow());
        assertEquals(id, directory.startGame(code, host.id()).game().orElseThrow().id());
        var badId = new RoomDirectory(() -> code, () -> null, () -> true);
        var badReady = prepare(badId);
        assertThrows(NullPointerException.class, () -> badId.startGame(code, host.id()));
        assertSame(badReady, badId.find(code).orElseThrow());
    }

    @Test
    void missingRoomsReturnBusinessErrorForEveryNewAction() {
        var directory = new RoomDirectory();
        assertCode(RoomException.Code.ROOM_NOT_FOUND, () -> directory.setReady(code, host.id(), true));
        assertCode(RoomException.Code.ROOM_NOT_FOUND, () -> directory.startGame(code, host.id()));
        assertCode(RoomException.Code.ROOM_NOT_FOUND, () -> directory.play(code, host.id(), new BoardPosition(0, 0)));
        assertCode(RoomException.Code.ROOM_NOT_FOUND, () -> directory.resign(code, host.id()));
        assertEquals(0, directory.roomCount());
        assertThrows(NullPointerException.class, () -> new RoomDirectory(() -> code, null, () -> true));
        assertThrows(NullPointerException.class, () -> new RoomDirectory(() -> code, () -> id, null));
    }

    @Test
    void defaultSuppliersStartARealInMemoryGame() {
        var directory = new RoomDirectory();
        var generatedCode = directory.create(host).code();
        directory.join(generatedCode, guest);
        directory.setReady(generatedCode, host.id(), true);
        directory.setReady(generatedCode, guest.id(), true);
        var game = directory.startGame(generatedCode, host.id()).game().orElseThrow();
        assertNotNull(game.id());
        assertEquals(GameStatus.ACTIVE, game.status());
        assertEquals(0, game.board().moveCount());
        assertNotEquals(game.blackPlayer(), game.whitePlayer());
    }

    private static void assertCode(RoomException.Code expected, Runnable operation) {
        assertEquals(expected, assertThrows(RoomException.class, operation::run).code());
    }
}
