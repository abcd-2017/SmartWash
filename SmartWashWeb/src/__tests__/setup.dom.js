/**
 * vitest 全局 setup：Node 22 的实验性全局 localStorage getter 在未提供
 * --localstorage-file 时返回 undefined，且会遮蔽 happy-dom 注入的同名属性，
 * 导致所有直接引用 `localStorage` 的测试拿到 undefined。
 * 仅在环境未提供可用 localStorage 时注入内存版 stub（happy-dom 未来修复后自动失效）。
 */
if (typeof globalThis.localStorage === 'undefined') {
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
