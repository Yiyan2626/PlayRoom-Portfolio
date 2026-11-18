package dev.playroom.review;
import java.util.List;
import java.util.UUID;
/** 服务之间的数据格式，没有密码、Cookie、SQL或修改棋盘的动作。 */
public final class Review {
    private Review() { }
    /** 一条实际落子：例如第1手、玩家7、第7行第0列、黑棋、版本1；行列内部从0开始。 */
    public record Step(int number, long playerId, int row, int col, String color, long version) { }
    public record Cell(int row, int col) { }
    /** 一条已核对的事实：如WIN引用第9手及组成连五的坐标，description装可读解释。 */
    public record Evidence(String id, String kind, int step, String description, List<Cell> cells) {
        public Evidence { cells = List.copyOf(cells); }
    }
    /** actorId只由对战服务从会话取得，用于参与权校验，不发送给模型。 */
    public record Facts(UUID gameId, long actorId, long blackId, long whiteId, long version,
                        String reason, Long winnerId, List<Step> moves) {
        public Facts { moves = List.copyOf(moves); }
    }
    /** facts装整局事实，question装用户问题，mode区分规则演示/真实模型，contextMode选择规则资料。 */
    public record Request(Facts facts, String question, String mode, String contextMode) { }
    public record Usage(long inputTokens, long outputTokens, long cachedInputTokens, Double estimatedUsd) {
        public static Usage zero() { return new Usage(0,0,0,null); }
    }
    public record Answer(UUID gameId, long version, String mode, String model, String text,
                         List<String> citations, List<Evidence> evidence, Usage usage,
                         long durationMs, int toolSteps, boolean cacheHit, String instance) {
        public Answer { citations=List.copyOf(citations); evidence=List.copyOf(evidence); }
        public Answer cached(String owner) {
            // 解释来自旧生成结果，但本次没有调用模型；不能再次累加旧结果的token和耗时。
            return new Answer(gameId,version,mode,model,text,citations,evidence,Usage.zero(),0,0,true,owner);
        }
    }
}
