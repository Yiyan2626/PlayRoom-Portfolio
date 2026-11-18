package dev.playroom.core;

/** 房间跨多局存在；阶段由准备集合和当前棋局推导，避免维护互相矛盾的标志。 */
public enum RoomPhase {
    WAITING,
    READY,
    PLAYING,
    FINISHED
}
