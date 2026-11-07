// src/views/game/gameDirectory.js
// games-library 游戏目录规范共享逻辑：目录结构校验 + README 解析。
// 单目录导入（GameList）与父目录批量导入（BatchUploadDialog）共用同一套规范，避免两处漂移。
// 规范：游戏目录根层只允许 index.html（必须入口）+ README.md（可选元数据）+ 封面图（可选），
// 子目录与其余文件一律不合规（多文件游戏请打包 .zip 走手动上传）。
import { GAME_CATEGORY_OPTIONS } from '@/constants/dict';

/** gameKey 规则：小写字母开头，3-64 位小写字母/数字/下划线/中划线（与后端 UploadGameFrom 校验对齐） */
export const GAME_KEY_PATTERN = /^[a-z][a-z0-9_-]{2,63}$/;

// 同一规则剥掉 ^/$ 后供「从文本中间提取」使用：GAME_KEY_PATTERN 的锚点是为整串校验设计的，
// README 首行里 gameKey 前后还有其他字符，需要无锚点版本
export const GAME_KEY_SOURCE = GAME_KEY_PATTERN.source.replace(/^\^/, '').replace(/\$$/, '');

/** 目录规范提示文案（错误信息尾部统一拼接） */
export const GAME_DIR_RULES =
  '目录仅支持「index.html（+ 可选 README.md / 封面图 png/jpg/jpeg/webp）」，含其他文件请打包 .zip 手动上传';

/** 封面图扩展名（命名兼容 cover.png 与 games-library 规范 <game-key>.png） */
const COVER_EXTS = ['.png', '.jpg', '.jpeg', '.webp'];

/** 文件相对路径段（webkitdirectory 选择时浏览器提供 webkitRelativePath） */
export const relSegs = (file) => (file.webkitRelativePath || file.name).split('/');

/**
 * 批量导入：按游戏目录分组父目录下的文件。
 * @param {File[]} files 选择父目录得到的全部文件（webkitRelativePath = 父目录/游戏目录/…）
 * @returns {{dirName: string, files: File[]}[]} 每个游戏目录一组；隐藏目录整组忽略
 */
export function splitGameDirs(files) {
  const groups = new Map();
  for (const file of files) {
    const segs = relSegs(file);
    // 至少「父目录/游戏目录/文件」三层才构成游戏目录条目
    if (segs.length < 3) continue;
    const dirName = segs[1];
    if (dirName.startsWith('.')) continue;
    if (!groups.has(dirName)) groups.set(dirName, []);
    groups.get(dirName).push(file);
  }
  return [...groups].map(([dirName, list]) => ({ dirName, files: list }));
}

/**
 * 单个游戏目录规范校验。
 * @param {string} dirName 游戏目录名（规范约定 = gameKey）
 * @param {File[]} files 该目录组的全部文件
 * @param {number} baseDepth 组前缀段数：单目录导入 1（相对路径 = 目录/文件），批量导入 2（父/目录/文件）
 * @returns {{ok: boolean, reason: string, indexHtml: File|null, readme: File|null, coverFile: File|null}}
 *          不合规时 ok=false 且 reason 给出可展示的原因；合规时返回三个入口文件（readme/cover 可能为 null）
 */
export function inspectGameDir(dirName, files, baseDepth) {
  // 忽略隐藏文件/目录（.DS_Store、.git 等），与单目录导入行为一致
  const visible = files.filter((f) => relSegs(f).every((seg) => !seg.startsWith('.')));
  if (visible.some((f) => relSegs(f).length > baseDepth + 1)) {
    return { ok: false, reason: `「${dirName}」含子目录或多余文件。${GAME_DIR_RULES}` };
  }
  const rootFiles = visible.filter((f) => relSegs(f).length === baseDepth + 1);
  const isCover = (f) => COVER_EXTS.some((ext) => f.name.toLowerCase().endsWith(ext));
  const illegal = rootFiles.find((f) => {
    const name = f.name.toLowerCase();
    return !['index.html', 'readme.md'].includes(name) && !isCover(f);
  });
  if (illegal) {
    return { ok: false, reason: `「${dirName}」含不支持的文件 ${illegal.name}。${GAME_DIR_RULES}` };
  }
  const indexHtml = rootFiles.find((f) => f.name.toLowerCase() === 'index.html');
  if (!indexHtml) {
    return { ok: false, reason: `「${dirName}」缺少 index.html 入口文件` };
  }
  return {
    ok: true,
    reason: '',
    indexHtml,
    readme: rootFiles.find((f) => f.name.toLowerCase() === 'readme.md') || null,
    coverFile: rootFiles.find(isCover) || null,
  };
}

/**
 * README.md 元数据解析（自 GameList 迁入共享）。
 * 首行：`# 标题（gameKey：`x`，category 建议：`y`）`；玩法行 → 描述（截到 128，与表单 maxlength 对齐）。
 * 匹配不到的字段留空（gameKey 除外，回退目录名——规范约定目录名 = gameKey），不让自动化静默填错。
 */
export function parseGameReadme(text, dirName) {
  const heading = (text.split(/\r?\n/, 1)[0] || '').replace(/^#+\s*/, '');
  const title = heading
    .replace(/（[^）]*）\s*$/, '')
    .trim()
    .slice(0, 32);
  const keyMatch = heading.match(new RegExp(`gameKey[：:]\\s*\`?(${GAME_KEY_SOURCE})\`?`, 'i'));
  const categoryMatch = heading.match(/category(?:\s*建议)?[：:]\s*`?([a-z]+)`?/i);
  const category = categoryMatch?.[1];
  const playMatch = text.match(/^\s*[-*]?\s*\**\s*玩法[：:]\s*(.+)$/m);
  return {
    title,
    gameKey: keyMatch?.[1] || dirName,
    category: GAME_CATEGORY_OPTIONS.some((opt) => opt.value === category) ? category : '',
    description: (playMatch?.[1] || '').replace(/\*+/g, '').trim().slice(0, 128),
  };
}
