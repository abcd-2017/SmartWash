// src/constants/dict.js
// 枚举字典统一维护（评审 #16）：状态颜色/文案与后端枚举对齐，
// 页面一律从本模块引用，禁止再写硬编码 switch。
// 注：订单/储物柜/支付状态的中文文案由后端枚举接口下发，前端只维护标签配色。

// 通用兜底取值：命中返回映射值，否则返回 fallback
function dictValue(map, key, fallback = 'info') {
  return map[key] ?? fallback;
}

/** 订单状态 → el-tag 类型（key 与后端 OrdersStatus 对齐） */
export const ORDER_STATUS_TAG_TYPES = {
  '-2': 'info', // 已取消
  '-1': 'warning', // 已退款
  0: 'danger', // 待支付
  1: '', // 待寄件（默认）
  2: 'success', // 已收取
  3: 'primary', // 清洗中
  4: '', // 已烘干（默认）
  5: 'warning', // 配送中
  6: 'success', // 待取件
  7: 'success', // 已完成
};

/** 支付状态 → el-tag 类型 */
export const PAY_STATUS_TAG_TYPES = {
  0: 'warning', // 待支付
  1: 'success', // 已支付
  2: 'danger', // 失败
};

/** 储物柜状态 → el-tag 类型 */
export const LOCKER_STATUS_TAG_TYPES = {
  0: 'success', // 空闲
  1: 'danger', // 使用中
  2: 'warning', // 故障
};

/** 充值类型选项（搜索下拉用） */
export const RECHARGE_TYPE_OPTIONS = [
  { value: '1', label: '微信支付' },
  { value: '2', label: '支付宝支付' },
];

/** 充值类型 → 文案 */
export const RECHARGE_TYPE_TEXT = {
  1: '微信支付',
  2: '支付宝支付',
};

/** 充值类型 → el-tag 类型 */
export const RECHARGE_TYPE_TAG_TYPES = {
  1: 'success', // 微信支付
  2: 'primary', // 支付宝支付
};

/** 订单状态标签配色 */
export const orderStatusTagType = (status) => dictValue(ORDER_STATUS_TAG_TYPES, status);

/** 支付状态标签配色 */
export const payStatusTagType = (status) => dictValue(PAY_STATUS_TAG_TYPES, status);

/** 储物柜状态标签配色 */
export const lockerStatusTagType = (status) => dictValue(LOCKER_STATUS_TAG_TYPES, status);

/** 充值类型标签配色 */
export const rechargeTypeTagType = (type) => dictValue(RECHARGE_TYPE_TAG_TYPES, type);

/** 充值类型文案（未知类型兜底） */
export const rechargeTypeText = (type) => RECHARGE_TYPE_TEXT[type] || '未知类型';

// ---------- 工具箱短码（与后端 ToolboxShortCode 对齐） ----------

/** 短码内容类型选项（搜索下拉用）：1短链 2活码 3交付箱 4图床 5时间胶囊 */
export const SHORT_CODE_CONTENT_TYPE_OPTIONS = [
  { value: 1, label: '短链' },
  { value: 2, label: '活码' },
  { value: 3, label: '交付箱' },
  { value: 4, label: '图床' },
  { value: 5, label: '时间胶囊' },
];

/** 短码内容类型 → 文案 */
export const SHORT_CODE_CONTENT_TYPE_TEXT = {
  1: '短链',
  2: '活码',
  3: '交付箱',
  4: '图床',
  5: '时间胶囊',
};

/** 短码内容类型 → el-tag 类型 */
export const SHORT_CODE_CONTENT_TYPE_TAG_TYPES = {
  1: 'primary',
  2: 'success',
  3: 'warning',
  4: 'danger',
  5: 'info',
};

/** 短码可见性 → 文案（后端 isPublic：1公开 2私有） */
export const SHORT_CODE_VISIBILITY_TEXT = {
  1: '公开',
  2: '私有',
};

/** 短码可见性 → el-tag 类型 */
export const SHORT_CODE_VISIBILITY_TAG_TYPES = {
  1: 'success',
  2: 'info',
};

/** 短码内容类型文案（未知类型兜底） */
export const shortCodeContentTypeText = (type) => SHORT_CODE_CONTENT_TYPE_TEXT[type] || '未知类型';

/** 短码内容类型标签配色 */
export const shortCodeContentTypeTagType = (type) =>
  dictValue(SHORT_CODE_CONTENT_TYPE_TAG_TYPES, type);

/** 短码可见性文案（未知值兜底） */
export const shortCodeVisibilityText = (type) => SHORT_CODE_VISIBILITY_TEXT[type] || '未知';

/** 短码可见性标签配色 */
export const shortCodeVisibilityTagType = (type) =>
  dictValue(SHORT_CODE_VISIBILITY_TAG_TYPES, type);
