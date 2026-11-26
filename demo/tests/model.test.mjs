import test from 'node:test';
import assert from 'node:assert/strict';
import { DemoStore, SIZE, emptyBoard, hasWon, replayBoard } from '../model.mjs';
import { replayCsv } from '../replay-export.mjs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('terminal replay displays the selected sample position and rejects invalid steps', () => {
  const cli = fileURLToPath(new URL('../replay-sample.mjs', import.meta.url));
  const run = (...args) => spawnSync(process.execPath, [cli, ...args], { encoding: 'utf8' });
  for (const [args, step, last, blacks] of [[['0'], 0, '-', 0], [['8'], 8, 'G9', 4], [[], 9, 'H8', 5]]) {
    const { status, stdout } = run(...args);
    assert.equal(status, 0);
    const [summary, labels, ...rows] = stdout.trimEnd().split(/\r?\n/);
    assert.ok(summary.startsWith(`Step ${step}/9 | last: ${last} |`));
    assert.equal(labels.trim(), 'A B C D E F G H I J K L M N O');
    assert.equal(rows.length, 15);
    assert.equal(rows[7].trim().split(/\s+/).slice(4, 4 + blacks).join(''), 'X'.repeat(blacks));
    assert.equal(rows.join('').match(/X/g)?.length ?? 0, blacks);
    assert.equal(rows.join('').match(/O/g)?.length ?? 0, Math.floor(step / 2));
  }
  const csv = run('--csv');
  assert.equal(csv.status, 0);
  assert.equal(csv.stdout, replayCsv(new DemoStore().history.find(game => game.sample).moves));
  for (const args of [['10'], ['2.5'], ['1', '2'], ['--csv', '8']]) {
    const result = run(...args);
    assert.equal(result.status, 1);
    assert.equal(result.stdout, '');
    assert.match(result.stderr, /0\.\.9/);
  }
});

test('between replay steps lists only new moves with their original numbers', () => {
  const cli = fileURLToPath(new URL('../replay-sample.mjs', import.meta.url));
  const run = (...args) => spawnSync(process.execPath, [cli, '--between', ...args], { encoding: 'utf8' });
  for (const [from, to, lines] of [['7', '9', ['move 8: white G9', 'move 9: black H8']],
    ['0', '1', ['move 1: black D8']], ['9', '9', ['No new moves.']]]) {
    const result = run(from, to);
    assert.equal(result.status, 0);
    assert.deepEqual(result.stdout.trim().split(/\r?\n/), lines);
  }
  for (const args of [['9','8'], ['0','10'], ['1.5','4'], ['1'], ['0','1','2']]) {
    const result = run(...args);
    assert.equal(result.status, 1);
    assert.equal(result.stdout, '');
    assert.match(result.stderr, /--between/);
  }
});

test('coordinate lookup distinguishes recorded moves, empty intersections and invalid input', () => {
  const cli = fileURLToPath(new URL('../replay-sample.mjs', import.meta.url));
  const run = (...args) => spawnSync(process.execPath, [cli, '--at', ...args], { encoding: 'utf8' });
  for (const [position, expected] of [['h8', 'H8: move 9, black'], ['G9', 'G9: move 8, white'],
    ['A1', 'A1: empty'], ['O15', 'O15: empty']]) {
    const result = run(position);
    assert.equal(result.status, 0);
    assert.equal(result.stdout.trimEnd(), expected);
  }
  for (const [pos, step, expected] of [['D8', '0', 'empty'], ['D8', '8', 'move 1, black'],
    ['G9', '7', 'empty'], ['G9', '8', 'move 8, white'], ['H8', '8', 'empty'], ['H8', '9', 'move 9, black']]) {
    const result = run(pos, step);
    assert.equal(result.status, 0);
    assert.equal(result.stdout.trimEnd(), `${pos}: ${expected}`);
  }
  for (const args of [[], ['H0'], ['A16'], ['P1'], ['H08'], ['A1', 'B2'], ['H8', '10'], ['H8', '-1'], ['H8', '1.5']]) {
    const result = run(...args);
    assert.equal(result.status, 1);
    assert.equal(result.stdout, '');
    assert.match(result.stderr, /--at A1\.\.O15/);
  }
});

