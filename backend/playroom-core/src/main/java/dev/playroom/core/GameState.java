package dev.playroom.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 不可变单局状态：身份、终局、回合校验通过后，才计算并返回新的候选状态。 */
public final class GameState {
    private final UUID id;
    private final PlayerId blackPlayer;
    private final PlayerId whitePlayer;
    private final Board board;
    // 棋局每次成功操作递增；认输不落子，所以不能再用棋子数量代替版本。
    private final long version;
    private final StoneColor nextColor;
    private final GameEndReason endReason;
    private final PlayerId winner;

    // 私有构造器只接收本类创建的合法组合；终局时nextColor为空，平局时winner为空。
    private GameState(UUID id, PlayerId blackPlayer, PlayerId whitePlayer, Board board,
                      StoneColor nextColor, GameEndReason endReason, PlayerId winner, long version) {
        this.id = id;
        this.blackPlayer = blackPlayer;
        this.whitePlayer = whitePlayer;
        this.board = board;
        this.version = version;
        this.nextColor = nextColor;
        this.endReason = endReason;
        this.winner = winner;
    }

    public static GameState start(UUID id, PlayerId blackPlayer, PlayerId whitePlayer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(blackPlayer, "blackPlayer");
        Objects.requireNonNull(whitePlayer, "whitePlayer");
        if (blackPlayer.equals(whitePlayer)) {
            throw new IllegalArgumentException("A game requires two distinct players");
        }
        return new GameState(id, blackPlayer, whitePlayer, new Board(), StoneColor.BLACK, null, null, 0);
    }

    public UUID id() { return id; }
    public PlayerId blackPlayer() { return blackPlayer; }
    public PlayerId whitePlayer() { return whitePlayer; }
    public Board board() { return board; }
    public long version() { return version; }
    public GameStatus status() { return endReason == null ? GameStatus.ACTIVE : GameStatus.FINISHED; }
    public Optional<StoneColor> nextColor() { return Optional.ofNullable(nextColor); }
    public Optional<GameEndReason> endReason() { return Optional.ofNullable(endReason); }
    public Optional<PlayerId> winner() { return Optional.ofNullable(winner); }

    public Optional<StoneColor> colorOf(PlayerId player) {
        Objects.requireNonNull(player, "player");
        if (blackPlayer.equals(player)) return Optional.of(StoneColor.BLACK);
        if (whitePlayer.equals(player)) return Optional.of(StoneColor.WHITE);
        return Optional.empty();
    }

    public GameState play(PlayerId player, BoardPosition position) {
        Objects.requireNonNull(position, "position");
        StoneColor color = requireParticipant(player);
        requireActive();
        if (color != nextColor) {
            throw new RoomException(RoomException.Code.NOT_YOUR_TURN, "Not your turn");
        }
        if (board.at(position).isPresent()) {
            throw new RoomException(RoomException.Code.CELL_OCCUPIED, "Cell is occupied");
        }
        Board candidate = board.place(position, color);
        GameOutcome outcome = GomokuRules.evaluate(candidate, position);
        // 只在ONGOING时切换回合。胜利或平局没有下一位执棋者。
        return switch (outcome) {
            case ONGOING -> new GameState(id, blackPlayer, whitePlayer, candidate,
                    opposite(color), null, null, version + 1);
            case BLACK_WINS -> finished(candidate, GameEndReason.FIVE_IN_ROW, blackPlayer);
            case WHITE_WINS -> finished(candidate, GameEndReason.FIVE_IN_ROW, whitePlayer);
            case DRAW -> finished(candidate, GameEndReason.BOARD_FULL, null);
        };
    }

    /** 认输不要求轮到本人，但必须是本局参与者，且棋局还在进行中。 */
    public GameState resign(PlayerId player) {
        StoneColor color = requireParticipant(player);
        requireActive();
        return finished(board, GameEndReason.RESIGNATION,
                color == StoneColor.BLACK ? whitePlayer : blackPlayer);
    }

    private GameState finished(Board candidate, GameEndReason reason, PlayerId winningPlayer) {
        return new GameState(id, blackPlayer, whitePlayer, candidate, null, reason, winningPlayer, version + 1);
    }

    private StoneColor requireParticipant(PlayerId player) {
        return colorOf(player).orElseThrow(
                () -> new RoomException(RoomException.Code.NOT_MEMBER, "Player is not in this game"));
    }

    private void requireActive() {
        if (status() == GameStatus.FINISHED) {
            throw new RoomException(RoomException.Code.GAME_FINISHED, "Game has finished");
        }
    }

    private static StoneColor opposite(StoneColor color) {
        return color == StoneColor.BLACK ? StoneColor.WHITE : StoneColor.BLACK;
    }
}
