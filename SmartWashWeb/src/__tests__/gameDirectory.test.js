// src/__tests__/gameDirectory.test.js
// games-library 目录规范单测：levels.json 进入根层白名单（随上传自动导入关卡）后，
// 其余非白名单文件仍须拒绝——目录规范是单目录导入与父目录批量导入共用的唯一预检关口。
import { describe, expect, it } from 'vitest';
import { inspectGameDir, GAME_DIR_RULES } from '../views/platform/game/gameDirectory';

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
