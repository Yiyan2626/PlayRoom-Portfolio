package dev.playroom.core;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 不可变双座位房间：房间保存成员、准备集合及当前一局，多局可以复用同一个房间。 */
public final class Room {
    public static final int CAPACITY = 2;
    private final RoomCode code;
    // List 保留两个座位的顺序；Set 只记录谁已准备，同一玩家不会被重复计数。
    private final List<Player> members;
    private final Set<PlayerId> readyPlayers;
    private final GameState game;

    private Room(RoomCode code, List<Player> members, Set<PlayerId> readyPlayers, GameState game) {
        this.code = code;
        // 固定集合内容，避免外部增删；元素本身也不可变，不必再逐个复制 Player。
        // copyOf 不承诺返回 ArrayList/HashSet，调用方只能依赖 List/Set 的接口契约。
        this.members = List.copyOf(members);
        this.readyPlayers = Set.copyOf(readyPlayers);
        this.game = game;
    }

    public static Room create(RoomCode code, Player host) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(host, "host");
        return new Room(code, List.of(host), Set.of(), null);
    }

    public RoomCode code() {
        return code;
    }

    /** 列表不可修改，且Player及其字段不可变，所以共享元素引用不会破坏旧快照。 */
    public List<Player> members() {
        return members;
    }

    public Optional<Seat> seatOf(PlayerId id) {
        Objects.requireNonNull(id, "id");
        for (int index = 0; index < members.size(); index++) {
            if (members.get(index).id().equals(id)) {
                return Optional.of(index == 0 ? Seat.FIRST : Seat.SECOND);
            }
        }
        return Optional.empty();
    }

    /** 已有成员重试直接返回原对象，不修改昵称、准备状态或当前局。 */
    public Room join(Player player) {
        Objects.requireNonNull(player, "player");
        // 先识别已有ID，再查满员；否则满员后的正常重入会被错误拒绝。
        if (seatOf(player.id()).isPresent()) {
            return this;
        }
        if (members.size() >= CAPACITY) {
            throw new RoomException(RoomException.Code.ROOM_FULL, "Room is full");
        }
        // 候选列表可追加，旧列表不动；只有返回的新 Room 才包含第二名成员。
        var candidateMembers = new ArrayList<>(members);
        candidateMembers.add(player);
        return new Room(code, candidateMembers, readyPlayers, game);
    }

    public Set<PlayerId> readyPlayers() { return readyPlayers; }
    public Optional<GameState> game() { return Optional.ofNullable(game); }

    /** 阶段按真实数据推导；结束后的双方重新准备好时，显示READY供下一局启动。 */
    public RoomPhase phase() {
        if (game != null && game.status() == GameStatus.ACTIVE) return RoomPhase.PLAYING;
        if (members.size() == CAPACITY && readyPlayers.size() == CAPACITY) return RoomPhase.READY;
        return game == null ? RoomPhase.WAITING : RoomPhase.FINISHED;
    }

    /** 显式设置true/false，而非toggle：同一次准备操作重试不会意外取消准备。 */
    public Room setReady(PlayerId player, boolean ready) {
        requireMember(player);
        if (phase() == RoomPhase.PLAYING) {
            throw new RoomException(RoomException.Code.GAME_IN_PROGRESS, "Cannot change readiness during a game");
        }
        var candidate = new HashSet<>(readyPlayers);
        boolean changed = ready ? candidate.add(player) : candidate.remove(player);
        return changed ? new Room(code, members, candidate, game) : this;
    }

    /** 首局颜色由可控随机结果决定；重开时按上局交换，座位顺序不变。 */
    public Room startGame(PlayerId actor, UUID gameId, boolean firstSeatBlack) {
        ensureCanStart(actor);
        Objects.requireNonNull(gameId, "gameId");
        if (game != null && game.id().equals(gameId)) {
            throw new RoomException(RoomException.Code.GAME_ID_REUSED, "New game needs a new ID");
        }
        PlayerId black = game == null
                ? members.get(firstSeatBlack ? 0 : 1).id() : game.whitePlayer();
        PlayerId white = game == null
                ? members.get(firstSeatBlack ? 1 : 0).id() : game.blackPlayer();
        return new Room(code, members, Set.of(), GameState.start(gameId, black, white));
    }

    // 包内目录在分配随机资源前复用校验，失败请求不会消耗棋局ID或颜色选择。
    void ensureCanStart(PlayerId actor) {
        requireMember(actor);
        if (phase() == RoomPhase.PLAYING) {
            throw new RoomException(RoomException.Code.GAME_IN_PROGRESS, "Game already in progress");
        }
        if (phase() != RoomPhase.READY) {
            throw new RoomException(RoomException.Code.ROOM_NOT_READY, "Both players must be ready");
        }
    }

    public Room play(PlayerId player, BoardPosition position) {
        requireMember(player);
        GameState candidate = requireGame().play(player, position);
        return new Room(code, members, readyPlayers, candidate);
    }

    public Room resign(PlayerId player) {
        requireMember(player);
        GameState candidate = requireGame().resign(player);
        return new Room(code, members, readyPlayers, candidate);
    }

    private GameState requireGame() {
        if (game == null) {
            throw new RoomException(RoomException.Code.GAME_NOT_STARTED, "Game has not started");
        }
        return game;
    }

    private void requireMember(PlayerId player) {
        if (seatOf(player).isEmpty()) {
            throw new RoomException(RoomException.Code.NOT_MEMBER, "Player is not in this room");
        }
    }
}
