package dev.playroom.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    @ParameterizedTest
    @ValueSource(longs = {1, 7, Long.MAX_VALUE})
    void acceptsPositiveIds(long value) {
        assertEquals(value, new PlayerId(value).value());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveIds(long value) {
        assertThrows(IllegalArgumentException.class, () -> new PlayerId(value));
    }

    @Test
    void identityIsAValueKeyRatherThanAnObjectReference() {
        var first = new PlayerId(7);
        var equal = new PlayerId(7);
        assertNotSame(first, equal);
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        var names = new HashMap<PlayerId, String>();
        names.put(first, "Chen");
        names.put(equal, "阿陈");
        assertEquals(1, names.size());
        assertEquals("阿陈", names.get(new PlayerId(7)));
        assertNull(names.get(new PlayerId(8)));
    }

    @Test
    void stripsSurroundingWhitespaceAndKeepsInteriorSpaces() {
        assertEquals("阿 陈", new Player(new PlayerId(7), " \t阿 陈\u2003").nickname());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void rejectsBlankNicknames(String nickname) {
        assertThrows(IllegalArgumentException.class, () -> new Player(new PlayerId(7), nickname));
    }

    @Test
    void measuresNicknameLimitByCodePointsNotUtf16Units() {
        String emoji = "\uD83D\uDE00";
        assertEquals(emoji.repeat(32), new Player(new PlayerId(7), emoji.repeat(32)).nickname());
        assertThrows(IllegalArgumentException.class, () -> new Player(new PlayerId(7), emoji.repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> new Player(new PlayerId(7), "a".repeat(33)));
    }

    @Test
    void profileEqualityIncludesNicknameButIdentityEqualityDoesNot() {
        var oldProfile = new Player(new PlayerId(7), "Chen");
        var renamed = new Player(new PlayerId(7), "阿陈");
        assertNotEquals(oldProfile, renamed);
        assertEquals(oldProfile.id(), renamed.id());
    }

    @Test
    void rejectsMissingProfileFields() {
        assertThrows(NullPointerException.class, () -> new Player(null, "Chen"));
        assertThrows(NullPointerException.class, () -> new Player(new PlayerId(7), null));
    }
}
