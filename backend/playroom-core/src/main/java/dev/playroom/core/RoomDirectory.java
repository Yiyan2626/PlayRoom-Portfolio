package dev.playroom.core;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 内存房间目录，只支持顺序调用，不是线程安全的。
 * 后续服务必须保护整个读状态、检查、生成候选和发布流程；本类没有账号、HTTP或数据库。
 */
public final class RoomDirectory {
    public static final int MAX_CODE_ATTEMPTS = 10;
    public static final int MAX_GAME_ID_ATTEMPTS = 10;
    // 键是规范化房间码，值是该房间最新的不可变快照；size 表示房间数，不是玩家数。
    private final Map<RoomCode, Room> rooms = new HashMap<>();
    private final Supplier<RoomCode> codeGenerator;
    private final Supplier<UUID> gameIds;
    private final BooleanSupplier firstSeatBlack;
    private final Set<UUID> usedGameIds = new HashSet<>();

    public RoomDirectory() {
        this(randomCodes());
    }

    /** 兼容D04入口；棋局ID和首局黑白默认随机，测试可用三参数构造器控制。 */
    public RoomDirectory(Supplier<RoomCode> codeGenerator) {
        this(codeGenerator, UUID::randomUUID, new SecureRandom()::nextBoolean);
    }

    public RoomDirectory(Supplier<RoomCode> codeGenerator, Supplier<UUID> gameIds,
                         BooleanSupplier firstSeatBlack) {
        this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator");
        this.gameIds = Objects.requireNonNull(gameIds, "gameIds");
        this.firstSeatBlack = Objects.requireNonNull(firstSeatBlack, "firstSeatBlack");
    }

    public Room create(Player host) {
        Objects.requireNonNull(host, "host");
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            RoomCode code = Objects.requireNonNull(codeGenerator.get(), "generated code");
            if (!rooms.containsKey(code)) {
                Room room = Room.create(code, host);
                rooms.put(code, room);
                return room;
            }
        }
        throw new RoomException(RoomException.Code.ROOM_CODE_EXHAUSTED,
                "Could not allocate an unused room code");
    }

    public Optional<Room> find(RoomCode code) {
        Objects.requireNonNull(code, "code");
        return Optional.ofNullable(rooms.get(code));
    }

    public Room join(RoomCode code, Player player) {
        Objects.requireNonNull(player, "player");
        return update(code, current -> current.join(player));
    }

    public Room setReady(RoomCode code, PlayerId player, boolean ready) {
        return update(code, current -> current.setReady(player, ready));
    }

    public Room startGame(RoomCode code, PlayerId actor) {
        return startGame(code, actor, candidate -> { });
    }

    /** 候选交给应用层提交；回调正常返回才发布，不让核心依赖JDBC或Spring。 */
    public Room startGame(RoomCode code, PlayerId actor, Consumer<Room> beforePublish) {
        Objects.requireNonNull(beforePublish, "beforePublish");
        Room current = requireRoom(code);
        current.ensureCanStart(actor);
        for (int attempt = 0; attempt < MAX_GAME_ID_ATTEMPTS; attempt++) {
            UUID id = Objects.requireNonNull(gameIds.get(), "generated game ID");
            if (!usedGameIds.contains(id)) {
                // 只在首局取随机值。再次对战交换上局黑白，避免重新随机后颜色没交换。
                boolean black = current.game().isEmpty() && firstSeatBlack.getAsBoolean();
                Room candidate = current.startGame(actor, id, black);
                beforePublish.accept(candidate);
                usedGameIds.add(id);
                rooms.put(code, candidate);
                return candidate;
            }
        }
        throw new RoomException(RoomException.Code.GAME_ID_EXHAUSTED, "Could not allocate an unused game ID");
    }

    public Room play(RoomCode code, PlayerId player, BoardPosition position) {
        return update(code, current -> current.play(player, position));
    }

    /** 应用持有同一目录锁；回调抛异常时不执行put，旧Room及棋盘保持不变。 */
    public Room play(RoomCode code, PlayerId player, BoardPosition position, Consumer<Room> beforePublish) {
        Objects.requireNonNull(beforePublish, "beforePublish");
        Room candidate = requireRoom(code).play(player, position);
        beforePublish.accept(candidate);
        rooms.put(code, candidate);
        return candidate;
    }

    public Room resign(RoomCode code, PlayerId player) {
        return update(code, current -> current.resign(player));
    }

    /** 认输也先提交候选；失败时保留旧房间、旧棋局与准备状态。 */
    public Room resign(RoomCode code, PlayerId player, Consumer<Room> beforePublish) {
        Objects.requireNonNull(beforePublish, "beforePublish");
        Room candidate = requireRoom(code).resign(player);
        beforePublish.accept(candidate);
        rooms.put(code, candidate);
        return candidate;
    }

    private Room requireRoom(RoomCode code) {
        return find(code).orElseThrow(
                () -> new RoomException(RoomException.Code.ROOM_NOT_FOUND, "Room not found"));
    }

    private Room update(RoomCode code, UnaryOperator<Room> operation) {
        // operation 是调用方传入的“旧 Room -> 候选 Room”操作；不是网络调用。
        Room candidate = operation.apply(requireRoom(code));
        // 上一行抛错时不会执行put，所以失败不改变目录里的已发布状态。
        rooms.put(code, candidate);
        return candidate;
    }

    public int roomCount() {
        return rooms.size();
    }

    private static Supplier<RoomCode> randomCodes() {
        var random = new SecureRandom();
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        return () -> {
            var value = new StringBuilder(RoomCode.LENGTH);
            for (int index = 0; index < RoomCode.LENGTH; index++) {
                value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            return new RoomCode(value.toString());
        };
    }
}
