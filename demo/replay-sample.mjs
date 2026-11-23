import { DemoStore, replayBoard } from './model.mjs';
import { notation, replayCsv } from './replay-export.mjs';

const { moves } = new DemoStore().history.find(game => game.sample);
const args = process.argv.slice(2);
const raw = args[0] ?? String(moves.length);
if (args.length === 1 && raw === '--csv') {
  process.stdout.write(replayCsv(moves));
} else if (args.length > 1 || !/^(0|[1-9]\d*)$/.test(raw) || Number(raw) > moves.length) {
  console.error(`Usage: node demo/replay-sample.mjs [0..${moves.length} | --csv]`);
  process.exitCode = 1;
} else {
  const step = Number(raw);
  const board = replayBoard(moves, step);
  const last = step ? notation(moves[step - 1]) : '-';
  console.log(`Step ${step}/${moves.length} | last: ${last} | X black, O white, . empty`);
  console.log('   ' + board[0].map((_, col) => String.fromCharCode(65 + col)).join(' '));
  for (const [i, row] of board.entries()) {
    console.log(String(i + 1).padStart(2) + ' ' + row.map(stone => '.XO'[stone]).join(' '));
  }
}
