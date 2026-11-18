package dev.playroom.review;

import dev.playroom.core.*;
import java.util.*;

/** 只在已核对的终局棋谱副本上恢复局面；枚举一步成五，不搜索多步最优策略。 */
public final class BoardAnalysis {
    public static final String VERSION="board-one-ply-1";
    private BoardAnalysis() { }
    public record Position(int step, String toMove, boolean terminal, List<Review.Cell> winningMoves,
                           List<Review.Cell> opponentThreats, List<Review.Cell> defenses) { }
    public record Trial(int step, int row, int col, String color, boolean winsNow,
                        List<Review.Cell> remainingThreats) { }

    /** step=7表示只重放前7颗棋；后面的棋和认输动作不会进入这个历史局面。 */
    private static GameState restore(Review.Facts facts,int step) {
        RuleEvidence.verify(facts);
        if(step<0||step>facts.moves().size())throw new IllegalArgumentException("步号超出本局棋谱");
        var state=GameState.start(facts.gameId(),new PlayerId(facts.blackId()),new PlayerId(facts.whiteId()));
        for(var move:facts.moves().subList(0,step))
            state=state.play(new PlayerId(move.playerId()),new BoardPosition(move.row(),move.col()));
        return state;
    }
    private static StoneColor other(StoneColor color) {
        return color==StoneColor.BLACK?StoneColor.WHITE:StoneColor.BLACK;
    }
    private static boolean wins(Board board,Review.Cell cell,StoneColor color) {
        var position=new BoardPosition(cell.row(),cell.col());
        var outcome=GomokuRules.evaluate(board.place(position,color),position);
        return outcome==GameOutcome.BLACK_WINS||outcome==GameOutcome.WHITE_WINS;
    }
    private static List<Review.Cell> empty(Board board) {
        var cells=new ArrayList<Review.Cell>();
        for(int row=0;row<15;row++)for(int col=0;col<15;col++)
            if(board.at(new BoardPosition(row,col)).isEmpty())cells.add(new Review.Cell(row,col));
        return cells;
    }
    private static List<Review.Cell> winning(Board board,StoneColor color) {
        return empty(board).stream().filter(cell->wins(board,cell,color)).toList();
    }
    public static Position analyze(Review.Facts facts,int step) {
        var state=restore(facts,step);
        if(state.status()==GameStatus.FINISHED)return new Position(step,"NONE",true,List.of(),List.of(),List.of());
        var color=state.nextColor().orElseThrow();var board=state.board();
        var own=winning(board,color);var threats=winning(board,other(color));
        var defenses=new ArrayList<Review.Cell>();
        // 对手目前没有一步获胜点时，不把所有空位都称为“防守点”。自己立即取胜单列。
        // 对手可在x一手成五，其连线除x外均已有对手棋；落在别处不会破坏它。
        // 两个不同成五空点无法用一颗非获胜棋同时占住，因此只需试唯一威胁点。
        if(threats.size()==1)for(var cell:threats) {
            if(own.contains(cell))continue;
            var candidate=board.place(new BoardPosition(cell.row(),cell.col()),color);
            if(winning(candidate,other(color)).isEmpty())defenses.add(cell);
        }
        return new Position(step,color.name(),false,own,threats,List.copyOf(defenses));
    }
    public static Trial simulate(Review.Facts facts,int step,int row,int col) {
        var state=restore(facts,step);
        if(state.status()==GameStatus.FINISHED)throw new IllegalArgumentException("连五或满盘后不能再试落子");
        var position=new BoardPosition(row,col);var color=state.nextColor().orElseThrow();
        if(state.board().at(position).isPresent())throw new IllegalArgumentException("试落点已占用");
        var cell=new Review.Cell(row,col);boolean won=wins(state.board(),cell,color);
        var candidate=state.board().place(position,color);
        return new Trial(step,row,col,color.name(),won,won?List.of():winning(candidate,other(color)));
    }
    public static Review.Evidence evidence(Review.Facts facts,int step) {
        var p=analyze(facts,step);
        String text="第"+step+"手后（只重放棋子，未执行其后的认输），";
        text+=p.terminal()?"棋子局面已终结，无下一手":
                "轮到"+colorName(p.toMove())+"；本方一步获胜点="+coordinates(p.winningMoves())
                +"；对手下一步直接获胜点="+coordinates(p.opponentThreats())
                +"；本方非获胜落子中可消除全部上述威胁的防守点="+coordinates(p.defenses());
        text+="。仅核对一步成五，不代表多步安全或最优棋力；界面行列从1开始";
        var cells=new ArrayList<Review.Cell>(p.opponentThreats());cells.addAll(p.defenses());cells.addAll(p.winningMoves());
        return new Review.Evidence("BOARD-"+step,"BOARD_ANALYSIS",step,text,cells.stream().distinct().toList());
    }
    public static Review.Evidence evidence(Review.Facts facts,int step,int row,int col) {
        var t=simulate(facts,step,row,col);
        return new Review.Evidence("TRY-"+step+"-"+row+"-"+col,"BOARD_TRIAL",step,
                "第"+step+"手后假设"+colorName(t.color())+"在第"+(row+1)+"行第"+(col+1)+"列落子；本手成五="+(t.winsNow()?"是":"否")
                +"；随后对手直接获胜点="+coordinates(t.remainingThreats())+"。只改副本，实际棋谱不变；仅一步核对",
                List.of(new Review.Cell(row,col)));
    }
    private static String colorName(String color) {return color.equals("BLACK")?"黑棋":"白棋";}
    private static String coordinates(List<Review.Cell> cells) {
        return cells.isEmpty()?"无":String.join("、",cells.stream().map(c->"("+(c.row()+1)+","+(c.col()+1)+")").toList());
    }
    /** 跨服务不能直接相信新增证据；按ID重算完整内容，拒绝伪造坐标、步号与描述。 */
    public static boolean authentic(Review.Facts facts,Review.Evidence value) {
        try {
            String id=value.id();Review.Evidence expected;
            if(id.matches("BOARD-(0|[1-9][0-9]{0,2})"))expected=evidence(facts,Integer.parseInt(id.substring(6)));
            else if(id.matches("TRY-(0|[1-9][0-9]{0,2})-(0|[1-9][0-9]?)-(0|[1-9][0-9]?)")) {
                var parts=id.split("-");expected=evidence(facts,Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));
            } else return false;
            return expected.equals(value);
        } catch(RuntimeException invalid){return false;}
    }
}
