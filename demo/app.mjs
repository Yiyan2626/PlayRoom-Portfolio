import { DemoStore, PLAYERS, SIZE, player, replayBoard } from './model.mjs';
import { notation, replayCsv } from './replay-export.mjs';

const store = new DemoStore();
const ui = { page: 'welcome', roomCode: null, replayId: null, step: 0, modal: false };
const app = document.querySelector('#app');
let renderedPage = null;
const paths = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  history: '<path d="M5 4h13a2 2 0 0 1 2 2v14H7a3 3 0 0 1-3-3V6a2 2 0 0 1 1-2Z"/><path d="M4 17a3 3 0 0 1 3-3h13M9 8h7"/>',
  rules: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  arrow: '<path d="M5 12h14m-5-5 5 5-5 5"/>',
  back: '<path d="M19 12H5m5-5-5 5 5 5"/>',
  link: '<path d="m10 13 4-4m-6 6-2 2a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m0 10a4 4 0 0 0 6 0l4-4a4 4 0 0 0-6-6l-2 2" transform="translate(2 0) scale(.9)"/>',
  cup: '<path d="M7 3h10v6a5 5 0 0 1-10 0ZM7 5H4v3a4 4 0 0 0 4 4m9-7h3v3a4 4 0 0 1-4 4m-4 2v5m-4 2h8"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
};
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] ?? paths.grid}</svg>`;
const esc = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const avatar = id => `<span class="avatar ${player(id).color}">${player(id).initial}</span>`;
const active = () => player(store.activePlayer);
const statusText = room => ({ waiting: '等待开局', playing: '正在对局', finished: '已结束' }[room.status]);
const dateText = value => new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value));

function toast(message) {
  const box = document.querySelector('#toast');
  box.textContent = message; box.classList.add('show');
  clearTimeout(toast.timer); toast.timer = setTimeout(() => box.classList.remove('show'), 4500);
}

function intro(eyebrow, title, subtitle, extra = '') {
  return `<section class="page-intro"><div><p class="eyebrow">${eyebrow}</p><h1 tabindex="-1">${title}</h1><p class="subtitle">${subtitle}</p></div>${extra}</section>`;
}

function tools(inRoom = false) {
  return `<aside class="demo-tools ${inRoom ? 'room-tools' : ''}" aria-label="Demo 试用工具"><div><span class="tools-title">试用工具</span> · 一个人也能扮演双方，体验完整流程</div><div class="tools-controls"><label for="role-select">当前扮演</label><select id="role-select">${PLAYERS.slice(0, 2).map(p => `<option value="${p.id}" ${p.id === store.activePlayer ? 'selected' : ''}>${p.name}</option>`).join('')}</select><button class="tool-button" data-action="connection">${store.online[store.activePlayer] ? '模拟断线' : '恢复连接'}</button></div></aside>`;
}

function welcome() {
  const pieces = [[39, 40, 0], [48, 49, 1], [48, 40, 0], [57, 49, 1], [57, 40, 0], [66, 49, 1], [66, 40, 0], [39, 58, 1]];
  return `${intro('A LITTLE ROOM FOR A GOOD GAME', '棋局之间，刚好有你。', '一间房、两位朋友，一局不慌不忙的五子棋。')}<section class="card entry-grid"><div class="entry-copy"><p class="eyebrow">PLAYROOM / FIRST MOVE</p><h1>从一颗棋子，<br>开始一次相遇。</h1><p>邀请朋友进入你的房间。轮流落子，连成五颗，再回看那些差一点就赢的瞬间。</p><button class="primary" data-action="enter">以林同学身份体验 ${icon('arrow')}</button><p class="hint">模拟身份 · 无需注册 · 刷新页面将重置演示</p></div><div class="entry-art" aria-hidden="true"><div class="mini-board"><div class="mini-board-grid"></div>${pieces.map(([x, y, white]) => `<span class="mini-piece ${white ? 'white' : ''}" style="left:${x}%;top:${y}%"></span>`).join('')}</div><span class="entry-caption">LESS NOISE. MORE PLAY.</span></div></section><div class="card howto"><strong>这次可以体验</strong><span>邀请入房</span><span>双人对局</span><span>模拟断线恢复</span><span>棋谱复盘</span></div>`;
}

function roomPreview(room, index) {
  const owned = room.members.includes(store.activePlayer);
  return `<article class="card room-preview"><header><p class="eyebrow" style="margin:0">ROOM / ${esc(room.code)}</p><span class="tiny-tag ${room.members.length === 2 ? 'full' : ''}">${!owned && room.members.length === 2 && room.status === 'waiting' ? '房间已满' : statusText(room)}</span></header><h3>${owned ? '你的对局房间' : index === 0 ? '午后练习局' : '好友切磋局'}</h3><p>${avatar(room.members[0])}${player(room.members[0]).name}的房间</p><footer><span class="hint" style="margin:0">${room.members.length} / 2 位玩家</span><button class="${owned ? 'primary' : 'secondary'}" data-action="join" data-code="${esc(room.code)}">${owned ? '返回房间' : '加入房间'}</button></footer></article>`;
}

function lobby() {
  const rooms = [...store.rooms.values()].sort((a, b) => Number(b.members.includes(store.activePlayer)) - Number(a.members.includes(store.activePlayer))).slice(0, 3);
  return `${intro('PLAY LOBBY', `下午好，${active().name}。`, '找一位朋友，把今天的第一步落在这里。', '<div class="intro-meta"><span class="online-dot"></span>轻松开局，认真落子<br>15 × 15 · 自由五子棋</div>')}
    <div class="lobby-actions"><section class="card create-card"><div class="card-symbol">${icon('plus')}</div><h2>留一间房给朋友</h2><p>创建专属房间，分享房间码，等你的对手入座。</p><button class="primary" data-action="create">创建房间 ${icon('arrow')}</button><div class="stone-decoration" aria-hidden="true"></div></section>
    <section class="card join-card"><p class="eyebrow">HAVE AN INVITATION?</p><h2>赴一场棋局之约</h2><p>朋友已经开好房间？输入房间码就能找到彼此。</p><form id="join-form" class="join-form"><input id="room-code" aria-label="房间码" placeholder="例如 PR2026" maxlength="12" autocomplete="off" required><button class="secondary" type="submit">加入房间 ${icon('arrow')}</button></form><div class="hint">试试 PR2026；FULL15 可以体验房间已满。</div></section></div>
    <div class="section-heading"><h2>可以赴约的房间</h2><span>演示房间 · 本页模拟</span></div><div class="room-list">${rooms.map(roomPreview).join('')}</div>
    <div class="section-heading"><h2>上一局，值得再看一眼</h2><button class="quiet" data-action="navigate" data-page="history">全部棋谱 ${icon('arrow')}</button></div>${historyRow(store.history[0])}${tools()}`;
}

function boardMarkup(board, moves, isReplay = false) {
  const last = moves.at(-1);
  return `<div class="board" role="group" aria-label="15 行 15 列五子棋棋盘">${board.flatMap((line, row) => line.map((stone, col) => {
    const star = (row === 7 && col === 7) || ([3, 11].includes(row) && [3, 11].includes(col));
    const lastStone = last?.row === row && last?.col === col;
    return `<button class="cell ${star ? 'star' : ''}" data-action="move" data-row="${row}" data-col="${col}" aria-label="第${row + 1}行第${col + 1}列${stone ? stone === 1 ? '黑棋' : '白棋' : '空位'}" aria-disabled="${Boolean(stone || isReplay)}" ${isReplay ? 'disabled' : ''}>${star ? '<span class="star-dot"></span>' : ''}${stone ? `<span class="stone ${stone === 2 ? 'white' : ''} ${lastStone ? 'last' : ''}"></span>` : ''}</button>`;
  })).join('')}</div>`;
}

function seat(room, id, index) {
  if (!id) return `<div class="player-seat empty-seat"><span class="avatar">＋</span><div><div class="name">空着的座位</div><div class="detail">等你的朋友来赴约</div></div></div>`;
  const detail = room.black ? id === room.black ? '执黑 · 先行' : '执白 · 后行' : `${index === 0 ? '房主' : '受邀玩家'}`;
  return `<div class="player-seat">${avatar(id)}<div><div class="name">${player(id).name}${id === store.activePlayer ? ' <span class="hint">（你）</span>' : ''}</div><div class="detail">${detail} · ${store.online[id] ? '在线' : '模拟断线'}</div></div><span class="seat-state">${room.status === 'waiting' ? room.ready.includes(id) ? '已准备' : '未准备' : room.turn === id ? '当前回合' : ''}</span></div>`;
}

function roomPage() {
  const room = store.viewRoom(ui.roomCode), member = room.members.includes(store.activePlayer);
  const finished = room.status === 'finished';
  let action = '';
  if (!member) action = '<button class="primary full-width" data-action="join" data-code="' + esc(room.code) + '">以当前身份加入</button>';
  else if (room.status === 'waiting') action = `<button class="primary full-width" data-action="ready" ${room.ready.includes(store.activePlayer) ? 'disabled' : ''}>${room.ready.includes(store.activePlayer) ? '已准备，等待另一位玩家' : '我准备好了'}</button>`;
  else if (room.status === 'playing') action = '<button class="danger full-width" data-action="ask-resign">认输并结束这局</button>';
  else action = '<button class="primary full-width" data-action="rematch">再来一局，交换黑白</button>';
  const title = room.status === 'waiting' ? '让朋友先坐下来。' : finished ? '这一局，落定了。' : '你的下一步，会落在哪里？';
  const turn = room.turn ? `${player(room.turn).name}执${room.turn === room.black ? '黑' : '白'}，轮到你${room.turn === store.activePlayer ? '了' : '的对手'}` : statusText(room);
  const result = room.reason === 'draw' ? '势均力敌，平局。' : room.winner ? `${player(room.winner).name}获胜。` : '';
  const history = store.history.find(record => record.id === `${room.code}-${room.round}`);
  return `${intro('YOUR PLAYROOM', title, `房间 ${esc(room.code)} · ${room.status === 'waiting' ? '双方准备后自动开局。你可以使用试用工具切换玩家。' : '一人一步，五子连线。'}`)}${tools(true)}
    ${!store.online[store.activePlayer] ? '<div class="alert"><span>已模拟断线：棋盘停留在断线前，操作暂停。恢复连接后会同步最新棋局。</span><button class="tool-button" data-action="connection">恢复连接</button></div>' : ''}
    ${finished ? `<div class="result-banner"><p class="eyebrow">GOOD GAME, WELL PLAYED</p><h2>${result}</h2><p>${room.reason === 'resign' ? '对手选择认输。' : room.reason === 'draw' ? '棋盘已满，没有五子连线。' : '五子连线，棋局结束。'} ${room.moves.length} 手棋，留下一段值得回看的过程。</p>${history ? `<div class="button-row"><button class="secondary" data-action="replay" data-id="${history.id}">${icon('history')} 复盘这局</button></div>` : ''}</div>` : ''}
    <div class="room-layout"><section class="card board-card"><header class="board-header"><h2 data-turn-player="${room.turn ?? ''}">${turn}</h2><span><i class="online-dot ${store.online[store.activePlayer] ? '' : 'off'}"></i>${store.online[store.activePlayer] ? '演示连接正常' : '演示连接中断'}</span></header><div class="board-wrap">${boardMarkup(room.board, room.moves)}</div><footer class="board-footer"><span>第 <strong data-move-count>${room.moves.length}</strong> 手 · 黑棋先行</span><span>最后一手用小点标记</span></footer></section>
    <aside class="room-side"><section class="card code-box"><p>邀请你的对手</p><div class="code-value">${esc(room.code)}</div><button class="quiet" data-action="copy">${icon('link')} 复制房间码</button></section><section class="card players-card"><h3>房间里的两位玩家</h3>${seat(room, room.members[0], 0)}${seat(room, room.members[1], 1)}<div class="button-row">${action}</div>${room.members.length < 2 ? '<button class="quiet" data-action="friend">模拟好友加入</button>' : ''}</section><div class="room-note"><strong>${finished ? '回看，也是一种进步。' : '别着急，想好再落。'}</strong>${finished ? '复盘时可逐步查看棋谱；再开一局会交换执棋颜色。' : '同一浏览器中，切换玩家即可轮流落子。这是一场模拟对局。'}</div></aside></div><button class="quiet" style="margin-top:17px" data-action="navigate" data-page="lobby">${icon('back')} 返回大厅，房间仍会保留</button>`;
}

function historyRow(record) {
  return `<article class="card history-row"><div class="history-symbol">${icon('history')}</div><div><h3>${player(record.black).name} <span class="hint">vs</span> ${player(record.white).name}</h3><p>${dateText(record.date)} · ${record.moves.length} 手 · ${record.winner ? player(record.winner).name + '获胜' : '平局'} · ${record.sample ? '示例棋谱' : '本次演示'}</p></div><button class="secondary" data-action="replay" data-id="${record.id}">查看棋谱 ${icon('arrow')}</button></article>`;
}

function historyPage() {
  return `${intro('GAME JOURNAL', '每一步，都有迹可循。', '胜负留在结果里，想法留在棋谱里。')}<div class="history-list">${store.history.filter(record => record.members.includes(store.activePlayer)).map(historyRow).join('') || '<div class="card blank">还没有你的棋谱，先去大厅开一局吧。</div>'}</div>${tools()}`;
}

function replayPage() {
  const record = store.history.find(item => item.id === ui.replayId);
  const moves = record.moves.slice(0, ui.step), board = replayBoard(record.moves, ui.step);
  return `${intro('LOOK BACK, PLAY BETTER', '重新走过，这一盘棋。', `${player(record.black).name} vs ${player(record.white).name} · ${record.sample ? '示例棋谱' : '本次演示棋谱'}`)}<div class="room-layout"><section class="card board-card"><header class="board-header"><h2>棋谱复盘</h2><span data-replay-step>第 ${ui.step} / ${record.moves.length} 手</span></header><div class="board-wrap">${boardMarkup(board, moves, true)}</div><footer class="board-footer"><span>复盘仅查看，不会改变原对局</span><span>黑棋：${player(record.black).name}</span></footer></section><aside class="room-side"><section class="card players-card"><h3>慢一点，再看一次</h3><div class="replay-controls"><button class="secondary" data-action="step" data-step="0" aria-label="回到开局">｜‹</button><button class="secondary" data-action="step" data-step="${Math.max(0, ui.step - 1)}" aria-label="上一手" ${ui.step === 0 ? 'disabled' : ''}>‹</button><button class="secondary" data-action="step" data-step="${Math.min(record.moves.length, ui.step + 1)}" aria-label="下一手" ${ui.step === record.moves.length ? 'disabled' : ''}>›</button><button class="secondary" data-action="step" data-step="${record.moves.length}" aria-label="跳到终局">›｜</button></div><label for="replay-slider" class="hint">跳到第 ${ui.step} 手</label><input id="replay-slider" class="replay-slider" type="range" min="0" max="${record.moves.length}" value="${ui.step}" aria-label="复盘步数"><div class="move-list">${record.moves.map((move, index) => `<div class="move-line ${index + 1 === ui.step ? 'selected' : ''}"><span>${String(index + 1).padStart(2, '0')} · ${move.stone === 1 ? '黑' : '白'}</span><span>${notation(move)}</span></div>`).join('') || '<p class="hint">这局在落子前认输，没有棋谱。</p>'}</div><div class="replay-export"><button class="secondary" data-action="export-replay">导出 CSV</button></div></section><div class="room-note"><strong>${record.winner ? player(record.winner).name + '获胜' : '平局'}</strong>${record.moves.length} 手 · ${record.reason === 'resign' ? '认输结算' : '自由五子棋'}</div></aside></div><button class="quiet" style="margin-top:20px" data-action="navigate" data-page="history">${icon('back')} 返回我的棋谱</button>`;
}

function rulesPage() {
  return `${intro('KEEP IT SIMPLE', '规则很简单，好棋不简单。', '自由五子棋 · 15 × 15 · 两位玩家')}<section class="card rules"><h2>开始之前，知道这几件事就够了</h2><ol><li><strong>双方入座并准备。</strong> 第一局随机分配黑白，黑棋先行；再来一局交换颜色。</li><li><strong>轮流落子。</strong> 每次只能落在空位置上，不能替对方落子。</li><li><strong>连续五颗即获胜。</strong> 横、竖、两种斜线都算；超过五颗也算，不设禁手。</li><li><strong>也可能平局。</strong> 棋盘放满且无人连五，即为平局。</li><li><strong>可以认输和复盘。</strong> 认输前需要确认，结束后可以逐步回看。</li></ol><div class="room-note"><strong>关于这个 Demo</strong>玩家、房间和连接都在当前页面里模拟。没有真实账号和远程对手，刷新页面会重置数据。使用底部试用工具扮演双方；这不能证明真实的后端功能。</div></section>${tools()}`;
}

function render() {
  const previous = document.activeElement;
  const previousId = previous?.id;
  const previousCell = previous?.matches?.('.cell') ? { row: previous.dataset.row, col: previous.dataset.col } : null;
  const previousAction = previous?.dataset?.action;
  const changedPage = renderedPage !== ui.page;
  const navPage = ['room', 'welcome'].includes(ui.page) ? 'lobby' : ui.page === 'replay' ? 'history' : ui.page;
  const pageLabel = { welcome: '欢迎来到 PlayRoom', lobby: '对局大厅', room: '对局房间', history: '我的棋谱', replay: '棋谱复盘', rules: '玩法说明' }[ui.page];
  app.innerHTML = `<div class="shell"><aside class="sidebar"><div class="brand"><span class="brand-mark"></span><span>PlayRoom</span></div><p class="brand-sub">棋局之间</p><nav class="nav" aria-label="主要导航">${[['lobby', 'grid', '对局大厅'], ['history', 'history', '我的棋谱'], ['rules', 'rules', '玩法说明']].map(([page, symbol, text]) => `<button class="${navPage === page ? 'active' : ''}" data-action="navigate" data-page="${page}" aria-label="${text}" ${ui.page === 'welcome' ? 'disabled' : ''}>${icon(symbol)}<span class="nav-label">${text}</span></button>`).join('')}</nav><div class="sidebar-note"><strong>一局棋，一点默契。</strong>慢慢想，认真下。<br>下一步，等你落子。</div><div class="side-bottom"><i class="online-dot"></i>PLAYROOM / DEMO 01</div></aside><div class="workspace"><header class="topbar"><div class="breadcrumb">PlayRoom <span>/</span><strong>${pageLabel}</strong></div><div class="top-right"><span class="demo-badge">Demo · 模拟数据</span><div class="profile">${avatar(store.activePlayer)}<span>${active().name}</span></div></div></header><main id="main">${({ welcome, lobby, room: roomPage, history: historyPage, replay: replayPage, rules: rulesPage }[ui.page])()}<p class="footer-note">仅本页模拟 · 不接真实后端 · 刷新页面重置数据</p></main></div></div>${ui.modal ? `<div class="modal-backdrop"><section class="card modal" role="dialog" aria-modal="true" aria-labelledby="resign-title"><h2 id="resign-title">确定认输吗？</h2><p>这局将结束，并记为对手获胜。你仍然可以复盘棋局或再开一局。</p><div class="button-row"><button class="secondary" data-action="cancel-resign">继续下棋</button><button class="danger" data-action="resign">确认认输</button></div></section></div>` : ''}`;
  if (ui.modal) app.querySelector('[data-action="cancel-resign"]').focus();
  else {
    const target = changedPage ? app.querySelector('h1') :
      (previousId ? document.getElementById(previousId) : null) ??
      (previousCell ? app.querySelector(`.cell[data-row="${previousCell.row}"][data-col="${previousCell.col}"]`) : null) ??
      (previousAction ? app.querySelector(`button[data-action="${previousAction}"]:not(:disabled)`) : null) ?? app.querySelector('h1');
    target?.focus({ preventScroll: true });
    if (changedPage) window.scrollTo(0, 0);
  }
  renderedPage = ui.page;
}

function perform(callback, message) {
  try {
    callback(); render();
    if (message) toast(message);
    else { clearTimeout(toast.timer); document.querySelector('#toast').classList.remove('show'); document.querySelector('#toast').textContent = ''; }
  }
  catch (error) { toast(error.message); }
}

app.addEventListener('submit', event => {
  if (event.target.id !== 'join-form') return;
  event.preventDefault();
  perform(() => { ui.roomCode = store.joinRoom(document.querySelector('#room-code').value); ui.page = 'room'; });
});
app.addEventListener('change', event => {
  if (event.target.id === 'role-select') perform(() => store.switchPlayer(event.target.value));
  if (event.target.id === 'replay-slider') perform(() => { ui.step = Number(event.target.value); });
});
app.addEventListener('click', async event => {
  const button = event.target.closest('button[data-action]');
  if (!button || button.disabled) return;
  const { action, code, page, id, step, row, col } = button.dataset;
  if (action === 'export-replay') {
    const record = store.history.find(item => item.id === ui.replayId && item.members.includes(store.activePlayer));
    if (!record) return;
    try {
      const url = URL.createObjectURL(new Blob([replayCsv(record.moves)], { type: 'text/csv;charset=utf-8' }));
      const download = document.createElement('a');
      download.href = url;
      download.download = 'playroom-replay.csv';
      download.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { toast(error.message); }
    return;
  }
  if (action === 'copy') {
    try { await navigator.clipboard.writeText(ui.roomCode); toast('房间码已复制；在 Demo 中请切换角色模拟加入'); }
    catch { toast(`房间码：${ui.roomCode}，可以手动复制`); }
    return;
  }
  perform(() => {
    if (action === 'enter') ui.page = 'lobby';
    if (action === 'navigate') { ui.page = page; ui.modal = false; }
    if (action === 'create') { ui.roomCode = store.createRoom(); ui.page = 'room'; }
    if (action === 'join') { ui.roomCode = store.joinRoom(code); ui.page = 'room'; }
    if (action === 'ready') store.ready(ui.roomCode);
    if (action === 'move') store.play(ui.roomCode, Number(row), Number(col));
    if (action === 'connection') store.toggleConnection();
    if (action === 'friend') {
      const room = store.room(ui.roomCode), old = store.activePlayer;
      const friend = old === 'lin' ? 'chen' : 'lin';
      try { store.switchPlayer(friend); store.joinRoom(room.code); } finally { store.switchPlayer(old); }
    }
    if (action === 'ask-resign') ui.modal = true;
    if (action === 'cancel-resign') ui.modal = false;
    if (action === 'resign') { store.resign(ui.roomCode); ui.modal = false; }
    if (action === 'rematch') store.rematch(ui.roomCode);
    if (action === 'replay') { ui.replayId = id; ui.step = store.history.find(record => record.id === id).moves.length; ui.page = 'replay'; }
    if (action === 'step') ui.step = Number(step);
  });
});
document.addEventListener('keydown', event => {
  if (!ui.modal) return;
  if (event.key === 'Escape') { ui.modal = false; render(); app.querySelector('[data-action="ask-resign"]')?.focus(); }
  if (event.key === 'Tab') {
    const buttons = [...app.querySelectorAll('.modal button')];
    const index = buttons.indexOf(document.activeElement);
    event.preventDefault(); buttons[(index + (event.shiftKey ? buttons.length - 1 : 1)) % buttons.length].focus();
  }
});
render();
