package dev.playroom.core;

import java.util.Objects;

/** 不可变的房间展示资料；核心不负责登录，HTTP 层从已认证账号构造此对象。 */
public record Player(PlayerId id, String nickname) {
    public Player {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nickname, "nickname");
        nickname = nickname.strip();
        if (nickname.isEmpty() || nickname.codePointCount(0, nickname.length()) > 32) {
            throw new IllegalArgumentException("Nickname must contain 1 to 32 Unicode code points");
        }
    }
}
