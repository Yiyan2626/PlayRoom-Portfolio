package dev.playroom.review;
import dev.playroom.core.*;
import java.util.*;
/** Java重算棋谱事实；模型只解释已确认事实。最多225步，没有写入或落子工具。 */
public final class RuleEvidence {
    public static final String VERSION="gomoku-freestyle-1";
    private RuleEvidence() { }
    public static List<Review.Evidence> verify(Review.Facts facts) {
        if(facts==null || facts.gameId()==null || facts.actorId()<=0
                || (facts.actorId()!=facts.blackId() && facts.actorId()!=facts.whiteId())
                || facts.moves().size()>225) throw new IllegalArgumentException("非法棋谱或参与者");
        GameState state=GameState.start(facts.gameId(),new PlayerId(facts.blackId()),new PlayerId(facts.whiteId()));
        var evidence=new ArrayList<Review.Evidence>();
        for(int i=0;i<facts.moves().size();i++) {
            var step=facts.moves().get(i);
            if(step.number()!=i+1 || step.version()!=i+1 || !state.nextColor().orElseThrow().name().equals(step.color()))
                throw new IllegalArgumentException("棋步编号、版本或颜色不一致");
            state=state.play(new PlayerId(step.playerId()),new BoardPosition(step.row(),step.col()));
            evidence.add(new Review.Evidence("M"+step.number(),"MOVE",step.number(),
                    "第"+step.number()+"手"+("BLACK".equals(step.color())?"黑棋":"白棋")+"落在第"+(step.row()+1)+"行第"+(step.col()+1)+"列",
                    List.of(new Review.Cell(step.row(),step.col()))));
        }
        if("RESIGNATION".equals(facts.reason())) {
            Long winner=facts.winnerId();
            if(winner==null || (winner!=facts.blackId() && winner!=facts.whiteId())) throw new IllegalArgumentException("认输缺少有效胜者");
            state=state.resign(new PlayerId(winner==facts.blackId()?facts.whiteId():facts.blackId()));
        }
        if(state.status()!=GameStatus.FINISHED || state.version()!=facts.version()
                || !state.endReason().orElseThrow().name().equals(facts.reason())
                || !Objects.equals(state.winner().map(PlayerId::value).orElse(null),facts.winnerId()))
            throw new IllegalArgumentException("终局与棋步不匹配");
        if("FIVE_IN_ROW".equals(facts.reason())) {
            var last=facts.moves().getLast();
            for(int[] dir:new int[][]{{0,1},{1,0},{1,1},{1,-1}}) {
                var line=new ArrayList<Review.Cell>();
                for(int offset=-14;offset<=14;offset++) {
                    int row=last.row()+offset*dir[0],col=last.col()+offset*dir[1];
                    if(row<0||row>14||col<0||col>14) {line.clear();continue;}
                    var color=state.board().at(new BoardPosition(row,col));
                    if(color.isPresent()&&color.get().name().equals(last.color())) {
                        line.add(new Review.Cell(row,col));
                        if(line.size()>=5&&line.contains(new Review.Cell(last.row(),last.col()))) break;
                    } else line.clear();
                }
                if(line.size()>=5&&line.contains(new Review.Cell(last.row(),last.col()))) {
                    evidence.add(new Review.Evidence("WIN","LINE",last.number(),"第"+last.number()+"手形成连续"+line.size()+"颗同色棋，按自由五子棋规则获胜",line));break;
                }
            }
            if(evidence.stream().noneMatch(e->e.id().equals("WIN"))) throw new IllegalArgumentException("缺少连五证据");
        }
        evidence.add(new Review.Evidence("END","RESULT",facts.moves().size(),switch(facts.reason()) {
            case "FIVE_IN_ROW"->"五子连线结束，胜方为"+(facts.winnerId()==facts.blackId()?"黑棋":"白棋");
            case "BOARD_FULL"->"225格已满且无人连五，结果为平局";
            case "RESIGNATION"->"一方认输，对方获胜；认输不增加棋子";
            default->throw new IllegalArgumentException("未知结束原因");
        },List.of()));
        return List.copyOf(evidence);
    }
    public static void checkQuestion(String question,String mode,String context) {
        if(question==null||question.isBlank()||question.length()>500
                ||question.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))
            throw new IllegalArgumentException("问题需为1至500个字符");
        if(mode==null||context==null||!List.of("demo","openai").contains(mode)||!List.of("direct","retrieval").contains(context))
            throw new IllegalArgumentException("未知复盘或上下文模式");
    }
    /** 引用和事实需吻合。自然语言语义仍需人工/评测核对，不把JSON合法冒充解释必然正确。 */
    public static void checkAnswer(Review.Facts facts,List<Review.Evidence> evidence,Review.Answer answer) {
        if(answer==null||!facts.gameId().equals(answer.gameId())||answer.version()!=facts.version()
                ||answer.text()==null||answer.text().isBlank()||answer.text().length()>4000
                ||answer.citations().isEmpty()||answer.citations().size()>12||!validEvidence(facts,evidence,answer.evidence())
                ||answer.citations().stream().anyMatch(id->answer.evidence().stream().noneMatch(e->e.id().equals(id))))
            throw new IllegalArgumentException("复盘返回的版本、内容或引用不一致");
    }
    private static boolean validEvidence(Review.Facts facts,List<Review.Evidence> base,List<Review.Evidence> actual) {
        // 输入base只能是基础棋谱事实；新增的棋局分析逐条由本服务重算，最多对应三次工具执行。
        var canonical=verify(facts);
        if(!base.equals(canonical)||actual==null||actual.size()<canonical.size()||actual.size()>canonical.size()+3
                ||!actual.subList(0,canonical.size()).equals(canonical))return false;
        var ids=new HashSet<String>();
        for(var item:actual)if(!ids.add(item.id()))return false;
        return actual.subList(canonical.size(),actual.size()).stream().allMatch(e->BoardAnalysis.authentic(facts,e));
    }

}