function playing() {
  const store = new DemoStore({ random: () => 0 });
  const code = store.createRoom();
  store.ready(code);
  store.switchPlayer('chen'); store.joinRoom(code); store.ready(code);
  return { store, code, room: store.room(code) };
}

test('CSV preserves both colors, edge positions and move order without player fields', () => {
  const moves = [
    { row: 14, col: 14, stone: 1, playerId: 'black-id' },
    { row: 0, col: 0, stone: 2, playerId: 'white-id' },
  ];
  assert.equal(replayCsv(moves), 'move,stone,position\r\n1,black,O15\r\n2,white,A1\r\n');
  assert.equal(replayCsv([]), 'move,stone,position\r\n');
  assert.throws(() => replayCsv([moves[0], { ...moves[0], stone: 2 }]), /非法落子/);
});

test('both colors can finish a replay, but a move after five cannot be replayed or exported', () => {
  for (const winner of [1, 2]) {
    const moves = Array.from({ length: winner === 1 ? 9 : 10 }, (_, i) => {
      const stone = i % 2 + 1, n = Math.floor(i / 2);
      return { stone, row: stone === winner ? 0 : 2, col: stone === winner ? n : n * 2 };
    });
    const continued = [...moves, { row: 14, col: 14, stone: 3 - winner }];
    assert.equal(replayBoard(continued, moves.length)[0][4], winner);
    assert.doesNotThrow(() => replayCsv(moves));
    assert.throws(() => replayBoard(continued, continued.length), /五子连线后/);
    assert.throws(() => replayCsv(continued), /五子连线后/);
  }
});

test('a room keeps two distinct seats and remains usable after a rejected join', () => {
  const store = new DemoStore();
  const code = store.createRoom();
  assert.deepEqual(store.room(code).members, ['lin']);
  store.joinRoom(code);
  assert.equal(store.room(code).members.length, 1);
  store.switchPlayer('chen'); store.joinRoom(code);
  assert.equal(store.joinRoom(code), code);
  store.switchPlayer('lin');
  assert.equal(store.joinRoom(code), code);
  assert.deepEqual(store.room(code).members, ['lin', 'chen']);
  store.switchPlayer('qiao');
  const fullRoomSnapshot = structuredClone(store.room(code));
  assert.throws(() => store.joinRoom(code), /房间已满/);
  assert.deepEqual(store.room(code), fullRoomSnapshot);
  store.switchPlayer('lin'); store.ready(code);
  store.switchPlayer('chen'); store.ready(code);
  assert.equal(store.room(code).status, 'playing');
  assert.deepEqual(store.room(code).members, ['lin', 'chen']);
});

test('existing players can re-enter a full ongoing game without changing its state', () => {
  const { store, code, room } = playing();
  store.switchPlayer('lin'); store.play(code, 7, 7);
  const snapshot = structuredClone(room);
  for (const identifier of ['lin', 'chen']) {
    store.switchPlayer(identifier);
    assert.equal(store.joinRoom(code), code);
    assert.deepEqual(room, snapshot);
  }
});

test('unknown room, full room and normalized room codes behave differently', () => {
  const store = new DemoStore();
  assert.throws(() => store.joinRoom('NOPE'), /不存在/);
  assert.throws(() => store.joinRoom('FULL15'), /已满/);
  assert.equal(store.joinRoom(' pr2026 '), 'PR2026');
});

