// 游戏规范审计（加固版 2026-11-07）：按 GAME-THEME.md §2/§3/§4/§5.1/§6 逐条机器核对
// 严格模式（新游戏）：全项硬性检查；宽松模式（存量）：功能等价检查 + 遗留偏离信息项
// 加固点：布局类型判定（消灭全屏误报）、存档键全量提取（消灭漏检/误报）、热区选择器扩展、
//         §4 裁撤残留检查、theme-color 同步检查、token 基线值比对、JS 颜色字面量检查、
//         setChrome 位置检查、canvas 字号灰区单列、素材前缀补 st-。
// 用法：node audit-gates.mjs [game…]（缺省审计全部游戏目录）
import { existsSync, readFileSync, readdirSync, statSync } from "fs";
import { join } from "path";
import { fileURLToPath } from "url";

const DIR = fileURLToPath(new URL(".", import.meta.url));
const CANONICAL = readFileSync(join(DIR, "block-drop/index.html"), "utf8");

// ── 新游戏清单（出生即按现行 §6 规范实现）──
const STRICT = new Set([
  "tetris", "sudoku", "idiom", "pacman", "invaders", "lights-out",
  "sokoban", "connect-four", "solitaire", "fruit-slice", "air-hockey", "reversi",
  "magic-tower", "crossword", "lianliankan", "match3",
  "word-search", "dots-boxes", "bubble-shooter",
  "hangman", "mastermind", "asteroids",
  "mahjong-solitaire", "xiangqi",
  "suika", "klotski", "pipes",
  "maze", "game24", "whack-a-mole",
  "pitch-pot", "idiom-chain", "tile-tap",
  "jump-jump",
  "stack-match",
  "gold-miner",
  "chai-zi",
  "draw-save",
  "pull-pin",
  "stack-up",
  "stick-bridge",
  "hanoi",
  "tangram",
  "one-stroke", "battle-city",
  "flow-connect", "bomberman", "poem-fill", "peg-solitaire", "matchstick",
  "spider-solitaire", "mini-golf", "penalty", "riddle", "ballz-bounce",
  "doodle-jump", "helix-jump", "blob-eat", "virus-defense", "fishing", "fire-ice", "rope-cut",
  "lane-defense", "tower-defense", "milk-tea", "rock-climb", "kite-duel", "balloon-dart",
  "pancake-flip", "steady-chop", "feather-blow", "lantern-rise", "paper-glide", "domino-chain",
  "marble-run", "pinball-mini", "target-rush", "nut-bolt", "key-turn", "bell-strike",
  "dish-stack", "road-hop", "log-hop", "lift-dodge", "belt-sort", "pizza-toss",
  "latte-line", "sushi-grab", "pocket-putt", "bubble-line", "coin-flick", "hoop-roll",
  "egg-catch", "cargo-balance", "rope-swing", "river-cross", "torch-bridge", "water-jug",
  "scale-ball", "knight-tour", "eight-queens", "kakuro", "calc-doku", "slitherlink",
  "nurikabe", "hashi-wire", "shikaku",
  "ripple-fill", "laser-maze", "gear-mesh", "fuse-cut", "rubik-mini", "rotate-pic",
  "jigsaw-snap", "spot-diff", "hidden-item", "maze-paint", "color-mix", "logic-box", "clue-grid",
  "alchemy-tree", "echo-seq", "clock-gears", "mate-in-one", "endgame-lab", "tsumego-go", "ring-puzzle",
  "knot-free", "color-chain", "block-fit", "pentomino", "gravity-flip", "clone-portal",
  "shadow-match", "hex-fill", "lock-combo", "escape-room", "treasure-grid", "number-path", "color-route",
  "magnet-poles", "water-dig", "map-fold", "paper-fold-cut", "weight-order", "balance-stack", "door-keys",
  "hamster-run", "pogo-street", "surf-wave", "ski-slalom", "bobsled-drift", "canoe-rapids", "relay-baton",
  "hoop-shot", "curling-sweep", "archery-move", "clay-shoot",
  "pinyin-tone", "radical-hunt", "stroke-fill", "word-link", "antonym-pair", "synonym-ladder",
  "proverb-fill", "poem-order", "typo-hunt", "sentence-fix", "pinyin-write", "measure-fill", "word-grid",
  "tone-tower", "glyph-pair", "couplet-pair", "xiehouyu", "twist-spot", "mad-lib", "stroke-count",
  "word-burst", "char-rain", "anagram-EN", "typing-race", "idiom-cross", "homophone", "word-hunt",
  "freecell", "pyramid-sol", "golf-solitaire", "clock-solitaire", "accordion-sol", "rummy-lite", "old-maid",
  "war-cards", "spit-race", "three-card-monte", "baccarat-lite", "dice-poker", "stop-bus", "dudo-bluff",
  "checkers", "hex-line", "breakthrough", "morris-line", "minishogi", "jungle-dark", "backgammon-lite",
  "ludo-solo", "snake-ladder", "mancala", "abalone-push",
  "amazon-queens", "ataxx-flood", "l-game", "quoridor-lite", "tsuro-path", "bingo-line", "poker-sol", "nim-coins",
  "bridge-build", "tower-crane", "dam-gates", "balloon-inflate", "pendulum-shot", "trebuchet", "seesaw-stack",
  "chain-blast", "kitchen-line", "signal-cross", "elevator-rush", "metro-dispatch", "tower-control", "stock-tap",
  "ant-trail", "bee-lines", "tank-eco", "bonsai-trim", "garden-waters", "laundry-day", "power-grid", "well-drill",
  "coaster-build", "pond-fish", "warehouse-rush",
  "futoshiki", "binary-taku", "magic-square", "prime-fact", "fraction-pie", "discount-math", "unit-quiz",
  "clock-math", "money-change", "abacus-flash", "sequence-next", "logic-owners", "syllogism-judge",
  "calendar-quiz", "dice-odds", "chem-balance", "speed-math", "number-maze", "tower-sum"
]);

