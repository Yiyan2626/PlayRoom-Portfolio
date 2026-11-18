package dev.playroom.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class RoomCodeTest {
    @ParameterizedTest
    @ValueSource(strings = {"abc123", "ABC123", " \tabC123\n"})
    void normalizesEquivalentInvitationCodes(String value) {
        var code = new RoomCode(value);
        assertEquals("ABC123", code.value());
        var rooms = new HashMap<RoomCode, String>();
        rooms.put(new RoomCode("ABC123"), "room");
        assertEquals("room", rooms.get(code));
        assertEquals(new RoomCode("ABC123").hashCode(), code.hashCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABC12", "ABC1234", "AB C12", "ABC12!", "ＡBC123", "ßabc12", "阿BC123"})
    void rejectsMalformedOrNonAsciiCodes(String value) {
        assertThrows(IllegalArgumentException.class, () -> new RoomCode(value));
    }

    @Test
    void acceptsAllAsciiLettersAndDigitsIncludingManualZeroAndOne() {
        assertEquals("IO01AZ", new RoomCode("io01az").value());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> new RoomCode(null));
    }
}
