package dev.playroom.core;

import java.util.Objects;

/** 房间与对局的稳定业务错误码；未来由接口层映射HTTP响应，核心不依赖网络框架。 */
public final class RoomException extends RuntimeException {
    public enum Code {
        ROOM_FULL,
        ROOM_NOT_FOUND,
        ROOM_CODE_EXHAUSTED,
        NOT_MEMBER,
        ROOM_NOT_READY,
        GAME_NOT_STARTED,
        GAME_IN_PROGRESS,
        GAME_FINISHED,
        NOT_YOUR_TURN,
        CELL_OCCUPIED,
        GAME_ID_REUSED,
        GAME_ID_EXHAUSTED
    }

    private final Code code;

    public RoomException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
