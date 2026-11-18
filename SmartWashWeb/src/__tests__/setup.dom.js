/**
 * vitest 全局 setup：Node 22 的实验性全局 localStorage getter 在未提供
 * --localstorage-file 时返回 undefined，且会遮蔽 happy-dom 注入的同名属性，
 * 导致所有直接引用 `localStorage` 的测试拿到 undefined。
 * 仅在环境未提供可用 localStorage 时注入内存版 stub（happy-dom 未来修复后自动失效）。
 */
// Node 22 的实验性 localStorage 在未提供 --localstorage-file 时是一个残缺实现：
// 全局属性存在但缺 getItem/clear 等方法（并非 undefined），上面的「仅未定义才注入」
// 条件永远不命中，导致 http.test.js 的 localStorage.clear() 与 pinia devtools-kit
// 的 localStorage.getItem() 直接抛 TypeError。改为：核心方法任一缺失即覆写。
const ls = globalThis.localStorage;
const needsStub =
  typeof ls === 'undefined' ||
  typeof ls !== 'object' ||
  typeof ls.getItem !== 'function' ||
  typeof ls.setItem !== 'function' ||
  typeof ls.removeItem !== 'function' ||
  typeof ls.clear !== 'function';
if (needsStub) {
  const store = new Map();
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key) => (store.has(key) ? store.get(key) : null),
      setItem: (key, value) => store.set(key, String(value)),
      removeItem: (key) => store.delete(key),
      clear: () => store.clear(),
      key: (index) => Array.from(store.keys())[index] ?? null,
      get length() {
        return store.size;
      },
    },
  });
}
