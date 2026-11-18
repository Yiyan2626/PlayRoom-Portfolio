package dev.playroom.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 按业务阶段检查允许与禁止的操作，而不只检查一个方法的返回值。 */
class RoomLifecycleTest {
    private final Player host = new Player(new PlayerId(7), "Chen");
    private final Player guest = new Player(new PlayerId(8), "Lin");
    private final PlayerId outsider = new PlayerId(9);
    private final RoomCode code = new RoomCode("ABC123");
    private final UUID id = new UUID(0, 1);

    private Room twoPlayers() { return Room.create(code, host).join(guest); }
    private Room readyRoom() { return twoPlayers().setReady(host.id(), true).setReady(guest.id(), true); }

    @Test
    void oneReadyPlayerCannotStartAndReadinessSurvivesSecondJoin() {
        var initial = Room.create(code, host);
        assertEquals(RoomPhase.WAITING, initial.phase());
        assertTrue(initial.game().isEmpty());
        var oneReady = initial.setReady(host.id(), true);
        assertSame(oneReady, oneReady.setReady(new PlayerId(7), true));
        assertCode(RoomException.Code.ROOM_NOT_READY, () -> oneReady.startGame(host.id(), id, true));
        var joined = oneReady.join(guest);
        assertEquals(Set.of(host.id()), joined.readyPlayers());
        assertEquals(RoomPhase.WAITING, joined.phase());
        assertTrue(initial.readyPlayers().isEmpty());
        assertCode(RoomException.Code.ROOM_NOT_READY, () -> joined.startGame(host.id(), id, true));
    }

