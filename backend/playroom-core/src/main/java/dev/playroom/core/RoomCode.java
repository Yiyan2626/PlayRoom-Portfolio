package dev.playroom.core;

import java.util.Locale;
import java.util.Objects;

/** 六位 ASCII 字母或数字；去除首尾空白、统一大写，供目录按值查找同一房间。 */
public record RoomCode(String value) {
    public static final int LENGTH = 6;

    public RoomCode {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (!value.matches("[A-Za-z0-9]{6}")) {
            throw new IllegalArgumentException("Room code must contain six ASCII letters or digits");
        }
        value = value.toUpperCase(Locale.ROOT);
    }
}
