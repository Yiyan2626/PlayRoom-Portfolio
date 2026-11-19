import { replayBoard } from './model.mjs';

export const notation = ({ row, col }) => `${String.fromCharCode(65 + col)}${row + 1}`;

export function replayCsv(moves) {
  replayBoard(moves, moves.length);
  return ['move,stone,position', ...moves.map((move, index) =>
    `${index + 1},${move.stone === 1 ? 'black' : 'white'},${notation(move)}`)].join('\r\n') + '\r\n';
}
