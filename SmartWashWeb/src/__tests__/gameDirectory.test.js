// src/__tests__/gameDirectory.test.js
// games-library 目录规范单测：levels.json 进入根层白名单（随上传自动导入关卡）后，
// 其余非白名单文件仍须拒绝——目录规范是单目录导入与父目录批量导入共用的唯一预检关口。
import { describe, expect, it } from 'vitest';
import { inspectGameDir, splitGameDirs, GAME_DIR_RULES } from '../views/platform/game/gameDirectory';

/** 构造带 webkitRelativePath 的伪目录文件（浏览器 webkitdirectory 选择行为的单测替身） */
const makeFile = (name, relPath, content = 'x') => {
  const file = new File([content], name);
  Object.defineProperty(file, 'webkitRelativePath', { value: relPath });
  return file;
};

describe('inspectGameDir 根层白名单', () => {
  it('index.html + README.md + 封面图合规', () => {
    const files = [
      makeFile('index.html', 'game24/index.html', '<html></html>'),
      makeFile('README.md', 'game24/README.md', '# 24点'),
      makeFile('game24.png', 'game24/game24.png'),
    ];
    const res = inspectGameDir('game24', files, 1);
    expect(res.ok).toBe(true);
    expect(res.levelsFile).toBeNull();
  });

  it('levels.json 放行为可选文件并透传 levelsFile', () => {
    const files = [
      makeFile('index.html', 'game24/index.html', '<html></html>'),
      makeFile('levels.json', 'game24/levels.json', '[{"data":"x"}]'),
    ];
    const res = inspectGameDir('game24', files, 1);
    expect(res.ok).toBe(true);
    expect(res.levelsFile).not.toBeNull();
    expect(res.levelsFile.name).toBe('levels.json');
  });

  it('levels.json 大小写不敏感（LEVELS.JSON 同样放行）', () => {
    const files = [
      makeFile('index.html', 'beat-tap/index.html'),
      makeFile('LEVELS.JSON', 'beat-tap/LEVELS.JSON'),
    ];
    const res = inspectGameDir('beat-tap', files, 1);
    expect(res.ok).toBe(true);
    expect(res.levelsFile?.name).toBe('LEVELS.JSON');
  });

  it('.levels.json 为 games-library 约定写法（隐藏名防静态部署暴露），与 levels.json 等价', () => {
    const files = [
      makeFile('index.html', 'asteroids/index.html', '<html></html>'),
      makeFile('.levels.json', 'asteroids/.levels.json', '[{"data":"x"}]'),
      makeFile('asteroids.png', 'asteroids/asteroids.png'),
    ];
    const res = inspectGameDir('asteroids', files, 1);
    expect(res.ok).toBe(true);
    expect(res.levelsFile?.name).toBe('.levels.json');
  });

  it('.levels.json 在子目录仍被忽略（隐藏文件豁免仅限根层文件名）', () => {
    const files = [
      makeFile('index.html', 'game24/index.html'),
      makeFile('.levels.json', 'game24/assets/.levels.json'),
    ];
    const res = inspectGameDir('game24', files, 1);
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('子目录或多余文件');
  });

  it('.DS_Store 等其他隐藏文件仍被过滤', () => {
    const files = [
      makeFile('index.html', 'game24/index.html'),
      makeFile('.DS_Store', 'game24/.DS_Store'),
    ];
    const res = inspectGameDir('game24', files, 1);
    expect(res.ok).toBe(true);
    expect(res.levelsFile).toBeNull();
  });

  it('白名单之外的根层文件仍被拒绝，规范文案提及 levels.json', () => {
    const files = [
      makeFile('index.html', 'game24/index.html'),
      makeFile('notes.txt', 'game24/notes.txt'),
    ];
    const res = inspectGameDir('game24', files, 1);
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('notes.txt');
    expect(GAME_DIR_RULES).toContain('levels.json');
  });

  it('子目录仍被拒绝（levels.json 只允许在根层）', () => {
    const files = [
      makeFile('index.html', 'game24/index.html'),
      makeFile('extra.json', 'game24/assets/extra.json'),
    ];
    const res = inspectGameDir('game24', files, 1);
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('子目录或多余文件');
  });

  it('缺少 index.html 仍失败（levels.json 不能替代入口文件）', () => {
    const files = [makeFile('levels.json', 'game24/levels.json', '[]')];
    const res = inspectGameDir('game24', files, 1);
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('缺少 index.html');
  });
});

describe('splitGameDirs + inspectGameDir 批量导入（父目录，baseDepth=2）', () => {
  it('父目录/游戏目录/.levels.json 三层分组后关卡文件正确透传', () => {
    const files = [
      makeFile('index.html', 'games-library/suika/index.html', '<html></html>'),
      makeFile('.levels.json', 'games-library/suika/.levels.json', '[{"data":"x"}]'),
      makeFile('README.md', 'games-library/asteroids/README.md', '# 小行星带'),
      makeFile('index.html', 'games-library/asteroids/index.html', '<html></html>'),
      makeFile('asteroids.png', 'games-library/asteroids/asteroids.png'),
    ];
    const groups = splitGameDirs(files);
    expect(groups.map((g) => g.dirName).sort()).toEqual(['asteroids', 'suika']);

    const suika = groups.find((g) => g.dirName === 'suika');
    const res = inspectGameDir('suika', suika.files, 2);
    expect(res.ok).toBe(true);
    expect(res.levelsFile?.name).toBe('.levels.json');

    const asteroids = groups.find((g) => g.dirName === 'asteroids');
    const res2 = inspectGameDir('asteroids', asteroids.files, 2);
    expect(res2.ok).toBe(true);
    expect(res2.levelsFile).toBeNull();
    expect(res2.coverFile?.name).toBe('asteroids.png');
  });

  it('隐藏父目录整组忽略（.DS_Store 目录不构成游戏目录）', () => {
    const files = [
      makeFile('index.html', 'games-library/.hidden/index.html'),
      makeFile('index.html', 'games-library/suika/index.html'),
    ];
    const groups = splitGameDirs(files);
    expect(groups.map((g) => g.dirName)).toEqual(['suika']);
  });
});