// §2 基础 token + §3 已登记素材 token 前缀
const BASE_TOKENS = new Set([
  "--g-bg", "--g-surface", "--g-surface2", "--g-primary", "--g-primary-deep", "--g-primary-soft", "--g-on-soft",
  "--g-text", "--g-muted", "--g-faint", "--g-border", "--g-hairline", "--g-danger", "--g-warning", "--g-success",
  "--g-water", "--g-scrim", "--g-shadow", "--g-radius", "--g-radius-btn", "--g-radius-inner", "--g-gap", "--g-on-primary",
  "--g-btn-primary"
]);
const MATERIAL_PREFIX = /^--g-(bp|cb-|cz-|cbr-|ch-|bc-|bshade|bz-|field|grid|snake|food|sky|sky1|sky2|cloud|cld|gnd|gnl|pipe|pipecap|pipeln|bird1|bird2|birdln|wing|beak|bd|bd-|line|star|ball|ballln|maze|card|cell|cellhi|celllo|open|openln|n[1-8]|fs-|felt|felt2|rail|pocket|fr-|ss-|moon|moon2|board|tilesuper|st-|mt-|mzp-|ld-|low-|mp-|mm-|dpk-|pys-|ds-|mg-|fc-|gs-|gm-|hp-|bj-|am-|bl-|bel-|pn-|pnt-|pp-|jj-|su-|sb-|sbu-|sps-|acc-|rl-|sol-|mio-|el-|mnc-|msg-|xq-|ms-|ps-|lo-|nkb-|ng-|cd-|sk-|ls-|hf-|fut-|kro-|ws-|cw-|os-|hl-|sl-|np-|ah-|afb-|ast-|abp-|aq-|af-|at-|bgm-|hm-|ic-|id-|bs-|bt-|bm-|bn-|bb-|belt-|bal-|ck-|cr-|dc-|sm-|g2048-|llk-|m3-|ska-|poem-|icr-|pf-|pw-|pt-|pg-|rp-|rf-|th-|sf-|xky-|rdl-|qr-|c4-|mor-|rv-|kt-|lg-|sdk-|cs-|db-|dudo-|fi-|flc-|fuse-|lnd-|lr-|ll-|hj-|jd-|kd-|kl-|kf-|g24-|gmesh-|hw-|pot-|pac-|pfc-|peg-|mc-|nb-|mf-|mr-|mst-|scl-|sc-|ssl-|rc-|rh-|rcl-|rpic-|rm-|stc-|tet-|tb-|tc-|td-|tg-|wo-|wd-|trb-|tsp-|vd-|wr-|wj-)/;
// 说明：bs- 覆盖 bell-strike 与 bubble-shooter 的素材 token（bubble-shooter 2026-11-27 复用登记）
//      bal- 覆盖 balance-stack 的素材 token（2026-11-27 渲染层重做登记）
//      cr- 覆盖 color-route 的素材 token（2026-11-27 渲染层重做登记）
//      dc- 覆盖 domino-chain 的素材 token（2026-11-27 渲染层重做登记）
//      g2048- 覆盖 g2048 的素材 token（2026-11-27 渲染层重做登记）
//      ska- 覆盖 suika 的素材 token（2026-11-27 渲染层重做登记）
//      llk- 覆盖 lianliankan 的素材 token（lianliankan 2026-11-27 渲染层重做登记）
//      m3- 覆盖 match3 的素材 token（match3 2026-11-27 渲染层重做登记）
//      sm- 覆盖 stack-match 的素材 token（stack-match 2026-11-27 渲染层重做登记）
//      pnt- 覆盖 pentomino 的素材 token（pentomino 2026-11-27 渲染层重做登记）
//      pf- 覆盖 poem-fill 的素材 token（poem-fill 2026-11-27 渲染层重做登记）
//      poem- 覆盖 poem-order 的素材 token（poem-order 2026-11-27 渲染层重做登记）
//      ch- 覆盖 char-rain 的素材 token（char-rain 2026-11-27 渲染层重做登记）
//      ic- 覆盖 idiom-chain 的素材 token（idiom-chain 2026-11-27 渲染层重做登记）
//      icr- 覆盖 idiom-cross 的素材 token（idiom-cross 2026-11-27 渲染层重做登记）
//      id- 覆盖 idiom 的素材 token（idiom 2026-11-27 渲染层重做登记）
//      hm- 覆盖 hangman 的素材 token（hangman 2026-11-27 渲染层重做登记）
//      pw- 覆盖 pinyin-write 的素材 token（pinyin-write 2026-11-27 渲染层重做登记）
//      pt- 覆盖 pinyin-tone 的素材 token（pinyin-tone 2026-11-27 渲染层重做登记）
//      th- 覆盖 typo-hunt 的素材 token（typo-hunt 2026-11-27 渲染层重做登记）
//      sf- 覆盖 sentence-fix 的素材 token（sentence-fix 2026-11-27 渲染层重做登记）
//      xky- 覆盖 xiehouyu 的素材 token（xiehouyu 2026-11-27 渲染层重做登记）
//      rdl- 覆盖 riddle 的素材 token（riddle 2026-11-27 渲染层重做登记）
//      msg- 覆盖 minishogi 的素材 token（minishogi 2026-11-27 渲染层重做登记）
//      ms-  覆盖 mahjong-solitaire 与 minesweeper 的素材 token（2026-11-27 渲染层重做登记）
//      c4-  覆盖 connect-four 的素材 token（connect-four 2026-11-27 渲染层重做登记）
//      qr- 覆盖 quoridor-lite 的素材 token（quoridor-lite 2026-11-27 渲染层重做登记）
//      mor- 覆盖 morris-line 的素材 token（morris-line 2026-11-27 渲染层重做登记）
//      ck-  覆盖 checkers 的素材 token（checkers 2026-11-27 渲染层重做登记）
//      ls- 覆盖 ludo-solo 的素材 token（ludo-solo 2026-11-27 渲染层重做登记）
//      lg- 覆盖 l-game 的素材 token（l-game 2026-11-27 渲染层重做登记）
//      sk-  覆盖 sokoban 的素材 token（sokoban 2026-11-27 渲染层重做登记；前缀原已在白名单，本次补注释）
//      sdk- 覆盖 sudoku 的素材 token（sudoku 2026-11-27 渲染层重做登记）
//      st-  覆盖 tsumego-go 的素材 token（tsumego-go 2026-11-27 渲染层重做复用登记，st- 原属本游戏）
//      cz-  覆盖 chai-zi 的素材 token（chai-zi 2026-11-27 渲染层重做登记）
//      cbr- 覆盖 coaster-build 的素材 token（coaster-build 2026-11-27 渲染层重做登记）
//      cb-  覆盖 chem-balance 的素材 token（chem-balance 2026-11-27 渲染层重做登记）
//      fi- 覆盖 fishing 的素材 token（fishing 2026-11-27 渲染层立体化重做登记）
//      flc- 覆盖 flow-connect 的素材 token（flow-connect 2026-11-27 渲染层重做登记）
//      fuse- 覆盖 fuse-cut 的素材 token（fuse-cut 2026-11-27 渲染层重做登记）
//      bt-  覆盖 binary-taku 的素材 token（binary-taku 2026-11-27 渲染层重做登记；前缀原属 beat-tap，本次复用）
//      bm-  覆盖 bomberman 的素材 token（bomberman 2026-11-27 渲染层重做登记）
//      bn-  覆盖 bonsai-trim 的素材 token（bonsai-trim 2026-11-27 渲染层重做登记）
//      ld-  覆盖 laundry-day 的素材 token（laundry-day 2026-11-27 渲染层立体化重做登记）
//      low- 覆盖 logic-owners 的素材 token（logic-owners 2026-11-27 渲染层重做登记）
//      mp-  覆盖 magnet-poles 的素材 token（magnet-poles 2026-11-27 渲染层重做登记；原 st- 前缀收敛）
//      g24- 覆盖 game24 的素材 token（game24 2026-11-27 渲染层重做登记）
//      gmesh- 覆盖 gear-mesh 的素材 token（gear-mesh 2026-11-27 渲染层重做登记）
//      hw-  覆盖 hashi-wire 的素材 token（hashi-wire 2026-11-27 渲染层重做登记；原 st- 收敛为 hw-）
//      hj-  覆盖 helix-jump 的素材 token（helix-jump 2026-11-27 渲染层重做登记；前缀原复用 st-，本次收敛为游戏前缀）
//      jd-  覆盖 jungle-dark 的素材 token（jungle-dark 2026-11-27 渲染层重做登记）
//      kd-  覆盖 kite-duel 的素材 token（kite-duel 2026-11-27 渲染层重做登记）
//      kl-  覆盖 klotski 的素材 token（klotski 2026-11-27 渲染层重做登记）
//      kf-  覆盖 knot-free 的素材 token（knot-free 2026-11-27 渲染层重做登记）
//      lnd- 覆盖 lane-defense 的素材 token（lane-defense 2026-11-27 渲染层重做登记；原 st- 收敛为游戏前缀）
//      lr-  覆盖 lantern-rise 的素材 token（lantern-rise 2026-11-27 渲染层重做登记；原 st- 收敛为游戏前缀）
//      ll-  覆盖 latte-line 的素材 token（latte-line 2026-11-27 渲染层重做登记；原 st- 收敛为游戏前缀）
//      pg-  覆盖 power-grid 的素材 token（power-grid 2026-11-27 渲染层重做登记）
//      rp-  覆盖 ring-puzzle 的素材 token（ring-puzzle 2026-11-27 渲染层重做登记；原 st- 收敛为游戏前缀）
//      rf-  覆盖 ripple-fill 的素材 token（ripple-fill 2026-11-27 渲染层重做登记）
//      pac- 覆盖 pacman 的素材 token（pacman 2026-11-28 渲染层重做登记）
//      pfc- 覆盖 paper-fold-cut 的素材 token（paper-fold-cut 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      peg- 覆盖 peg-solitaire 的素材 token（peg-solitaire 2026-11-28 渲染层重做登记）
//      mc-  覆盖 money-change 的素材 token（money-change 2026-11-28 渲染层重做登记）
//      nb-  覆盖 nut-bolt 的素材 token（nut-bolt 2026-11-28 渲染层重做登记）
//      mf-  覆盖 map-fold 的素材 token（map-fold 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      mr-  覆盖 marble-run 的素材 token（marble-run 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      mst- 覆盖 matchstick 的素材 token（matchstick 2026-11-28 渲染层重做登记）
//      scl- 覆盖 scale-ball 的素材 token（scale-ball 2026-11-28 渲染层重做登记）
//      sc-  覆盖 signal-cross 的素材 token（signal-cross 2026-11-28 渲染层重做登记）
//      ssl- 覆盖 ski-slalom 的素材 token（ski-slalom 2026-11-28 渲染层重做登记）
//      rc-  覆盖 rope-cut 的素材 token（rope-cut 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      rpic- 覆盖 rotate-pic 的素材 token（rotate-pic 2026-11-28 渲染层重做登记）
//      rm-  覆盖 rubik-mini 的素材 token（rubik-mini 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      rc-  覆盖 river-cross 的素材 token（river-cross 2026-11-28 渲染层重做登记）
//      rh-  覆盖 road-hop 的素材 token（road-hop 2026-11-28 渲染层重做登记）
//      rcl- 覆盖 rock-climb 的素材 token（rock-climb 2026-11-28 渲染层重做登记）
//      stc- 覆盖 steady-chop 的素材 token（steady-chop 2026-11-28 渲染层重做登记）
//      tet- 覆盖 tetris 的素材 token（tetris 2026-11-28 渲染层重做登记）
//      tb-  覆盖 torch-bridge 的素材 token（torch-bridge 2026-11-28 渲染层重做登记；原 st- 收敛）
//      tc-  覆盖 tower-control 的素材 token（tower-control 2026-11-28 渲染层重做登记；原 st-tc- 收敛为游戏前缀）
//      td-  覆盖 tower-defense 的素材 token（tower-defense 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      tg-  覆盖 treasure-grid 的素材 token（treasure-grid 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      trb- 覆盖 trebuchet 的素材 token（trebuchet 2026-11-28 渲染层重做登记；原 st-tb- 收敛为游戏前缀）
//      tsp- 覆盖 tsuro-path 的素材 token（tsuro-path 2026-11-28 渲染层重做登记）
//      vd-  覆盖 virus-defense 的素材 token（virus-defense 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      wo-  覆盖 weight-order 的素材 token（weight-order 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      wd-  覆盖 well-drill 的素材 token（well-drill 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      wr-  覆盖 warehouse-rush 的素材 token（warehouse-rush 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
//      wj-  覆盖 water-jug 的素材 token（water-jug 2026-11-28 渲染层重做登记；原 st- 收敛为游戏前缀）
const FONT_TIER = new Set([36, 28, 22, 18, 16, 14, 12]);
const SHELL_RADIUS = new Set([16, 14, 12, 10]);