test('both distinct players must prepare before the game starts', () => {
  const store = new DemoStore({ random: () => 0 });
  const code = store.createRoom();
  store.ready(code); store.ready(code);
  assert.equal(store.room(code).status, 'waiting');
  store.switchPlayer('chen');
  assert.throws(() => store.ready(code), /先加入/);
  store.joinRoom(code); store.ready(code);
  assert.equal(store.room(code).status, 'playing');
  assert.equal(store.room(code).turn, 'lin');
});

test('first-game color assignment uses both random branches', () => {
  const store = new DemoStore({ random: () => 0.9 });
  const code = store.createRoom(); store.ready(code);
  store.switchPlayer('chen'); store.joinRoom(code); store.ready(code);
  assert.equal(store.room(code).black, 'chen');
  assert.equal(store.room(code).turn, 'chen');
});

test('invalid turns and duplicate positions do not change the game', () => {
  const { store, code, room } = playing();
  assert.throws(() => store.play(code, 7, 7), /还没轮到/);
  store.switchPlayer('lin'); store.play(code, 7, 7);
  assert.throws(() => store.play(code, 7, 8), /还没轮到/);
  store.switchPlayer('chen');
  assert.throws(() => store.play(code, 7, 7), /已经有棋子/);
  assert.equal(room.moves.length, 1);
  assert.equal(room.turn, 'chen');
});

test('out-of-bounds, fractional and invalid coordinates are rejected', () => {
  const { store, code, room } = playing(); store.switchPlayer('lin');
  for (const [row, col] of [[-1, 0], [15, 0], [0, 15], [1.5, 0], [NaN, 0]]) {
    assert.throws(() => store.play(code, row, col), /超出了/);
  }
  assert.equal(room.moves.length, 0);
});

for (const [name, dr, dc, r, c] of [
  ['horizontal edge', 0, 1, 0, 0], ['vertical edge', 1, 0, 0, 14],
  ['descending diagonal', 1, 1, 0, 0], ['ascending diagonal', 1, -1, 0, 14],
]) {
  test(`five stones win along ${name}, four stones do not`, () => {
    const board = emptyBoard();
    for (let i = 0; i < 4; i++) board[r + dr * i][c + dc * i] = 1;
    assert.equal(hasWon(board, r, c), false);
    board[r + dr * 4][c + dc * 4] = 1;
    assert.equal(hasWon(board, r + dr * 2, c + dc * 2), true);
  });
}

test('freestyle overlines win, disconnected stones and empty cells do not', () => {
  const board = emptyBoard();
  for (let col = 2; col < 8; col++) board[7][col] = 2;
  assert.equal(hasWon(board, 7, 4), true);
  board[7][4] = 0;
  assert.equal(hasWon(board, 7, 4), false);
  assert.equal(hasWon(board, 7, 2), false);
});

test('a final move in the middle joins both halves of an ascending diagonal and wins', () => {
  const { store, code, room } = playing();
  const blackMoves = [[5, 9], [6, 8], [8, 6], [9, 5]];
  for (let index = 0; index < blackMoves.length; index++) {
    store.switchPlayer('lin'); store.play(code, ...blackMoves[index]);
    store.switchPlayer('chen'); store.play(code, 0, 2 * index);
  }
  assert.equal(room.status, 'playing');
  assert.equal(room.board[7][7], 0);
  store.switchPlayer('lin'); store.play(code, 7, 7);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, 'lin');
  assert.equal(room.reason, 'five');
  assert.equal(store.history[0].moves.at(-1).row, 7);
  assert.equal(store.history[0].moves.at(-1).col, 7);
});

test('empty or opponent cells interrupt same-color totals in every line', () => {
  for (const [dr, dc] of [[0, 1], [1, 0], [1, 1], [1, -1]]) {
    for (const stone of [1, 2]) {
      for (const blocker of [0, 3 - stone]) {
        const board = emptyBoard();
        for (const offset of [-2, -1, 0, 2, 3]) {
          board[7 + dr * offset][7 + dc * offset] = stone;
        }
        board[7 + dr][7 + dc] = blocker;
        assert.equal(hasWon(board, 7, 7), false);
      }
    }
  }
});

