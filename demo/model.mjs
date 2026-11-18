// This is local demo state. It is not a server, authentication or persistence.
export const SIZE = 15;
export const PLAYERS = [
  { id: 'lin', name: '林同学', initial: '林', color: 'mint' },
  { id: 'chen', name: '陈同学', initial: '陈', color: 'peach' },
  { id: 'qiao', name: '乔同学', initial: '乔', color: 'lilac' },
];
export const emptyBoard = () => Array.from({ length: SIZE }, () => Array(SIZE).fill(0));
export const player = id => PLAYERS.find(item => item.id === id);

export function hasWon(board, row, col) {
  const stone = board[row]?.[col];
  if (!stone) return false;
  return [[0, 1], [1, 0], [1, 1], [1, -1]].some(([dr, dc]) => {
    let count = 1;
    for (const sign of [-1, 1]) {
      let r = row + dr * sign, c = col + dc * sign;
      while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] === stone) {
        count++; r += dr * sign; c += dc * sign;
      }
    }
    return count >= 5;
  });
}

export function replayBoard(moves, step) {
  if (!Number.isInteger(step) || step < 0 || step > moves.length) throw new Error('复盘步数不正确');
  const board = emptyBoard();
  for (let index = 0; index < step; index++) {
    const { row, col, stone } = moves[index];
    if (!Number.isInteger(row) || !Number.isInteger(col) || row < 0 || row >= SIZE || col < 0 || col >= SIZE ||
        stone !== (index % 2 === 0 ? 1 : 2) || board[row][col]) throw new Error('棋谱包含非法落子');
    board[row][col] = stone;
  }
  return board;
}

function makeRoom(code, host) {
  return { code, members: [host], ready: [], status: 'waiting', black: null, white: null,
    turn: null, board: emptyBoard(), moves: [], winner: null, reason: null, round: 0 };
}

function sampleHistory() {
  const moves = Array.from({ length: 9 }, (_, index) => ({
    row: index % 2 === 0 ? 7 : 8,
    col: 3 + Math.floor(index / 2),
    stone: index % 2 === 0 ? 1 : 2,
    playerId: index % 2 === 0 ? 'lin' : 'chen',
  }));
  return [{ id: 'sample-1', code: 'PR2025', members: ['lin', 'chen'], black: 'lin', white: 'chen',
    moves, winner: 'lin', reason: 'five', date: '2026-10-10T08:30:00+08:00', sample: true }];
}

export class DemoStore {
  constructor({ random = Math.random } = {}) {
    this.random = random;
    this.activePlayer = 'lin';
    this.online = { lin: true, chen: true, qiao: true };
    this.frozen = new Map();
    this.rooms = new Map();
    this.history = sampleHistory();
    this.nextCode = 4826;
    this.rooms.set('PR2026', makeRoom('PR2026', 'chen'));
    const full = makeRoom('FULL15', 'chen'); full.members.push('qiao');
    this.rooms.set(full.code, full);
  }

  switchPlayer(id) {
    if (!PLAYERS.some(item => item.id === id)) throw new Error('玩家不存在');
    this.activePlayer = id;
  }

  requireOnline() {
    if (!this.online[this.activePlayer]) throw new Error('你已模拟断线，请先恢复连接');
  }

  room(code) {
    const room = this.rooms.get(String(code).trim().toUpperCase());
    if (!room) throw new Error('这个房间不存在，请检查房间码');
    return room;
  }

  viewRoom(code) {
    return this.frozen.get(this.activePlayer)?.get(code) ?? this.room(code);
  }

  createRoom() {
    this.requireOnline();
    const code = `PR${this.nextCode++}`;
    this.rooms.set(code, makeRoom(code, this.activePlayer));
    return code;
  }

  joinRoom(code) {
    this.requireOnline();
    const room = this.room(code);
    if (room.members.includes(this.activePlayer)) return room.code;
    if (room.members.length >= 2) throw new Error('房间已满，两个座位都有人了');
    if (room.status !== 'waiting') throw new Error('这个房间已经开始对局');
    room.members.push(this.activePlayer);
    return room.code;
  }

  ready(code) {
    this.requireOnline();
    const room = this.room(code), id = this.activePlayer;
    if (!room.members.includes(id)) throw new Error('请先加入这个房间');
    if (room.status !== 'waiting') throw new Error('当前不能准备');
    if (!room.ready.includes(id)) room.ready.push(id);
    if (room.members.length === 2 && room.ready.length === 2) {
      if (!room.black) {
        const [a, b] = room.members;
        [room.black, room.white] = this.random() < 0.5 ? [a, b] : [b, a];
      }
      room.status = 'playing'; room.turn = room.black; room.round++;
    }
  }

  play(code, row, col) {
    this.requireOnline();
    const room = this.room(code), id = this.activePlayer;
    if (room.status !== 'playing') throw new Error('对局尚未开始或已经结束');
    if (!room.members.includes(id)) throw new Error('你不是这局的玩家');
    if (room.turn !== id) throw new Error('还没轮到你，请切换到当前执棋玩家');
    if (!Number.isInteger(row) || !Number.isInteger(col) || row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
      throw new Error('落子超出了棋盘范围');
    }
    if (room.board[row][col]) throw new Error('这个位置已经有棋子了');
    const stone = id === room.black ? 1 : 2;
    room.board[row][col] = stone; room.moves.push({ row, col, stone, playerId: id });
    if (hasWon(room.board, row, col)) this.finish(room, id, 'five');
    else if (room.moves.length === SIZE * SIZE) this.finish(room, null, 'draw');
    else room.turn = id === room.black ? room.white : room.black;
  }

  finish(room, winner, reason) {
    if (room.status === 'finished') return;
    room.status = 'finished'; room.winner = winner; room.reason = reason; room.turn = null;
    this.history.unshift(structuredClone({ id: `${room.code}-${room.round}`, code: room.code,
      members: room.members, black: room.black, white: room.white, moves: room.moves,
      winner, reason, date: new Date().toISOString(), sample: false }));
  }

  resign(code) {
    this.requireOnline();
    const room = this.room(code);
    if (!room.members.includes(this.activePlayer)) throw new Error('你不是这局的玩家');
    if (room.status !== 'playing') throw new Error('当前没有进行中的对局');
    this.finish(room, room.members.find(id => id !== this.activePlayer), 'resign');
  }

  rematch(code) {
    this.requireOnline();
    const room = this.room(code);
    if (!room.members.includes(this.activePlayer)) throw new Error('你不是这个房间的玩家');
    if (room.status !== 'finished') throw new Error('请先完成当前对局');
    [room.black, room.white] = [room.white, room.black];
    Object.assign(room, { ready: [], status: 'waiting', board: emptyBoard(), moves: [], turn: null, winner: null, reason: null });
  }

  toggleConnection() {
    const id = this.activePlayer;
    this.online[id] = !this.online[id];
    if (!this.online[id]) this.frozen.set(id, new Map([...this.rooms].map(([code, room]) => [code, structuredClone(room)])));
    else this.frozen.delete(id);
    return this.online[id];
  }
}
