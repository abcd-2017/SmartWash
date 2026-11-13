// src/__tests__/levelImportParser.test.js
// 关卡批量导入双兼容解析的单测：裸格式（游戏包 DEFAULT_LEVELS 拷出）、信封格式、混用、
// 错误行定位与序号分配决策。解析归一是提交前的唯一预检关口，行为必须与后端校验对齐。
import { describe, expect, it } from 'vitest';
import {
  buildImportPayload,
  parseLevelImportText,
  planLevelImport,
  MAX_IMPORT_COUNT,
} from '../views/platform/game/levelImportParser';

describe('parseLevelImportText 整体校验', () => {
  it('非 JSON 文本整体失败并透传解析原因', () => {
    const res = parseLevelImportText('not-json{{');
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('JSON 解析失败');
  });

  it('顶层非数组整体失败', () => {
    const res = parseLevelImportText('{"data": 1}');
    expect(res.ok).toBe(false);
    expect(res.reason).toContain('顶层必须是 JSON 数组');
  });

  it('空数组失败', () => {
    expect(parseLevelImportText('[]').reason).toContain('不含任何关卡');
  });

  it(`超过 ${MAX_IMPORT_COUNT} 关失败`, () => {
    const big = JSON.stringify(Array.from({ length: MAX_IMPORT_COUNT + 1 }, () => 'x'));
    expect(parseLevelImportText(big).reason).toContain('单次最多导入');
  });
});

describe('parseLevelImportText 双兼容元素', () => {
  it('裸字符串元素（推箱子单关为地图数组时按裸数组元素整体入库）', () => {
    const res = parseLevelImportText(JSON.stringify([['#####', '#   #'], ['##  ##', '# $ #']]));
    expect(res.ok).toBe(true);
    expect(res.items).toHaveLength(2);
    expect(res.items[0].dataJson).toBe(JSON.stringify(['#####', '#   #']));
    expect(res.items[0].error).toBeNull();
    expect(res.items[0].levelNo).toBeNull(); // 序号由导入时顺延分配
  });

  it('裸对象元素（魔塔类地图对象）不被误判为信封', () => {
    const level = { map: '...', hp: 10 };
    const res = parseLevelImportText(JSON.stringify([level]));
    expect(res.ok).toBe(true);
    expect(res.items[0].dataJson).toBe(JSON.stringify(level));
  });

  it('信封元素：携带元数据与 data，缺省序号为 null', () => {
    const res = parseLevelImportText(
      JSON.stringify([{ name: '热身靶场', difficulty: 2, data: { map: 'x' } }])
    );
    expect(res.ok).toBe(true);
    const item = res.items[0];
    expect(item.name).toBe('热身靶场');
    expect(item.difficulty).toBe(2);
    expect(item.enabled).toBeNull(); // 缺省交后端按 1
    expect(item.levelNo).toBeNull();
    expect(item.dataJson).toBe(JSON.stringify({ map: 'x' }));
  });

  it('信封与裸元素可在同一文件混用', () => {
    const res = parseLevelImportText(
      JSON.stringify([{ levelNo: 1, data: 'a' }, 'b'])
    );
    expect(res.ok).toBe(true);
    expect(res.items[0].levelNo).toBe(1);
    expect(res.items[1].dataJson).toBe('"b"');
  });

  it('信封缺 data（data 为 null）标错误行', () => {
    // 无 data 键的对象按裸元素处理（魔塔类地图对象合法），仅 data 键存在但为空才判信封缺数据
    const res = parseLevelImportText(JSON.stringify([{ name: 'x', data: null }]));
    expect(res.ok).toBe(true);
    expect(res.items[0].error).toContain('信封缺少 data');
  });

  it('enabled 非法值标错误行而非静默归一成下架', () => {
    const res = parseLevelImportText(JSON.stringify([{ enabled: 2, data: 'x' }]));
    expect(res.items[0].error).toContain('enabled 仅允许 0 或 1');
  });

  it('levelNo 非正整数标错误行', () => {
    const res = parseLevelImportText(JSON.stringify([{ levelNo: 0, data: 'x' }]));
    expect(res.items[0].error).toContain('levelNo 必须是正整数');
  });

  it('name 超 32 字标错误行', () => {
    const res = parseLevelImportText(JSON.stringify([{ name: '长'.repeat(33), data: 'x' }]));
    expect(res.items[0].error).toContain('name 最长 32 字');
  });

  it('错误行携带序号便于定位', () => {
    const res = parseLevelImportText(JSON.stringify(['ok', { data: null }]));
    expect(res.items[1].error).toContain('第 2 项');
  });
});

describe('planLevelImport 序号分配与处置', () => {
  const existing = [
    { levelNo: 1 },
    { levelNo: 2 },
    { levelNo: 3 },
  ];

  it('缺省序号从现有最大序号之后顺延', () => {
    const { ok, items } = parseLevelImportText(JSON.stringify(['a', 'b']));
    const plan = planLevelImport(items, existing);
    expect(ok).toBe(true);
    expect(plan.rows.map((r) => r.levelNo)).toEqual([4, 5]);
    expect(plan.created).toBe(2);
    expect(plan.updated).toBe(0);
    expect(plan.blocked).toBe(false);
  });

  it('顺延跳过信封显式占用的序号', () => {
    // 现有 1..3，信封占 5，缺省项顺延为 4、6
    const { items } = parseLevelImportText(JSON.stringify([{ levelNo: 5, data: 'x' }, 'a', 'b']));
    const plan = planLevelImport(items, existing);
    expect(plan.rows.map((r) => r.levelNo)).toEqual([5, 4, 6]);
  });

  it('信封显式序号命中现有关卡标为覆盖', () => {
    const { items } = parseLevelImportText(JSON.stringify([{ levelNo: 2, data: 'new' }]));
    const plan = planLevelImport(items, existing);
    expect(plan.rows[0].fate).toBe('update');
    expect(plan.updated).toBe(1);
  });

  it('批内显式序号重复标错误行并阻断提交', () => {
    const { items } = parseLevelImportText(
      JSON.stringify([{ levelNo: 9, data: 'a' }, { levelNo: 9, data: 'b' }])
    );
    const plan = planLevelImport(items, existing);
    expect(plan.blocked).toBe(true);
    expect(plan.rows.every((r) => r.fate === 'error')).toBe(true);
    expect(plan.rows[0].error).toContain('本批内重复');
  });

  it('错误行原样保留不参与序号分配', () => {
    const { items } = parseLevelImportText(JSON.stringify([{ data: null }, 'good']));
    const plan = planLevelImport(items, existing);
    expect(plan.rows[0].fate).toBe('error');
    expect(plan.rows[1].levelNo).toBe(4); // 错误行不占号
    expect(plan.blocked).toBe(true);
  });
});

describe('buildImportPayload 请求体构建', () => {
  it('只输出后端契约字段并透传 replaceAll', () => {
    const rows = [
      { levelNo: 4, name: 'N', dataJson: '"a"', difficulty: 1, enabled: null, sort: null },
    ];
    const payload = buildImportPayload(rows, { gameKey: 'sokoban', replaceAll: true });
    expect(payload).toEqual({
      gameKey: 'sokoban',
      replaceAll: true,
      levels: [
        { levelNo: 4, name: 'N', dataJson: '"a"', difficulty: 1, enabled: null, sort: null },
      ],
    });
  });
});
