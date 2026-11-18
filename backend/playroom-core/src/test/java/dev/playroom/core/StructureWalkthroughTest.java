package dev.playroom.core;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D13可运行的结构实验：用公开方法观察目录、成员、准备集合和棋盘。
 * 这里不启动HTTP、Spring或MySQL；打印只帮助跟踪，真正判断对错的是断言。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StructureWalkthroughTest {
    private final RoomCode code = new RoomCode("ROOM13");
    private final Player host = new Player(new PlayerId(7), "Chen");
    private final Player guest = new Player(new PlayerId(8), "Lin");

    @Test
    @Order(1)
    void directoryPublishesNewSnapshotsButRejectsAThirdIdentity() {
        // 注入固定房间码，避免随机值干扰观察；本实验只创建一个房间。
        var directory = new RoomDirectory(() -> code);
        var first = directory.create(host);
        assertEquals(1, directory.roomCount());
        assertEquals(List.of(host), first.members());
        System.out.println("[D13-1] create: code=" + first.code().value()
                + " roomCount=" + directory.roomCount() + " members=" + first.members().size());

        // 目录把ROOM13对应的值换成新Room；手里的旧Room仍只有房主。
        var full = directory.join(code, guest);
        assertNotSame(first, full);
        assertSame(full, directory.find(code).orElseThrow());
        assertEquals(List.of(host), first.members());
        assertEquals(List.of(host, guest), full.members());
        System.out.println("[D13-1] join 8: oldMembers=" + first.members().size()
                + " currentMembers=" + full.members().size());

        // 身份按ID判断：7还是7。重试不会挤掉座位，也不是隐式改昵称接口。
        var retried = directory.join(code, new Player(new PlayerId(7), "NewChen"));
        assertSame(full, retried);
        assertEquals("Chen", retried.members().get(0).nickname());
        assertEquals(Seat.FIRST, retried.seatOf(host.id()).orElseThrow());
        System.out.println("[D13-1] retry 7: sameRoom=" + (full == retried)
                + " nickname=" + retried.members().get(0).nickname()
                + " members=" + retried.members().size());

        // 9是新身份，满员时失败；异常发生后目录仍指向刚才成功发布的Room。
        var third = new Player(new PlayerId(9), "Qian");
        var error = assertThrows(RoomException.class, () -> directory.join(code, third));
        assertEquals(RoomException.Code.ROOM_FULL, error.code());
        assertSame(full, directory.find(code).orElseThrow());
        assertEquals(List.of(host), first.members());
        assertEquals(List.of(host, guest), full.members());
        assertEquals(1, directory.roomCount());
        System.out.println("[D13-1] join 9: error=" + error.code()
                + " currentUnchanged=" + (full == directory.find(code).orElseThrow())
                + " members=" + full.members().size());
    }

    @Test
    @Order(2)
    void readinessDeduplicatesAndPublishedCollectionsCannotBeEdited() {
        // 这是独立测试，用相同输入重新建房，不依赖上一测试留下的对象。
        var directory = new RoomDirectory(() -> code);
        var first = directory.create(host);
        var full = directory.join(code, guest);
        var ready = directory.setReady(code, host.id(), true);
        var retried = directory.setReady(code, new PlayerId(7), true);

        // Set表达“哪些ID已准备”，不记录重复次数；旧Room的Set仍为空。
        assertNotSame(full, ready);
        assertSame(ready, retried);
        assertSame(ready, directory.find(code).orElseThrow());
        assertEquals(Set.of(), full.readyPlayers());
        assertEquals(Set.of(host.id()), retried.readyPlayers());
        assertEquals(List.of(host), first.members());
        assertEquals(List.of(host, guest), ready.members());
        assertEquals(RoomPhase.WAITING, ready.phase());
        System.out.println("[D13-2] ready 7 twice: oldReady=" + full.readyPlayers().size()
                + " currentReady=" + ready.readyPlayers().size()
                + " retrySameRoom=" + (ready == retried));

        // 不可修改集合把写操作挡在公开入口；调用方不能绕过房间的容量/身份规则。
        assertThrows(UnsupportedOperationException.class,
                () -> ready.members().add(new Player(new PlayerId(9), "Qian")));
        assertThrows(UnsupportedOperationException.class,
                () -> ready.readyPlayers().add(guest.id()));
        assertEquals(2, ready.members().size());
        assertEquals(Set.of(host.id()), ready.readyPlayers());
        System.out.println("[D13-2] direct collection edits: rejected; members="
                + ready.members().size() + " ready=" + ready.readyPlayers().size());
    }

    @Test
    @Order(3)
    void exportedTwoDimensionalArrayCannotRewriteBoardHistory() {
        var position = new BoardPosition(7, 7);
        var empty = new Board();
        var placed = empty.place(position, StoneColor.BLACK);

        // snapshot返回数组副本。先改副本内的一格，再换掉一整行，均不得改到内部棋盘。
        // 若只复制二维数组的外层，第一条数组赋值就会通过共享行污染placed。
        StoneColor[][] exported = placed.snapshot();
        exported[7][7] = StoneColor.WHITE;
        exported[0][0] = StoneColor.BLACK;
        exported[7] = null;

        assertEquals(StoneColor.BLACK, placed.at(position).orElseThrow());
        assertTrue(placed.at(new BoardPosition(0, 0)).isEmpty());
        assertEquals(1, placed.moveCount());
        assertTrue(empty.at(position).isEmpty());
        assertEquals(0, empty.moveCount());
        System.out.println("[D13-3] change exported cell and row: board(7,7)="
                + placed.at(position).orElseThrow() + " moves=" + placed.moveCount());
        System.out.println("[D13-3] original empty board: cellEmpty="
                + empty.at(position).isEmpty() + " moves=" + empty.moveCount());
    }
}
