package dev.playroom.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoomTest {
    private final Player host = new Player(new PlayerId(7), "Chen");
    private final Player guest = new Player(new PlayerId(8), "Lin");
    private final RoomCode code = new RoomCode("ABC123");

    @Test
    void hostOccupiesFirstSeatAndUnknownPlayerHasNoSeat() {
        var room = Room.create(code, host);
        assertEquals(code, room.code());
        assertEquals(List.of(host), room.members());
        assertEquals(Seat.FIRST, room.seatOf(new PlayerId(7)).orElseThrow());
        assertTrue(room.seatOf(guest.id()).isEmpty());
    }

    @Test
    void secondPlayerGetsSecondSeatWithoutChangingOldSnapshot() {
        var before = Room.create(code, host);
        var membersBefore = before.members();
        var after = before.join(guest);
        assertNotSame(before, after);
        assertEquals(List.of(host), membersBefore);
        assertEquals(List.of(host), before.members());
        assertEquals(List.of(host, guest), after.members());
        assertEquals(Seat.FIRST, after.seatOf(host.id()).orElseThrow());
        assertEquals(Seat.SECOND, after.seatOf(guest.id()).orElseThrow());
    }

    @Test
    void sameNicknameDoesNotMergeDifferentIdentities() {
        var room = Room.create(code, host).join(new Player(guest.id(), host.nickname()));
        assertEquals(2, room.members().size());
        assertEquals(Seat.SECOND, room.seatOf(guest.id()).orElseThrow());
    }

    @Test
    void hostRetryDoesNotFillAnEmptySeatOrRenameTheProfile() {
        var room = Room.create(code, host);
        assertSame(room, room.join(new Player(new PlayerId(7), "阿陈")));
        assertEquals(List.of(host), room.members());
    }

    @Test
    void bothExistingPlayersCanRetryWhenFullEvenWithChangedNicknames() {
        var room = Room.create(code, host).join(guest);
        // 保留本人D04实操：同ID换昵称重试，仍是第一席，原资料也不被隐式更新。
        assertEquals(Seat.FIRST, room.join(new Player(new PlayerId(7), "新昵称"))
                .seatOf(new PlayerId(7)).orElseThrow());
        assertEquals("Chen", room.members().get(0).nickname());
        assertSame(room, room.join(new Player(new PlayerId(7), "阿陈")));
        assertSame(room, room.join(new Player(new PlayerId(8), "小林")));
        assertEquals(List.of(host, guest), room.members());
    }

    @Test
    void thirdIdentityIsRejectedAndFullSnapshotSurvives() {
        var room = Room.create(code, host).join(guest);
        var error = assertThrows(RoomException.class,
                () -> room.join(new Player(new PlayerId(9), "Chen")));
        assertEquals(RoomException.Code.ROOM_FULL, error.code());
        assertEquals(List.of(host, guest), room.members());
        assertSame(room, room.join(host));
    }

    @Test
    void callersCannotAppendReplaceOrClearMembership() {
        var room = Room.create(code, host);
        assertThrows(UnsupportedOperationException.class, () -> room.members().add(guest));
        assertThrows(UnsupportedOperationException.class, () -> room.members().set(0, guest));
        assertThrows(UnsupportedOperationException.class, () -> room.members().clear());
        assertEquals(List.of(host), room.members());
    }

    @Test
    void nullArgumentsAreRejectedWithoutChangingTheRoom() {
        var room = Room.create(code, host);
        assertThrows(NullPointerException.class, () -> Room.create(null, host));
        assertThrows(NullPointerException.class, () -> Room.create(code, null));
        assertThrows(NullPointerException.class, () -> room.join(null));
        assertThrows(NullPointerException.class, () -> room.seatOf(null));
        assertEquals(List.of(host), room.members());
    }
}