// §2 基线值（浅/深；radius 系为常量）。用于 ⑯ token 基线值比对。
const BASELINE_LIGHT = {
  "--g-bg": "#f5f6f3", "--g-surface": "#ffffff", "--g-surface2": "#eff1ee", "--g-primary": "#2d9b6a",
  "--g-primary-deep": "#17734a", "--g-primary-soft": "#e8f6ef", "--g-on-soft": "#0b3625",
  "--g-text": "#1c1c1e", "--g-muted": "#6b6e73", "--g-faint": "#a9aea9", "--g-border": "#e6e9e6",
  "--g-hairline": "#eff1ee", "--g-danger": "#a83850", "--g-warning": "#9a6210", "--g-success": "#17734a",
  "--g-water": "#4a9fd4", "--g-scrim": "rgba(245,246,243,0.9)",
  "--g-radius": "16px", "--g-radius-btn": "14px", "--g-radius-inner": "12px", "--g-gap": "16px", "--g-on-primary": "#ffffff",
  "--g-btn-primary": "#17734a"
};
const BASELINE_DARK = {
  "--g-bg": "#141614", "--g-surface": "#1d201e", "--g-surface2": "#262a27", "--g-primary": "#2d9b6a",
  "--g-primary-deep": "#5bc894", "--g-primary-soft": "#1d3229", "--g-on-soft": "#b9e8cf",
  "--g-text": "#e6e9e6", "--g-muted": "#a9aea9", "--g-faint": "#7e837d", "--g-border": "#2c312d",
  "--g-hairline": "#262a27", "--g-danger": "#db7186", "--g-warning": "#e2a44e", "--g-success": "#4ade6e",
  "--g-water": "#6bb4de", "--g-scrim": "rgba(20,22,20,0.9)"
};
const SHADOW_OK = [
  "02px12pxrgba(20,40,30,0.05)", "02px12pxrgba(0,0,0,0.28)",          // 卡片档
  "010px24pxrgba(20,40,30,0.1)", "010px24pxrgba(0,0,0,0.45)"           // 浮层档（gomoku §3 登记）
];

