package dev.playroom.review;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BoardAnalysisTest {
    static Review.Facts game(int[][] cells,boolean won) {
        var moves=new ArrayList<Review.Step>();
        for(int i=0;i<cells.length;i++)moves.add(new Review.Step(i+1,i%2==0?7:8,
                cells[i][0],cells[i][1],i%2==0?"BLACK":"WHITE",i+1));
        return new Review.Facts(UUID.randomUUID(),8,7,8,cells.length+(won?0:1),
                won?"FIVE_IN_ROW":"RESIGNATION",7L,moves);
    }
    static Review.Facts edgeWin() {
        return game(new int[][]{{7,0},{8,0},{7,1},{8,1},{7,2},{8,2},{7,3},{8,3},{7,4}},true);
    }
    @ParameterizedTest @CsvSource({"0,1,7,0,7,4","1,0,0,7,4,7","1,1,0,0,4,4","1,-1,0,14,4,10"})
    void findsTheOnlyBlockInAllFourDirections(int dr,int dc,int row,int col,int endRow,int endCol) {
        int[][] cells=new int[9][2];
        for(int i=0;i<9;i++)cells[i]=i%2==0?new int[]{row+i/2*dr,col+i/2*dc}:new int[]{14,i/2*2};
        var facts=game(cells,true);var p=BoardAnalysis.analyze(facts,7);
        assertEquals("WHITE",p.toMove());assertEquals(List.of(new Review.Cell(endRow,endCol)),p.opponentThreats());
        assertEquals(p.opponentThreats(),p.defenses());assertTrue(p.winningMoves().isEmpty());
    }
    @Test void openFourHasTwoThreatsAndNoSingleBlock() {
        var f=game(new int[][]{{7,3},{13,0},{7,4},{13,2},{7,5},{13,4},{7,6}},false);
        var p=BoardAnalysis.analyze(f,7);
        assertEquals(List.of(new Review.Cell(7,2),new Review.Cell(7,7)),p.opponentThreats());assertTrue(p.defenses().isEmpty());
        assertEquals(List.of(new Review.Cell(7,7)),BoardAnalysis.simulate(f,7,7,2).remainingThreats());
    }
    @Test void aWinningReplyEndsBeforeOpponentCanUseTheirThreat() {
        var p=BoardAnalysis.analyze(edgeWin(),8);
        assertEquals("BLACK",p.toMove());assertEquals(List.of(new Review.Cell(7,4)),p.winningMoves());
        assertEquals(List.of(new Review.Cell(8,4)),p.opponentThreats());
        var t=BoardAnalysis.simulate(edgeWin(),8,7,4);assertTrue(t.winsNow());assertTrue(t.remainingThreats().isEmpty());
    }
    @Test void restoringEarlierStepExcludesFutureStonesAndNeverChangesOriginal() {
        var f=edgeWin();var copy=List.copyOf(f.moves());
        assertTrue(BoardAnalysis.analyze(f,5).opponentThreats().isEmpty());
        var before=BoardAnalysis.evidence(f,7);var trial=BoardAnalysis.evidence(f,7,7,4);
        assertTrue(trial.description().contains("直接获胜点=无"));assertEquals(copy,f.moves());
        assertEquals(before,BoardAnalysis.evidence(f,7));assertTrue(BoardAnalysis.analyze(f,9).terminal());
        assertEquals("BLACK",BoardAnalysis.analyze(f,0).toMove());
    }
    @Test void brokenFourAndLongLineFollowFreestyleRules() {
        var broken=game(new int[][]{{7,0},{14,0},{7,1},{14,2},{7,3},{14,4},{7,4}},false);
        assertEquals(List.of(new Review.Cell(7,2)),BoardAnalysis.analyze(broken,7).opponentThreats());
        var longLine=game(new int[][]{{7,0},{14,0},{7,1},{14,2},{7,2},{14,4},{7,4},{14,6},{7,5}},false);
        assertEquals(List.of(new Review.Cell(7,3)),BoardAnalysis.analyze(longLine,9).opponentThreats());
    }
    @Test void rejectsOccupiedOutsideFutureTerminalAndUnauthorizedRequests() {
        var f=edgeWin();
        for(int step:new int[]{-1,10,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->BoardAnalysis.analyze(f,step));
        assertThrows(IllegalArgumentException.class,()->BoardAnalysis.simulate(f,7,7,0));
        assertThrows(IllegalArgumentException.class,()->BoardAnalysis.simulate(f,7,15,0));
        assertThrows(IllegalArgumentException.class,()->BoardAnalysis.simulate(f,9,0,0));
        var outsider=new Review.Facts(f.gameId(),99,7,8,9,f.reason(),7L,f.moves());
        assertThrows(IllegalArgumentException.class,()->BoardAnalysis.analyze(outsider,7));
    }
    @Test void gatewayCanRecomputeEvidenceAndRejectTampering() {
        var f=edgeWin();var base=RuleEvidence.verify(f);var board=BoardAnalysis.evidence(f,7);
        var all=new ArrayList<>(base);all.add(board);
        var answer=new Review.Answer(f.gameId(),9,"openai","fixture","见证据",List.of(board.id()),all,Review.Usage.zero(),0,2,false,"test");
        assertDoesNotThrow(()->RuleEvidence.checkAnswer(f,base,answer));
        assertFalse(BoardAnalysis.authentic(f,new Review.Evidence(board.id(),board.kind(),7,"伪造防守点",board.cells())));
        all.add(board);
        var duplicate=new Review.Answer(f.gameId(),9,"openai","fixture","见证据",List.of(board.id()),all,Review.Usage.zero(),0,2,false,"test");
        assertThrows(IllegalArgumentException.class,()->RuleEvidence.checkAnswer(f,base,duplicate));
    }
}