    @Test
    void settingAndUnsettingReadinessAreIdempotentAndRequireBothPlayers() {
        var room = twoPlayers();
        assertSame(room, room.setReady(host.id(), false));
        var ready = room.setReady(host.id(), true).setReady(guest.id(), true);
        assertEquals(RoomPhase.READY, ready.phase());
        assertSame(ready, ready.setReady(guest.id(), true));
        assertEquals(RoomPhase.WAITING, ready.setReady(host.id(), false).phase());
        assertEquals(RoomPhase.READY, ready.phase());
        assertThrows(UnsupportedOperationException.class, () -> ready.readyPlayers().clear());
        assertCode(RoomException.Code.NOT_MEMBER, () -> ready.setReady(outsider, true));
        assertCode(RoomException.Code.NOT_MEMBER, () -> ready.startGame(outsider, id, true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void eitherFirstGameColorAssignmentIsPossibleAndSeatsStayStable(boolean firstSeatBlack) {
        var before = readyRoom();
        // 任一成员可触发开局；两人准备是前置条件，而不是只允许房主启动。
        var playing = before.startGame(guest.id(), id, firstSeatBlack);
        var game = playing.game().orElseThrow();
        assertEquals(RoomPhase.PLAYING, playing.phase());
        assertEquals(firstSeatBlack ? host.id() : guest.id(), game.blackPlayer());
        assertEquals(firstSeatBlack ? guest.id() : host.id(), game.whitePlayer());
        assertEquals(StoneColor.BLACK, game.nextColor().orElseThrow());
        assertTrue(playing.readyPlayers().isEmpty());
        assertEquals(RoomPhase.READY, before.phase());
        assertEquals(Seat.FIRST, playing.seatOf(host.id()).orElseThrow());
        assertEquals(Seat.SECOND, playing.seatOf(guest.id()).orElseThrow());
    }

    @Test
    void playingRoomRejectsReadinessAndSecondStartButAllowsReentry() {
        var room = readyRoom().startGame(host.id(), id, true);
        assertCode(RoomException.Code.GAME_IN_PROGRESS, () -> room.setReady(host.id(), true));
        assertCode(RoomException.Code.GAME_IN_PROGRESS, () -> room.setReady(host.id(), false));
        assertCode(RoomException.Code.GAME_IN_PROGRESS, () -> room.startGame(host.id(), new UUID(0, 2), true));
        assertSame(room, room.join(new Player(new PlayerId(7), "新昵称")));
        assertEquals(0, room.game().orElseThrow().board().moveCount());

        // 首局传入true，所以host执黑；落子返回新快照one，原room仍是空盘。
        var one = room.play(host.id(), new BoardPosition(7, 7));
        assertEquals(RoomPhase.PLAYING, one.phase());
        assertEquals(0, room.game().orElseThrow().board().moveCount());

        // 现在轮到白棋；黑棋抢下应抛出指定异常，步数、目标格和回合都不变。
        assertCode(RoomException.Code.NOT_YOUR_TURN,
                () -> one.play(host.id(), new BoardPosition(7, 8)));
        assertEquals(1, one.game().orElseThrow().board().moveCount());
        assertTrue(one.game().orElseThrow().board().at(new BoardPosition(7, 8)).isEmpty());
        assertEquals(StoneColor.WHITE, one.game().orElseThrow().nextColor().orElseThrow());

        // 被拒绝后白棋仍能在同一格正常落子；用two接住新状态，one仍保留一步。
        var two = one.play(guest.id(), new BoardPosition(7, 8));
        assertEquals(2, two.game().orElseThrow().board().moveCount());
        assertEquals(StoneColor.WHITE,
                two.game().orElseThrow().board().at(new BoardPosition(7, 8)).orElseThrow());
        assertEquals(StoneColor.BLACK, two.game().orElseThrow().nextColor().orElseThrow());
        assertEquals(1, one.game().orElseThrow().board().moveCount());
    }

    @Test
    void playingAndResigningNeedMembershipAndAnActualGame() {
        var waiting = twoPlayers();
        assertCode(RoomException.Code.GAME_NOT_STARTED, () -> waiting.play(host.id(), new BoardPosition(0, 0)));
        assertCode(RoomException.Code.GAME_NOT_STARTED, () -> waiting.resign(host.id()));
        assertCode(RoomException.Code.NOT_MEMBER, () -> waiting.play(outsider, new BoardPosition(0, 0)));
        assertCode(RoomException.Code.NOT_MEMBER, () -> waiting.resign(outsider));
        var active = readyRoom().startGame(host.id(), id, true);
        assertCode(RoomException.Code.NOT_MEMBER, () -> active.resign(outsider));
        assertCode(RoomException.Code.NOT_MEMBER, () -> active.play(outsider, new BoardPosition(0, 0)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rematchAfterResignationNeedsFreshReadinessNewIdAndSwappedColors(boolean firstSeatBlack) {
        var first = readyRoom().startGame(host.id(), id, firstSeatBlack);
        var oldGame = first.game().orElseThrow();
        var oneMove = first.play(oldGame.blackPlayer(), new BoardPosition(7, 7));
        var ended = oneMove.resign(oldGame.whitePlayer());
        var oldFinished = ended.game().orElseThrow();
        assertEquals(RoomPhase.FINISHED, ended.phase());
        assertTrue(ended.readyPlayers().isEmpty());
        assertCode(RoomException.Code.GAME_FINISHED, () -> ended.play(host.id(), new BoardPosition(7, 8)));
        assertCode(RoomException.Code.GAME_FINISHED, () -> ended.resign(guest.id()));
        assertCode(RoomException.Code.ROOM_NOT_READY, () -> ended.startGame(host.id(), new UUID(0, 2), true));
        var oneReady = ended.setReady(host.id(), true);
        assertEquals(RoomPhase.FINISHED, oneReady.phase());
        var ready = oneReady.setReady(guest.id(), true);
        assertEquals(RoomPhase.READY, ready.phase());
        assertEquals(RoomPhase.FINISHED, ready.setReady(host.id(), false).phase());
        assertCode(RoomException.Code.GAME_ID_REUSED, () -> ready.startGame(host.id(), id, true));
        var second = ready.startGame(guest.id(), new UUID(0, 2), firstSeatBlack);
        var newGame = second.game().orElseThrow();
        assertEquals(oldGame.whitePlayer(), newGame.blackPlayer());
        assertEquals(oldGame.blackPlayer(), newGame.whitePlayer());
        assertNotEquals(oldFinished.id(), newGame.id());
        assertNotSame(oldFinished.board(), newGame.board());
        assertEquals(0, newGame.board().moveCount());
        assertEquals(1, oldFinished.board().moveCount());
        assertEquals(GameStatus.FINISHED, oldFinished.status());
        assertTrue(second.readyPlayers().isEmpty());
        assertSame(second, second.join(host));
    }

    @Test
    void normalFiveInARowEndsRoomAndCanBeFollowedByRematch() {
        var room = readyRoom().startGame(host.id(), id, true);
        for (int col = 0; col < 4; col++) {
            room = room.play(host.id(), new BoardPosition(0, col))
                    .play(guest.id(), new BoardPosition(14, col * 2));
        }
        var ended = room.play(host.id(), new BoardPosition(0, 4));
        assertEquals(RoomPhase.FINISHED, ended.phase());
        assertEquals(host.id(), ended.game().orElseThrow().winner().orElseThrow());
        var next = ended.setReady(host.id(), true).setReady(guest.id(), true)
                .startGame(host.id(), new UUID(0, 2), true);
        assertEquals(guest.id(), next.game().orElseThrow().blackPlayer());
        assertEquals(0, next.game().orElseThrow().board().moveCount());
    }

    @Test
    void nullInputsDoNotMutateReadyState() {
        var ready = readyRoom();
        assertThrows(NullPointerException.class, () -> ready.setReady(null, true));
        assertThrows(NullPointerException.class, () -> ready.startGame(null, id, true));
        assertThrows(NullPointerException.class, () -> ready.startGame(host.id(), null, true));
        var active = ready.startGame(host.id(), id, true);
        assertThrows(NullPointerException.class, () -> active.play(host.id(), null));
        assertEquals(RoomPhase.READY, ready.phase());
        assertEquals(0, active.game().orElseThrow().board().moveCount());
    }

    private static void assertCode(RoomException.Code expected, Runnable operation) {
        assertEquals(expected, assertThrows(RoomException.class, operation::run).code());
    }
}