// canvas 字号例外（§3 灰区：素材字号受素材尺寸硬约束，逐值登记）
const CANVAS_FONT_EXEMPT = {
  billiards: [9, 17], "moon-lander": [10, 13], beat_tap: [11, 13, 15, 34, 42],
  "forest-run": [15], slingshot: [15], flappy: [38], breakout: [15]
};

function norm(s) { return s.replace(/\s+/g, ""); }
function themeRuntime(html) {
  const m = html.match(/<script>\s*(\/\/主题运行时[\s\S]*?)<\/script>/);
  return m ? m[1] : null;
}
// 严格比对前剥离 setChrome 行（全屏游戏按 §6#6 在运行时内追加）与 __gRM 注入行
// （2026-11-14 动效层注入，与主题运行时功能等价、不影响语义；2026-11-15 口径修正）
function stripSetChrome(rt) { return rt ? rt.replace(/\n?\s*try\{SmartWashGameBridge&&SmartWashGameBridge\.setChrome\([^)]*\)\}catch\(e\)\{\}/g, "").replace(/\n?\s*window\.__gRM=\(function\(\)\{[\s\S]*?\}catch\(e\)\{\};/g, "") : rt; }
const canonRuntime = themeRuntime(CANONICAL);

// 颜色归一化：#abc→#aabbcc、#RRGGBBAA→rgba(...)、rgba 逗号归一、alpha 两位小数
function normColor(v) {
  v = String(v).trim().toLowerCase();
  let m;
  if ((m = v.match(/^#([0-9a-f]{3})$/))) return "#" + [...m[1]].map((c) => c + c).join("");
  if ((m = v.match(/^#([0-9a-f]{6})$/))) return "#" + m[1];
  if ((m = v.match(/^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/))) {
    const a = Math.round(parseInt(m[4], 16) / 2.55) / 100;
    return `rgba(${parseInt(m[1], 16)},${parseInt(m[2], 16)},${parseInt(m[3], 16)},${a})`;
  }
  if ((m = v.match(/^rgba?\(([^)]+)\)$/))) {
    const p = m[1].split(",").map((s) => s.trim());
    if (p.length === 4) return `rgba(${p[0]},${p[1]},${p[2]},${Math.round(parseFloat(p[3]) * 100) / 100})`;
    return `rgba(${p.slice(0, 3).join(",")},1)`;
  }
  return v;
}
function normShadow(v) { return normColor(String(v).replace(/\s+/g, " ").trim()).replace(/\s+/g, ""); }

// 解析 :root 浅色 与 [data-theme=dark] 深色两块的 token 值
function themeBlocks(html) {
  const css = html.replace(/\/\*[\s\S]*?\*\//g, "");
  const out = { light: {}, dark: {} };
  for (const m of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const sel = m[1].trim(), body = m[2];
    if (!/^(?:html\s*,\s*body|:root|\[data-theme)/.test(sel)) continue;
    if (/data-theme\s*=\s*["']?dark/.test(sel)) {
      for (const d of body.matchAll(/(--g-[a-z0-9-]+)\s*:\s*([^;]+);/g)) out.dark[d[1]] = d[2].trim();
    } else if (/^:root\b/.test(sel)) {
      for (const d of body.matchAll(/(--g-[a-z0-9-]+)\s*:\s*([^;]+);/g)) out.light[d[1]] = d[2].trim();
    }
  }
  return out;
}

// 布局类型：card（有 .wrap）/ fullscreen（html,body height:100%）/ unknown
function layoutType(html) {
  if (/\.wrap\s*\{/.test(html)) return "card";
  if (/html\s*,\s*body\s*\{[^}]*height:\s*100%/.test(html)) return "fullscreen";
  return "unknown";
}

// 存档键全量提取：常量表解析 + 直接调用 + 封装函数调用点（去注释后扫描最后一段 script）
function extractStorageKeys(html) {
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  const code = (scripts.length ? scripts[scripts.length - 1] : "")
    .replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
  const consts = {};
  for (const m of code.matchAll(/(?:var|let|const)\s+([^;]+?);/g)) {
    for (const d of m[1].split(",")) {
      const dm = d.match(/([A-Za-z_$][\w$]*)\s*=\s*(["'])((?:(?!\2).)*)\2/);
      if (dm) consts[dm[1]] = dm[3];
    }
  }
  const keys = new Set(), dynamic = new Set();
  const addArg = (arg) => {
    arg = arg.trim();
    const lit = arg.match(/^(["'])((?:(?!\1).)*)\1$/);
    if (lit) { keys.add(lit[2]); return; }
    if (consts[arg]) { keys.add(consts[arg]); return; }
    if (/^[A-Za-z_$][\w$]*$/.test(arg)) return; // 形参/标识符，非字面量
    dynamic.add(arg);
  };
  for (const m of code.matchAll(/localStorage\s*\.\s*(?:getItem|setItem|removeItem)\s*\(\s*([^;]+?)\s*(?:,|\))/g)) addArg(m[1]);
  // 封装函数：函数体引用 localStorage 且实参进入其调用 → 解析该函数的调用点
  //（函数体可能含 try/catch 嵌套花括号，故用签名后 400 字符窗口而非闭括号匹配）
  const wrappers = new Set();
  const ID_RE = /^[A-Za-z_$][\w$]*$/;
  const scanFn = (name, params, body) => {
    if (!/localStorage/.test(body) || !ID_RE.test(name)) return;
    for (const p of params) {
      if (!ID_RE.test(p)) continue;
      const re = new RegExp("localStorage\\s*\\.\\s*(?:getItem|setItem|removeItem)\\s*\\([^)]*\\b" + p + "\\b[^)]*\\)");
      if (re.test(body)) { wrappers.add(name); return; }
    }
  };
  for (const m of code.matchAll(/function\s+([A-Za-z_$][\w$]*)\s*\(([^)]*)\)/g)) scanFn(m[1], m[2].split(",").map((s) => s.trim()), code.slice(m.index + m[0].length, m.index + m[0].length + 400));
  for (const m of code.matchAll(/([A-Za-z_$][\w$]*)\s*:\s*function\s*\(([^)]*)\)/g)) scanFn(m[1], m[2].split(",").map((s) => s.trim()), code.slice(m.index + m[0].length, m.index + m[0].length + 400));
  // 对象方法简写 get(key){...}（排除 if/for/while/switch/catch 控制流）
  for (const m of code.matchAll(/(?!(?:if|for|while|switch|catch|function|return)\b)([A-Za-z_$][\w$]*)\s*\(([^)]*)\)\s*\{/g)) scanFn(m[1], m[2].split(",").map((s) => s.trim()), code.slice(m.index + m[0].length, m.index + m[0].length + 400));
  for (const name of wrappers) {
    for (const m of code.matchAll(new RegExp("\\b" + name + "\\s*\\(\\s*([^)]+)\\)", "g"))) {
      // 键是首参；调用可能带多参（read("key",0)），截取首参（引号串或标识符）
      const arg0 = m[1].match(/^\s*(["'][^"']*["']|[A-Za-z_$][\w$]*)/);
      if (arg0) addArg(arg0[1]);
    }
  }
  return { keys: [...keys], dynamic: [...dynamic] };
}

// JS 颜色字面量扫描（最后一个 script 块），按行豁免：行尾注释含例外/保留/恒白/恒暗/恒亮/素材
function scanJsColors(html) {
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  const code = scripts.length ? scripts[scripts.length - 1] : "";
  const bad = [];
  for (const line of code.split("\n")) {
    if (!/(#[0-9a-fA-F]{3,8}\b|rgba?\()/.test(line)) continue;
    if (/(?:例外|保留|恒白|恒暗|恒亮|素材)/.test(line)) continue;
    const probe = line.replace(/\/\/.*$/, "");
    if (/rgba?\(\s*255\s*,\s*255\s*,\s*255/.test(probe)) continue; // 恒白
    if (/rgba?\(\s*0\s*,\s*0\s*,\s*0/.test(probe)) continue;       // 恒黑阴影
    if (/#[fF]{3}\b|#[fF]{6}\b/.test(probe)) continue;             // 恒白 hex
    if (/rgba?\(\s*["']?\s*\+/.test(probe)) continue;              // 动态构造（非字面色）
    bad.push(line.trim().slice(0, 90));
  }
  return bad;
}

function audit(name) {
  const html = readFileSync(join(DIR, name, "index.html"), "utf8");
  const strict = STRICT.has(name);
  const fail = [];   // 违规（新游戏必须清零）
  const notes = [];  // 信息项（存量遗留偏离 / 严格模式的提示）
  const info = {};
  const P = (msg) => (strict ? fail : notes).push(msg);

  // ① 不自绘标题（全部硬性）
  const h1 = (html.match(/<h1[\s>]/g) || []).length;
  info.h1 = h1;
  if (h1 > 0) fail.push(`存在 ${h1} 个 <h1>`);

  // ② 主题运行时（新游戏归一化对齐范例；存量功能等价即可）
  const rt = themeRuntime(html);
  const functional = /__applySmartWashTheme=function\(m\)\{if\(m==="light"\|\|m==="dark"\)/.test(html) &&
    /getTheme/.test(html) && /dataset\.theme/.test(html);
  if (strict) {
    if (!rt) fail.push("主题运行时缺失");
    else if (norm(stripSetChrome(rt)) !== norm(stripSetChrome(canonRuntime))) fail.push("主题运行时与 block-drop 范例不一致");
    info.runtime = rt ? "一致" : "缺失";
  } else {
    if (!functional) fail.push("主题运行时功能要素缺失（getTheme/apply/theme 数据集）");
    else info.runtime = rt ? "变体(功能等价)" : "无注释变体(功能等价)";
  }

  // ③ meta theme-color 存在 + 浅色默认值 + paint() 同步（全部硬性）
  const metaM = html.match(/<meta\s+name="theme-color"\s+content="([^"]*)"/);
  if (!metaM) fail.push("缺 meta[name=theme-color]");
  else if (normColor(metaM[1]) !== normColor("#F5F6F3")) P(`theme-color 初值非浅色 bg: ${metaM[1]}`);
  if (metaM && !/meta\[name=["']?theme-color["']?\]/.test(html)) P("paint() 未同步 meta[name=theme-color]");

  // ④ setChrome 与布局类型匹配（§5.1：卡片式 0 处；全屏画布须 immersive 且置于 head 运行时内）
  const chrome = (html.match(/setChrome/g) || []).length;
  const layout = layoutType(html);
  info.setChrome = chrome; info.layout = layout;
  if (layout === "card" && chrome > 0) fail.push(`卡片式布局出现 ${chrome} 处 setChrome`);
  if (layout === "fullscreen") {
    if (chrome === 0) P("全屏画布未调 setChrome('immersive')");
    else if (rt && !/setChrome/.test(rt)) P("setChrome 未置于 head 主题运行时内");
    else if (rt && !/setChrome\(['"]immersive/.test(rt)) P("setChrome 非 immersive");
  }

  // ⑤ CSS 字号八档（新游戏硬性；存量信息项）
  const fontSizes = new Set();
  for (const m of html.matchAll(/font-size:\s*(\d+(?:\.\d+)?)px/g)) fontSizes.add(+m[1]);
  for (const m of html.matchAll(/font:\s*(?:inherit|\d+\s+)(\d+(?:\.\d+)?)px/g)) fontSizes.add(+m[1]);
  const badFonts = [...fontSizes].filter((s) => !FONT_TIER.has(s));
  info.fonts = [...fontSizes].sort((a, b) => a - b).join("/");
  if (badFonts.length) P(`字号越档: ${badFonts.join(",")}`);

  // ⑥ 圆角（UI 壳 ∈16/14/12/10；>10 越档新游戏硬性；小圆角/999 为素材或胶囊例外）
  const radii = new Set();
  for (const m of html.matchAll(/border-radius:\s*([^;}]+)/g)) {
    for (const v of m[1].matchAll(/(\d+(?:\.\d+)?)px/g)) radii.add(+v[1]);
    if (/%/.test(m[1])) radii.add("%");
  }
  info.radii = [...radii].join("/");
  const badShell = [...radii].filter((r) => r !== "%" && !SHELL_RADIUS.has(r) && r > 10 && r !== 999);
  if (badShell.length) P(`UI 壳疑似越档圆角: ${badShell.join(",")}`);

  // ⑦ token（未定义引用全部硬性；自造 token 新游戏硬性）
  const defined = new Set();
  for (const m of html.matchAll(/(--g-[a-z0-9-]*[a-z0-9])\s*:/g)) defined.add(m[1]);
  const used = new Set();
  for (const m of html.matchAll(/var\((--g-[a-z0-9-]*[a-z0-9])/g)) used.add(m[1]);
  const undef = [...used].filter((t) => !defined.has(t));
  const dynamicTok = undef.filter((t) => [...defined].some((d) => d.startsWith(t + "-")));
  const trulyUndef = undef.filter((t) => !dynamicTok.includes(t));
  info.tokens = used.size;
  if (trulyUndef.length) fail.push(`使用了未定义的 token: ${trulyUndef.join(",")}`);
  if (dynamicTok.length) notes.push(`动态拼接 token（运行时解析）: ${dynamicTok.join(",")}`);
  const nonBase = [...defined].filter((t) => !BASE_TOKENS.has(t) && !MATERIAL_PREFIX.test(t));
  if (nonBase.length) P(`疑似自造 token（不在 §2/§3）: ${nonBase.join(",")}`);

  // ⑧ 桥钩子 + canvas 调色板（全部硬性）
  if (!/__applySmartWashTheme/.test(html)) fail.push("缺 __applySmartWashTheme");
  if (/getContext\("2d"\)/.test(html)) {
    info.canvas = true;
    if (!/palLoad/.test(html)) fail.push("canvas 游戏缺 palLoad 调色板刷新");
    if (!/window\.__gOnTheme\s*=\s*(?:function\(\)\s*\{\s*palLoad|\(\)\s*=>\s*\{\s*palLoad)/.test(html))
      fail.push("__gOnTheme 未接 palLoad");
  } else info.canvas = false;

  // ⑨ body 触屏五件套（新游戏硬性；存量缺项信息）
  const bodyCss = [...html.matchAll(/(?:^|\})\s*body\s*\{([^}]*)\}/gm)].map((m) => m[1]).join("\n");
  const missing = ["user-select", "-webkit-touch-callout", "-webkit-tap-highlight-color", "touch-action", "overscroll-behavior"]
    .filter((r) => !bodyCss.includes(r));
  if (missing.length) P(`body 缺触屏规则: ${missing.join(",")}`);

  // ⑩ 布局（按类型判定：卡片查 480/padding；全屏查 height:100% + overflow:hidden）
  if (layout === "card") {
    if (!/max-width:\s*480px/.test(html)) P("缺 .wrap max-width:480px");
    if (!/padding:\s*16px 8px 30px/.test(html)) P("body padding 非 16px 8px 30px");
  } else if (layout === "fullscreen") {
    const hb = [...html.matchAll(/(?:^|\})\s*(?:html\s*,\s*body|body)\s*\{([^}]*)\}/gm)].map((m) => m[1]).join(";");
    if (!/height:\s*100%/.test(hb)) P("全屏缺 html,body height:100%");
    if (!/overflow:\s*hidden/.test(hb)) P("全屏缺 overflow:hidden（flex 形态豁免见 §5.1 待裁定）");
  } else P("布局类型未知（无 .wrap 且无全屏特征）");

  // ⑪ 热区（交互类选择器 ≥44px；compound 感知：.btn.warn 取 .btn；排除玩法素材 .card/.cell）
  const INTERACTIVE_PART = /(?:^|\.)(btn|ctl|key|lamp|lvbtn|pad|song|pbtn|icon-btn|ctrl-btn|lv|lv-chip)(?:$|[:.\[])/i;
  const small = [];
  for (const m of html.matchAll(/([^{}]+)\{([^}]*)\}/g)) {
    const sel = m[1].replace(/\/\*[\s\S]*?\*\//g, "").trim(), body = m[2];
    const last = sel.split(/\s+/).pop().toLowerCase();
    if (last.includes("::")) continue;   // ::before/::after 是装饰伪元素，不是可点控件
    const isBtn = last === "button" || INTERACTIVE_PART.test(last);
    if (!isBtn || /\.card\b|\.cell\b/.test(last)) continue;
    const mh = body.match(/min-height:\s*(\d+(?:\.\d+)?)px/);
    if (mh && +mh[1] < 44) small.push(sel + ":" + mh[1]);
    const h = body.match(/(?<!min-)height:\s*(\d+(?:\.\d+)?)px/);
    if (h && !mh && +h[1] < 44) small.push(sel + ":" + h[1]);
  }
  info.hotzone = small.length ? small.join(" ") : "按钮全 ≥44";
  if (small.length) fail.push(`按钮热区 <44px: ${small.join(" ")}`);

  // ⑫ 存档键（全量提取：新游戏全部键须 sw.<key>.v1；存量为 §6 豁免信息项）
  const extracted = extractStorageKeys(html);
  const swKey = html.match(/sw\.([a-z0-9-]+)\.v1/);
  // 存档键须以 sw.<key>.v1 为命名空间锚点，允许其下挂子键（如关卡缓存 sw.<key>.v1.levels），
  // 避免另起一套键名脱离游戏标识（2026-11-09 游戏数据平台化引入关卡缓存键时放宽）
  const badKeys = extracted.keys.filter((k) => !/^sw\.[a-z0-9-]+\.v1(\.[a-z0-9-]+)*$/.test(k));
  info.saveKey = extracted.keys.length
    ? (extracted.keys.length === 1 ? extracted.keys[0] : `${extracted.keys.length}键`) + (badKeys.length ? `（${badKeys.length} 遗留）` : "")
    : "无存档";
  if (extracted.keys.length === 0 && !swKey) {
    if (strict) fail.push("未找到存档键");
  } else if (strict) {
    if (!swKey) fail.push("存档键不符 sw.<key>.v1");
    if (badKeys.length) fail.push(`存档键不符规范: ${badKeys.join(", ")}`);
  } else if (badKeys.length) {
    notes.push(`遗留存档键: ${badKeys.join(" ")}`);
  }
  if (extracted.dynamic.length) notes.push(`动态拼键: ${extracted.dynamic.join(" ")}`);

  // ⑬ 零外链（全部硬性）
  if (/https?:\/\//.test(html)) fail.push("存在 http(s) 外链");

  // ⑬a canvas 闭合配对（全部硬性）：DOM 解析器不自动闭合 <canvas>，未闭合会把后续节点吞成 fallback
  const cvOpen = (html.match(/<canvas[\s>]/g) || []).length;
  const cvClose = (html.match(/<\/canvas>/g) || []).length;
  if (cvOpen !== cvClose) fail.push(`<canvas> 未闭合（open ${cvOpen} / close ${cvClose}）`);

  // ⑭ §4 裁撤残留（matchMedia/prefers-color-scheme/g-theme/切换按钮；全部硬性）
  // ⑭ §4 裁撤残留（全部硬性）
  // 2026-11-14 精确化：matchMedia 禁令仅限**主题层**（prefers-color-scheme）；
  // prefers-reduced-motion 属动效层（§6.1 降级），允许使用，不再误报。
  const residue = [];
  if (/prefers-color-scheme/.test(html)) residue.push("prefers-color-scheme");
  if (/matchMedia\(/.test(html.replace(/matchMedia\(\s*["']?\(prefers-reduced-motion[^)]*\)/g, ""))) {
    residue.push("matchMedia(非 reduced-motion)");
  }
  if (/["']g-theme["']/.test(html)) residue.push("g-theme localStorage");
  if (/__gThemeToggle|g-theme-toggle|btnTheme/.test(html)) residue.push("切换按钮");
  if (residue.length) fail.push(`§4 裁撤残留: ${residue.join(",")}`);

  // ⑮ token 基线值比对（§2 已定义 token 的值须与基线一致；素材 token 不比对）
  const blocks = themeBlocks(html);
  const drift = [];
  for (const [mode, table] of [["light", BASELINE_LIGHT], ["dark", BASELINE_DARK]]) {
    for (const [tok, base] of Object.entries(table)) {
      const val = blocks[mode][tok];
      if (val === undefined) continue; // 未定义不在此查（深色块只写与浅色不同的 token）
      if (tok === "--g-shadow") {
        if (!SHADOW_OK.includes(normShadow(val))) drift.push(`${mode} ${tok}=${val}`);
      } else if (normColor(val) !== normColor(base)) {
        drift.push(`${mode} ${tok}=${val}`);
      }
    }
  }
  if (drift.length) P(`token 基线偏离: ${drift.slice(0, 6).join("; ")}${drift.length > 6 ? " 等" + drift.length + "项" : ""}`);

  // ⑯ canvas 字号灰区（ctx.font 单列：越档且未登记例外的记信息项）
  if (info.canvas) {
    const cvFonts = new Set();
    for (const m of html.matchAll(/\.font\s*=\s*([^;\n]+)/g)) {
      const rhs = m[1];
      for (const pm of rhs.matchAll(/(\d+(?:\.\d+)?)px/g)) cvFonts.add(+pm[1]);
      if (rhs.includes("+") || rhs.includes("?")) {
        // 动态拼接/三元：抓独立两位整数（10–59，天然排除 400/600/800 字重）
        for (const pm of rhs.matchAll(/\b(\d{2})\b/g)) {
          const n = +pm[1];
          if (n >= 10 && n <= 59) cvFonts.add(n);
        }
      }
    }
    const exempt = CANVAS_FONT_EXEMPT[name.replace(/-/g, "_")] || CANVAS_FONT_EXEMPT[name] || [];
    const badCv = [...cvFonts].filter((s) => !FONT_TIER.has(s) && !exempt.includes(s));
    info.canvasFonts = [...cvFonts].sort((a, b) => a - b).join("/") || "-";
    if (badCv.length) notes.push(`canvas 字号越档: ${badCv.join(",")}`);
  }

  // ⑰ JS 颜色字面量（§3：零字面量，恒白/恒暗/行尾例外注释豁免；新游戏硬性）
  const badColors = scanJsColors(html);
  if (badColors.length) P(`JS 颜色字面量: ${badColors.length} 处（首处: ${badColors[0]}）`);

  return { name, strict, info, fail, notes };
}

const targets = process.argv.slice(2);
// 仅把「含 index.html 的目录」当游戏审计；跳过 covers-src 等资源目录（非游戏）
const games = targets.length ? targets : readdirSync(DIR).filter((d) =>
  !d.startsWith(".") && !d.endsWith(".md") && statSync(join(DIR, d)).isDirectory() && existsSync(join(DIR, d, "index.html")));

let bad = 0, strictBad = 0;
for (const g of games.sort()) {
  const r = audit(g);
  if (r.fail.length) { bad++; if (r.strict) strictBad++; }
  console.log(`${r.fail.length ? "❌" : "✅"} ${g.padEnd(14)} [${r.strict ? "严格" : "存量"}] 运行时:${r.info.runtime} 布局:${r.info.layout || "?"} h1:${r.info.h1} setChrome:${r.info.setChrome} 字号:${r.info.fonts || "-"}${r.info.canvasFonts ? "(cv:" + r.info.canvasFonts + ")" : ""} 圆角:${r.info.radii} 热区:${r.info.hotzone} 存档:${r.info.saveKey}`);
  r.fail.forEach((p) => console.log("     ❌ " + p));
  r.notes.forEach((p) => console.log("     · " + p));
}
console.log(`\n审计 ${games.length} 款：严格模式 ${strictBad} 款违规；存量模式另有 ${bad - strictBad} 款违规（遗留项见信息行）`);
process.exit(bad ? 1 : 0);
