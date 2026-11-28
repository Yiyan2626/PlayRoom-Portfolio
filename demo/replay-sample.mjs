import { DemoStore, replayBoard } from './model.mjs';
import { notation, replayCsv } from './replay-export.mjs';

const { moves } = new DemoStore().history.find(game => game.sample);
const args = process.argv.slice(2);
const raw = args[0] ?? String(moves.length);
const validStep = value => /^(0|[1-9]\d*)$/.test(value) && Number(value) <= moves.length;
if (args.length === 1 && raw === '--csv') {
  process.stdout.write(replayCsv(moves));
} else if (raw === '--check' && args.length === 3 && validStep(args[1]) && Number(args[1]) > 0 && /^[a-o](?:[1-9]|1[0-5])$/i.test(args[2])) {
  const expected = notation(moves[Number(args[1]) - 1]);
  const claimed = args[2].toUpperCase();
  const matches = claimed === expected;
  console.log(`M${args[1]}: claimed ${claimed}, replay ${expected} (${matches ? 'match' : 'mismatch'})`);
  if (!matches) process.exitCode = 2;
} else if (raw === '--between' && args.length === 3 && args.slice(1).every(validStep) && Number(args[1]) <= Number(args[2])) {
  const from = Number(args[1]), to = Number(args[2]);
  if (from === to) console.log('No new moves.');
  for (let i = from; i < to; i++) {
    const move = moves[i];
    console.log(`move ${i + 1}: ${move.stone === 1 ? 'black' : 'white'} ${notation(move)}`);
  }
} else if (raw === '--at' && /^[a-o](?:[1-9]|1[0-5])$/i.test(args[1]) &&
    (args.length === 2 || (args.length === 3 && validStep(args[2])))) {
  const position = args[1].toUpperCase();
  const step = Number(args[2] ?? moves.length);
  const index = moves.findIndex((move, i) => i < step && notation(move) === position);
  const result = index === -1 ? 'empty' : `move ${index + 1}, ${moves[index].stone === 1 ? 'black' : 'white'}`;
  console.log(`${position}: ${result}`);
} else if (args.length > 1 || !validStep(raw)) {
  console.error(`Usage: node demo/replay-sample.mjs [0..${moves.length} | --csv | --check MOVE A1..O15 | --at A1..O15 [0..${moves.length}] | --between FROM TO (0..${moves.length})]`);
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
