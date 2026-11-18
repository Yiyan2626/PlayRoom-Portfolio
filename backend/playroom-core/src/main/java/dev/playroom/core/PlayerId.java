package dev.playroom.core;

/** 稳定的玩家身份；record 按 value 生成 equals/hashCode，昵称不作为身份键。 */
public record PlayerId(long value) {
    public PlayerId {
        if (value <= 0) {
            throw new IllegalArgumentException("Player ID must be positive");
        }
    }
}
