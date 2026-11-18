package dev.playroom.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoomDirectoryTest {
    private final Player host = new Player(new PlayerId(7), "Chen");
    private final Player guest = new Player(new PlayerId(8), "Lin");
    private final RoomCode code = new RoomCode("ABC123");

    @Test
    void creationAndLookupUseCanonicalCodeValues() {
        var directory = new RoomDirectory(() -> code);
        assertEquals(0, directory.roomCount());
        assertTrue(directory.find(code).isEmpty());
        var room = directory.create(host);
        assertEquals(1, directory.roomCount());
        assertSame(room, directory.find(new RoomCode(" abc123 ")).orElseThrow());
    }

    @Test
    void successfulJoinPublishesNewSnapshotAndRetryIsANoOp() {
        var directory = new RoomDirectory(() -> code);
        var before = directory.create(host);
        var after = directory.join(new RoomCode("abc123"), guest);
        assertSame(after, directory.find(code).orElseThrow());
        assertEquals(List.of(host), before.members());
        assertEquals(List.of(host, guest), after.members());
        assertSame(after, directory.join(code, new Player(new PlayerId(8), "小林")));
        assertEquals(1, directory.roomCount());
    }

    @Test
    void rejectedJoinDoesNotCorruptThisOrAnotherRoom() {
        var codes = new ArrayDeque<>(List.of(code, new RoomCode("DEF456")));
        var directory = new RoomDirectory(codes::remove);
        directory.create(host);
        var other = directory.create(guest);
        var full = directory.join(code, guest);
        var error = assertThrows(RoomException.class,
                () -> directory.join(code, new Player(new PlayerId(9), "Third")));
        assertEquals(RoomException.Code.ROOM_FULL, error.code());
        assertSame(full, directory.find(code).orElseThrow());
        assertSame(other, directory.find(other.code()).orElseThrow());
        assertSame(full, directory.join(code, host));
        assertEquals(2, directory.roomCount());
    }

    @Test
    void missingRoomHasClearErrorAndNoPartialCreation() {
        var directory = new RoomDirectory(() -> code);
        var error = assertThrows(RoomException.class, () -> directory.join(code, host));
        assertEquals(RoomException.Code.ROOM_NOT_FOUND, error.code());
        assertTrue(directory.find(code).isEmpty());
        assertEquals(0, directory.roomCount());
    }

    @Test
    void collisionRetriesWithoutOverwritingTheFirstRoom() {
        var codes = new ArrayDeque<>(List.of(code, new RoomCode("abc123"), new RoomCode("DEF456")));
        var directory = new RoomDirectory(codes::remove);
        var first = directory.create(host);
        var second = directory.create(guest);
        assertEquals(new RoomCode("DEF456"), second.code());
        assertSame(first, directory.find(code).orElseThrow());
        assertEquals(List.of(host), first.members());
        assertEquals(2, directory.roomCount());
        assertTrue(codes.isEmpty());
    }

    @Test
    void tenCollisionsStopAndPreservePublishedRooms() {
        var calls = new AtomicInteger();
        var directory = new RoomDirectory(() -> {
            calls.incrementAndGet();
            return code;
        });
        var first = directory.create(host);
        var error = assertThrows(RoomException.class, () -> directory.create(guest));
        assertEquals(RoomException.Code.ROOM_CODE_EXHAUSTED, error.code());
        assertEquals(1 + RoomDirectory.MAX_CODE_ATTEMPTS, calls.get());
        assertSame(first, directory.find(code).orElseThrow());
        assertEquals(1, directory.roomCount());
    }

    @Test
    void tenthAttemptCanStillSucceed() {
        var calls = new AtomicInteger();
        var directory = new RoomDirectory(() -> calls.incrementAndGet() <= 10 ? code : new RoomCode("DEF456"));
        var first = directory.create(host);
        var second = directory.create(guest);
        assertEquals(11, calls.get());
        assertSame(first, directory.find(code).orElseThrow());
        assertEquals(new RoomCode("DEF456"), second.code());
    }

    @Test
    void failedGeneratorDoesNotPublishPartialRoom() {
        var nullGenerator = new RoomDirectory(() -> null);
        assertThrows(NullPointerException.class, () -> nullGenerator.create(host));
        assertEquals(0, nullGenerator.roomCount());
        var throwingGenerator = new RoomDirectory(() -> {
            throw new IllegalStateException("generator failed");
        });
        assertThrows(IllegalStateException.class, () -> throwingGenerator.create(host));
        assertEquals(0, throwingGenerator.roomCount());
    }

    @Test
    void defaultGeneratorProducesUsableCodeAndIndependentDirectoriesDoNotShareState() {
        var directory = new RoomDirectory();
        var room = directory.create(host);
        assertTrue(room.code().value().matches("[A-HJ-NP-Z2-9]{6}"));
        assertSame(room, directory.find(room.code()).orElseThrow());
        assertTrue(new RoomDirectory().find(room.code()).isEmpty());
    }

    @Test
    void rejectsNullArgumentsWithoutAllocatingCodes() {
        var calls = new AtomicInteger();
        var directory = new RoomDirectory(() -> {
            calls.incrementAndGet();
            return code;
        });
        assertThrows(NullPointerException.class, () -> new RoomDirectory(null));
        assertThrows(NullPointerException.class, () -> directory.create(null));
        assertThrows(NullPointerException.class, () -> directory.find(null));
        assertThrows(NullPointerException.class, () -> directory.join(null, host));
        assertThrows(NullPointerException.class, () -> directory.join(code, null));
        assertEquals(0, calls.get());
        assertEquals(0, directory.roomCount());
    }
}