test('a completed game produces one immutable replay and cannot receive more moves', () => {
  const { store, code, room } = playing();
  for (let index = 0; index < 9; index++) {
    store.switchPlayer(room.turn);
    store.play(code, index % 2 ? 8 : 7, 3 + Math.floor(index / 2));
  }
  assert.equal(room.status, 'finished'); assert.equal(room.winner, 'lin');
  assert.equal(store.history.length, 2);
  assert.throws(() => store.play(code, 0, 0), /已经结束/);
  store.finish(room, 'chen', 'five');
  assert.equal(store.history.length, 2);
  assert.equal(store.history[0].winner, 'lin');
  assert.deepEqual(replayBoard(store.history[0].moves, 9), room.board);
});

test('a valid full board without five in a row is a draw', () => {
  const { store, code, room } = playing();
  const black = [], white = [];
  for (let row = 0; row < SIZE; row++) for (let col = 0; col < SIZE; col++) {
    ((row + 2 * col) % 4 < 2 ? black : white).push([row, col]);
  }
  assert.equal(black.length, white.length + 1);
  for (let i = 0; i < SIZE * SIZE; i++) {
    store.switchPlayer(room.turn);
    const [row, col] = (i % 2 ? white : black)[Math.floor(i / 2)];
    store.play(code, row, col);
  }
  assert.equal(room.status, 'finished'); assert.equal(room.reason, 'draw');
  assert.equal(room.winner, null);
});

test('simulated disconnection freezes the view and reconnect restores the latest board', () => {
  const { store, code, room } = playing();
  store.switchPlayer('lin'); store.play(code, 7, 7); store.toggleConnection();
  assert.throws(() => store.play(code, 7, 8), /模拟断线/);
  store.switchPlayer('chen'); store.play(code, 8, 7);
  store.switchPlayer('lin');
  assert.equal(store.viewRoom(code).moves.length, 1);
  assert.equal(room.moves.length, 2);
  store.toggleConnection();
  assert.equal(store.viewRoom(code).moves.length, 2);
  assert.equal(store.viewRoom(code).board[8][7], 2);
});

test('spectators cannot resign, and resignation only settles once', () => {
  const { store, code, room } = playing();
  store.switchPlayer('qiao'); assert.throws(() => store.resign(code), /不是这局/);
  store.switchPlayer('lin'); store.resign(code);
  assert.equal(room.winner, 'chen'); assert.equal(room.reason, 'resign');
  assert.throws(() => store.resign(code), /没有进行中/);
  assert.equal(store.history.length, 2);
});

test('rematches exchange colors, clear readiness and preserve the previous replay', () => {
  const { store, code, room } = playing();
  store.switchPlayer('lin'); store.play(code, 7, 7); store.resign(code);
  const history = structuredClone(store.history[0]);
  store.rematch(code);
  assert.equal(room.status, 'waiting'); assert.equal(room.black, 'chen');
  assert.deepEqual(room.ready, []); assert.equal(room.moves.length, 0);
  store.ready(code); store.switchPlayer('chen'); store.ready(code);
  assert.equal(room.turn, 'chen'); assert.equal(room.round, 2);
  assert.deepEqual(store.history[0], history);
});

test('replay detects duplicate, out-of-bounds and wrong-color records', () => {
  const a = { row: 7, col: 7, stone: 1 }, b = { row: 8, col: 7, stone: 2 };
  assert.equal(replayBoard([a, b], 1)[8][7], 0);
  for (const moves of [[a, { ...a, stone: 2 }], [{ ...a, row: 15 }], [{ ...a, stone: 2 }]]) {
    assert.throws(() => replayBoard(moves, moves.length), /非法落子/);
  }
  assert.throws(() => replayBoard([a], 2), /步数/);
});
