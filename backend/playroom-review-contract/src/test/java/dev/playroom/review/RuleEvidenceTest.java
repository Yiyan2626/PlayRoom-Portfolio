package dev.playroom.review;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class RuleEvidenceTest {
    static Review.Facts win() {
        var moves=new ArrayList<Review.Step>();
        for(int i=0;i<9;i++) moves.add(new Review.Step(i+1,i%2==0?7:8,i%2==0?7:8,i/2,i%2==0?"BLACK":"WHITE",i+1));
        return new Review.Facts(UUID.randomUUID(),7,7,8,9,"FIVE_IN_ROW",7L,moves);
    }
    @Test void lineHasActualCoordinatesAndCannotBeChanged() {
        var e=RuleEvidence.verify(win());var line=e.stream().filter(x->x.id().equals("WIN")).findFirst().orElseThrow();
        assertEquals(5,line.cells().size());assertEquals(new Review.Cell(7,4),line.cells().getLast());
        assertThrows(UnsupportedOperationException.class,()->line.cells().clear());
    }
    @Test void rejectsOutsiderAndForgedColor() {
        var f=win();assertThrows(IllegalArgumentException.class,()->RuleEvidence.verify(new Review.Facts(f.gameId(),9,7,8,9,f.reason(),7L,f.moves())));
        var m=new ArrayList<>(f.moves());m.set(1,new Review.Step(2,8,8,0,"BLACK",2));
        assertThrows(RuntimeException.class,()->RuleEvidence.verify(new Review.Facts(f.gameId(),7,7,8,9,f.reason(),7L,m)));
    }
    @Test void zeroMoveResignationHasNoMadeUpMove() {
        var e=RuleEvidence.verify(new Review.Facts(UUID.randomUUID(),7,7,8,1,"RESIGNATION",7L,List.of()));
        assertEquals(1,e.size());assertEquals("END",e.getFirst().id());
    }
    @Test void questionAndOutputAreBounded() {
        assertThrows(IllegalArgumentException.class,()->RuleEvidence.checkQuestion("a".repeat(501),"demo","direct"));
        var f=win();var e=RuleEvidence.verify(f);
        assertThrows(IllegalArgumentException.class,()->RuleEvidence.checkAnswer(f,e,new Review.Answer(f.gameId(),9,"demo","rules","解释",List.of("M99"),e,Review.Usage.zero(),0,1,false,"test")));
    }
}
