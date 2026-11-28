// src/composables/useTableA11y.js
// 表格无障碍辅助：补齐 Element Plus 2.9.6 的两处限制（不改组件库、不引依赖）。
// 1) EP 的 el-table-column 只渲染排序箭头，不生成 aria-sort，读屏无法获知排序方向。
//    aria-sort 只有挂在列头 th（columnheader）上才生效，因此在 @sort-change 里
//    用 tableRef 拿到 DOM，按 column.id 定位 th 写入，并清理同表其他 th。
// 2) EP 的 attrs 透传到根 div.el-table，原生 <table> 拿不到 aria-label/aria-describedby；
//    表格内用 #append 插槽渲染 .sr-only 标题 span，再用 :aria-describedby 指向它。
import { onMounted } from 'vue';

const escapeSelector = (value) =>
  typeof CSS !== 'undefined' && CSS.escape
    ? CSS.escape(value)
    : String(value).replace(/[^a-zA-Z0-9_-]/g, '\\$&');

/**
 * @param {string} id 页面内唯一前缀（如 'order-list'），用于生成 caption 的 id
 * @param {import('vue').Ref} tableRef 指向 el-table 组件实例的模板 ref
 */
export function useTableA11y(id, tableRef) {
  const captionId = `table-caption-${id}`;

  const applySortAria = (order, column) => {
    const root = tableRef?.value?.$el;
    if (!root) return;
    // 先清掉本表所有列头的排序态，避免多次切换后残留
    root.querySelectorAll('th[aria-sort]').forEach((th) => th.removeAttribute('aria-sort'));
    if (!order || !column?.id) return;
    // EP 表头 th 的 class 含 column.id（如 el-table_1_column_2），用它定位当前排序列
    root
      .querySelectorAll(`th.${escapeSelector(column.id)}`)
      .forEach((th) => th.setAttribute('aria-sort', order));
  };

  // EP sort-change payload：{ column, prop, order }，order 为空表示取消排序
  const onSortChange = ({ order, column } = {}) => applySortAria(order, column);

  // 初始态：若表格配置了 default-sort，挂载后同步一次（无默认排序时为空操作）
  onMounted(() => {
    const states = tableRef?.value?.store?.states;
    const order = states?.sortOrder?.value;
    const prop = states?.sortProp?.value;
    const column = states?.columns?.value?.find((item) => item.property === prop);
    applySortAria(order, column);
  });

  return { captionId, onSortChange };
}
