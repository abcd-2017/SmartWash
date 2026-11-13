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
  0: 'warning', // 待支付（与支付状态配色一致，不再用 danger 红）
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
  3: 'primary', // 处理中
};

/** 储物柜状态 → el-tag 类型 */
export const LOCKER_STATUS_TAG_TYPES = {
  0: 'success', // 空闲
  1: 'danger', // 使用中
  2: 'warning', // 故障
};

/** 柜子状态 → 文案（公开落地页等无后端字典下发场景使用；未知值兜底） */
export const LOCKER_STATUS_TEXT = {
  0: '空闲',
  1: '使用中',
  2: '故障',
};

/** 柜子状态文案（未知状态兜底） */
export const lockerStatusText = (status) => LOCKER_STATUS_TEXT[status] || '未知状态';

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

/** 充值状态 → 文案（与后端充值记录 status 对齐：1 成功 2 失败 3 处理中） */
export const RECHARGE_STATUS_TEXT = {
  1: '成功',
  2: '失败',
  3: '处理中',
};

/** 充值状态 → el-tag 类型 */
export const RECHARGE_STATUS_TAG_TYPES = {
  1: 'success',
  2: 'danger',
  3: 'warning',
};

/** 充值状态文案（未知值兜底） */
export const rechargeStatusText = (status) => RECHARGE_STATUS_TEXT[status] || '未知';

/** 充值状态标签配色 */
export const rechargeStatusTagType = (status) => dictValue(RECHARGE_STATUS_TAG_TYPES, status);

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
  { value: 6, label: '设备投递' },
  { value: 7, label: '私密文本' },
  { value: 8, label: '物品标签' },
];

/** 短码内容类型 → 文案 */
export const SHORT_CODE_CONTENT_TYPE_TEXT = {
  1: '短链',
  2: '活码',
  3: '交付箱',
  4: '图床',
  5: '时间胶囊',
  6: '设备投递',
  7: '私密文本',
  8: '物品标签',
};

/** 短码内容类型 → el-tag 类型 */
export const SHORT_CODE_CONTENT_TYPE_TAG_TYPES = {
  1: 'primary',
  2: 'success',
  3: 'warning',
  4: 'danger',
  5: 'info',
  6: 'info',
  7: 'danger',
  8: 'success',
};

/** 物品标签寻物状态 → 文案（后端 status：0 正常 / 1 寻物中 / 2 已寻回） */
export const ASSET_TAG_STATUS_TEXT = {
  0: '正常',
  1: '寻物中',
  2: '已寻回',
};

/** 物品标签寻物状态 → el-tag 类型 */
export const ASSET_TAG_STATUS_TAG_TYPES = {
  0: 'info',
  1: 'danger',
  2: 'success',
};

/** 物品标签寻物状态文案（未知状态兜底） */
export const assetTagStatusText = (status) => ASSET_TAG_STATUS_TEXT[status] || '未知';

/** 物品标签寻物状态标签配色 */
export const assetTagStatusTagType = (status) => dictValue(ASSET_TAG_STATUS_TAG_TYPES, status);

/** 私密文本（阅后即焚）状态 → 文案（后端 SecretState：ACTIVE/BURNED/EXPIRED） */
export const SECRET_STATE_TEXT = {
  ACTIVE: '未领取',
  BURNED: '已焚毁',
  EXPIRED: '已过期',
};

/** 私密文本状态 → el-tag 类型 */
export const SECRET_STATE_TAG_TYPES = {
  ACTIVE: 'success',
  BURNED: 'danger',
  EXPIRED: 'warning',
};

/** 私密文本状态文案（未知状态兜底） */
export const secretStateText = (state) => SECRET_STATE_TEXT[state] || '未知';

/** 私密文本状态标签配色 */
export const secretStateTagType = (state) => dictValue(SECRET_STATE_TAG_TYPES, state);

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

// ---------- 优惠券 ----------

/** 优惠券状态 → 文案（与后端 CouponStatus 对齐：ACTIVE 可用，其余视为已失效） */
export const COUPON_STATUS_TEXT = {
  ACTIVE: '可用',
  INACTIVE: '已失效',
  EXPIRED: '已失效',
};

/** 优惠券状态 → el-tag 类型 */
export const COUPON_STATUS_TAG_TYPES = {
  ACTIVE: 'success',
  INACTIVE: 'info',
  EXPIRED: 'info',
};

/** 优惠券状态文案（未知值兜底） */
export const couponStatusText = (status) => COUPON_STATUS_TEXT[status] || '已失效';

/** 优惠券状态标签配色 */
export const couponStatusTagType = (status) => dictValue(COUPON_STATUS_TAG_TYPES, status);

// ---------- 观象台·术数方法（与后端占卜模块 method 枚举对齐） ----------

/** 术数方法选项（筛选下拉/表单下拉用） */
export const DIV_METHOD_OPTIONS = [
  { value: 'liuyao', label: '六爻' },
  { value: 'meihua', label: '梅花易数' },
  { value: 'qimen', label: '奇门遁甲' },
  { value: 'liuren', label: '大六壬' },
];

/** 术数方法 → 文案（未知值回显原值） */
export const divinationMethodText = (method) =>
  DIV_METHOD_OPTIONS.find((m) => m.value === method)?.label || method;

// ---------- 工作台入口配置（与后端 workbench 模块对齐） ----------

/**
 * 入口图标白名单选项（表单下拉用）。
 * 端上按 key 做白名单映射，未知 key 一律显示 default 默认图标，因此后端取值须与此处对齐。
 */
export const WORKBENCH_ENTRY_ICON_OPTIONS = [
  { value: 'calendar', label: '洗衣黄历' },
  { value: 'search', label: '渍迹识别' },
  { value: 'book', label: '面料百科' },
  { value: 'calculate', label: '费用估算' },
  { value: 'history', label: '卦历' },
  { value: 'chat', label: 'AI 助手' },
  { value: 'link', label: '短链' },
  { value: 'science', label: '实验' },
  { value: 'gamepad', label: '游戏' },
  { value: 'default', label: '默认' },
];

/**
 * 入口卡片类型选项（表单下拉用）。
 * 端上按 key 选组件渲染（tile 两列小卡 / wide 通栏卡 / row 列表行），未知 key 兜底 tile；
 * 连续 row 在端上归并进同一张白卡、连续 tile 归并为两列网格，type + sort 组合即页面构图。
 */
export const WORKBENCH_ENTRY_TYPE_OPTIONS = [
  { value: 'tile', label: '两列小卡（默认）' },
  { value: 'wide', label: '通栏横卡' },
  { value: 'row', label: '列表行' },
  { value: 'notice', label: '公告条（可关闭，target 选填）' },
];

/** 卡片类型 key → 文案（未知值回显原值） */
export const workbenchEntryTypeText = (type) =>
  WORKBENCH_ENTRY_TYPE_OPTIONS.find((o) => o.value === type)?.label || type || '-';

/**
 * 入口色组选项（表单下拉用，hex 供选项/表格色块渲染）。
 * 取值与用户端工作台卡片的色组白名单一致，两端需同步维护。
 */
export const WORKBENCH_ENTRY_COLOR_OPTIONS = [
  { value: 'green', label: '绿色', hex: '#22c55e' },
  { value: 'blue', label: '蓝色', hex: '#3b82f6' },
  { value: 'orange', label: '橙色', hex: '#f97316' },
  { value: 'pink', label: '粉色', hex: '#ec4899' },
  { value: 'teal', label: '青色', hex: '#14b8a6' },
  { value: 'purple', label: '紫色', hex: '#8b5cf6' },
];

/** 图标 key → 文案（未知值回显原值，端上会兜底为默认图标） */
export const workbenchEntryIconText = (icon) =>
  WORKBENCH_ENTRY_ICON_OPTIONS.find((o) => o.value === icon)?.label || icon || '-';

/** 色组 key → 文案（未知值回显原值） */
export const workbenchEntryColorText = (color) =>
  WORKBENCH_ENTRY_COLOR_OPTIONS.find((o) => o.value === color)?.label || color || '-';

/** 色组 key → 色值（未知值回退 Slate 灰，用于色块兜底渲染） */
export const workbenchEntryColorHex = (color) =>
  WORKBENCH_ENTRY_COLOR_OPTIONS.find((o) => o.value === color)?.hex || '#94a3b8';

// ---------- 游戏库（与后端 game 模块对齐，方案 05 §三/§五） ----------

/**
 * 游戏分类选项（筛选下拉/表单下拉用）。
 * 白名单与 Android :feature:game 的 GameCategory 枚举对齐（puzzle/arcade/word/board/other），
 * 中文文案对齐 Android strings.xml（益智/街机/文字/桌版）。
 */
export const GAME_CATEGORY_OPTIONS = [
  { value: 'puzzle', label: '益智' },
  { value: 'arcade', label: '街机' },
  { value: 'word', label: '文字' },
  { value: 'board', label: '桌版' },
  { value: 'other', label: '其他' },
];

/** 游戏分类 → 文案（未知值回显原值，端上会兜底 OTHER） */
export const gameCategoryText = (category) =>
  GAME_CATEGORY_OPTIONS.find((o) => o.value === category)?.label || category || '-';

/** 游戏分类 → el-tag 类型 */
export const GAME_CATEGORY_TAG_TYPES = {
  puzzle: 'primary',
  arcade: 'success',
  word: 'warning',
  board: 'danger',
  other: 'info',
};

/** 游戏分类标签配色 */
export const gameCategoryTagType = (category) => dictValue(GAME_CATEGORY_TAG_TYPES, category);

/**
 * 游戏图标白名单选项（表单下拉用）。
 * 与 Android gameIconFromKey 白名单对齐（方案 05 §十偏差 3 键集）：
 * gamepad/arcade/puzzle/grid/board/word，default/未知值端上兜底默认图标。
 */
export const GAME_ICON_OPTIONS = [
  { value: 'gamepad', label: '通用游戏' },
  { value: 'arcade', label: '街机' },
  { value: 'puzzle', label: '益智拼图' },
  { value: 'grid', label: '数字方块' },
  { value: 'board', label: '棋牌桌游' },
  { value: 'word', label: '文字' },
  { value: 'default', label: '默认' },
];

/**
 * 游戏色组选项：与工作台入口共用端上同一份六色组白名单
 * （green/blue/orange/pink/teal/purple，Android GameTint 同语言），不重复定义。
 */
export const GAME_COLOR_OPTIONS = WORKBENCH_ENTRY_COLOR_OPTIONS;

/** 游戏色组 key → 色值（同工作台色组映射，未知值回退 Slate 灰） */
export const gameColorHex = workbenchEntryColorHex;

/** 游戏来源 → 文案（source_type：1 上传包 2 外链，方案 05 §三） */
export const GAME_SOURCE_TYPE_TEXT = {
  1: '上传',
  2: '外链',
};

/** 游戏来源 → el-tag 类型 */
export const GAME_SOURCE_TYPE_TAG_TYPES = {
  1: 'primary',
  2: 'info',
};

/** 游戏来源文案（未知值兜底） */
export const gameSourceTypeText = (type) => GAME_SOURCE_TYPE_TEXT[type] || '未知';

/** 游戏来源标签配色 */
export const gameSourceTypeTagType = (type) => dictValue(GAME_SOURCE_TYPE_TAG_TYPES, type);

// ---------- 跳转路由字典（与后端 deeplink 模块对齐） ----------

/** 平台选项（筛选/表单下拉用）：1 Android 2 HarmonyOS 3 双端 */
export const DEEPLINK_PLATFORM_OPTIONS = [
  { value: 1, label: 'Android' },
  { value: 2, label: 'HarmonyOS' },
  { value: 3, label: '双端' },
];

/** 平台 → 文案 */
export const DEEPLINK_PLATFORM_TEXT = {
  1: 'Android',
  2: 'HarmonyOS',
  3: '双端',
};

/** 平台 → el-tag 类型 */
export const DEEPLINK_PLATFORM_TAG_TYPES = {
  1: 'primary',
  2: 'success',
  3: 'warning',
};

/** 平台文案（未知值兜底） */
export const deeplinkPlatformText = (platform) => DEEPLINK_PLATFORM_TEXT[platform] || '未知';

/** 平台标签配色 */
export const deeplinkPlatformTagType = (platform) =>
  dictValue(DEEPLINK_PLATFORM_TAG_TYPES, platform);

// ---------- 订阅逃生舱（与后端 user_subscription.status 对齐） ----------

/** 订阅状态选项（筛选下拉用） */
export const SUBSCRIPTION_STATUS_OPTIONS = [
  { value: 'active', label: '生效中' },
  { value: 'paused', label: '已暂停' },
  { value: 'canceled', label: '已取消' },
  { value: 'finished', label: '已结束' },
];

/** 订阅状态 → 文案 */
export const SUBSCRIPTION_STATUS_TEXT = {
  active: '生效中',
  paused: '已暂停',
  canceled: '已取消',
  finished: '已结束',
};

/** 订阅状态 → el-tag 类型 */
export const SUBSCRIPTION_STATUS_TAG_TYPES = {
  active: 'success',
  paused: 'warning',
  canceled: 'info',
  finished: 'info',
};

/** 订阅状态文案（未知值兜底） */
export const subscriptionStatusText = (status) => SUBSCRIPTION_STATUS_TEXT[status] || status || '-';

/** 订阅状态标签配色 */
export const subscriptionStatusTagType = (status) =>
  dictValue(SUBSCRIPTION_STATUS_TAG_TYPES, status);

/** 计费周期 → 文案（cycle_type month/year + cycle_interval → 每月/每2月/每年，未知值回显原值） */
export const subscriptionCycleText = (cycleType, interval) => {
  const unit = { month: '月', year: '年' }[cycleType];
  if (!unit) {
    return cycleType || '-';
  }
  const n = Number(interval);
  const step = Number.isFinite(n) && n > 1 ? n : '';
  return `每${step}${unit}`;
};

/** 自动续费 → 文案（auto_renew 1是 0否） */
export const subscriptionAutoRenewText = (autoRenew) => (Number(autoRenew) === 1 ? '是' : '否');

// ---------- 站内信/消息中心（与后端 notice.type 对齐：SYSTEM/ANNOUNCEMENT） ----------

/** 公告类型选项（发送表单 + 列表筛选共用） */
export const NOTICE_TYPE_OPTIONS = [
  { value: 'SYSTEM', label: '系统通知' },
  { value: 'ANNOUNCEMENT', label: '运营公告' },
];

/** 公告类型 → 文案（未知值回显原值） */
export const NOTICE_TYPE_TEXT = {
  SYSTEM: '系统通知',
  ANNOUNCEMENT: '运营公告',
};

/** 公告类型 → el-tag 类型 */
export const NOTICE_TYPE_TAG_TYPES = {
  SYSTEM: 'warning',
  ANNOUNCEMENT: 'success',
};

/** 公告类型文案（未知值兜底） */
export const noticeTypeText = (type) => NOTICE_TYPE_TEXT[type] || type || '-';

/** 公告类型标签配色 */
export const noticeTypeTagType = (type) => dictValue(NOTICE_TYPE_TAG_TYPES, type);

/** 已读率 → el-tag 类型（≥80% 绿、≥50% 蓝、>0 橙、0 灰） */
export const noticeReadRateTagType = (rateText) => {
  const value = Number.parseFloat(rateText);
  if (!Number.isFinite(value) || value <= 0) return 'info';
  if (value >= 80) return 'success';
  if (value >= 50) return 'primary';
  return 'warning';
};

// ---------- 家庭点单菜谱（与后端 dining_recipe.status/source 对齐：1/0 + ADMIN/USER） ----------

/** 菜谱上下架选项（列表筛选 + 表单共用） */
export const DINING_RECIPE_STATUS_OPTIONS = [
  { value: 1, label: '上架' },
  { value: 0, label: '下架' },
];

/** 菜谱上下架 → 文案（未知值回显原值） */
export const DINING_RECIPE_STATUS_TEXT = {
  1: '上架',
  0: '下架',
};

/** 菜谱上下架 → el-tag 类型（上架绿、下架灰） */
export const DINING_RECIPE_STATUS_TAG_TYPES = {
  1: 'success',
  0: 'info',
};

/** 菜谱上下架文案（未知值兜底） */
export const diningRecipeStatusText = (status) =>
  dictValue(DINING_RECIPE_STATUS_TEXT, status, String(status));

/** 菜谱上下架标签配色 */
export const diningRecipeStatusTagType = (status) =>
  dictValue(DINING_RECIPE_STATUS_TAG_TYPES, status);

/** 菜谱来源 → 文案 */
export const DINING_RECIPE_SOURCE_TEXT = {
  ADMIN: '运营录入',
  USER: '用户新增',
};

/** 菜谱来源文案（未知值兜底） */
export const diningRecipeSourceText = (source) =>
  DINING_RECIPE_SOURCE_TEXT[source] || source || '-';

/** 菜谱来源 → el-tag 类型 */
export const DINING_RECIPE_SOURCE_TAG_TYPES = {
  ADMIN: 'primary',
  USER: 'warning',
};

/** 菜谱来源标签配色 */
export const diningRecipeSourceTagType = (source) =>
  dictValue(DINING_RECIPE_SOURCE_TAG_TYPES, source);
// ---------- 操作审计（与后端 @Audited 注解全集对齐） ----------

// 来源：SmartWash 后端 controller/background/ 各 Controller 的 @Audited(action, targetType)。
// 后端新增注解动作时需同步维护 AUDIT_ACTION_TEXT；未知值由页面回显原始编码，不猜文案。
export const AUDIT_ACTION_TEXT = {
  ORDER_DELETE: '删除订单',
  ORDER_STATUS_UPDATE: '更新订单状态',
  ROLE_ADD: '新增角色',
  ROLE_UPDATE: '更新角色',
  ROLE_DELETE: '删除角色',
  LOCKER_ADD: '新增寄存柜',
  LOCKER_BATCH_ADD: '批量新增寄存柜',
  LOCKER_UPDATE: '更新寄存柜',
  LOCKER_DELETE: '删除寄存柜',
  USER_ADD: '新增学生',
  USER_UPDATE: '更新学生',
  USER_DELETE: '删除学生',
  PAYMENT_MANUAL_ADD: '人工补录支付',
  PAYMENT_MANUAL_UPDATE: '更新支付记录',
  USER_COUPON_DELETE: '删除用户优惠券',
  COUPON_ADD: '新增优惠券',
  COUPON_UPDATE: '更新优惠券',
  COUPON_DELETE: '删除优惠券',
  LAUNDRY_ITEM_ADD: '新增洗护套餐',
  LAUNDRY_ITEM_UPDATE: '更新洗护套餐',
  LAUNDRY_ITEM_DELETE: '删除洗护套餐',
  SCHOOL_ADD: '新增学校',
  SCHOOL_UPDATE: '更新学校',
  SCHOOL_DELETE: '删除学校',
  ADMIN_USER_ADD: '新增管理员',
  ADMIN_USER_UPDATE: '更新管理员',
  ADMIN_USER_DELETE: '删除管理员',
  SYS_CONFIG_UPDATE: '更新系统参数',
};

export const AUDIT_TARGET_TYPE_TEXT = {
  order: '订单',
  role: '角色',
  locker: '寄存柜',
  user: '学生',
  payment: '支付记录',
  user_coupon: '用户优惠券',
  coupon: '优惠券',
  laundry_item: '洗护套餐',
  school: '学校',
  admin_user: '管理员',
  sysConfig: '系统参数',
};

/** 审计筛选下拉由字典派生，保证下拉与表格文案同源 */
export const AUDIT_ACTION_OPTIONS = Object.entries(AUDIT_ACTION_TEXT).map(([value, label]) => ({
  value,
  label,
}));

export const AUDIT_TARGET_TYPE_OPTIONS = Object.entries(AUDIT_TARGET_TYPE_TEXT).map(
  ([value, label]) => ({ value, label })
);

/** 未知动作/类型回显原始编码（后端后续新增动作时页面不致显示错误文案） */
export const auditActionText = (action) => AUDIT_ACTION_TEXT[action] || action || '-';

export const auditTargetTypeText = (targetType) =>
  AUDIT_TARGET_TYPE_TEXT[targetType] || targetType || '-';

/** 动作标签配色按动词后缀派生：新增绿 / 更新蓝 / 删除红 / 其余灰 */
export const auditActionTagType = (action) => {
  if (action?.endsWith('_ADD')) return 'success';
  if (action?.endsWith('_UPDATE')) return 'primary';
  if (action?.endsWith('_DELETE')) return 'danger';
  return 'info';
};

// ---------- 验证码用途（与后端 CaptchaPurpose 对齐） ----------

export const CAPTCHA_PURPOSE_TAG_TYPES = {
  register: 'success',
  reset: 'warning',
};

/** 用途标签配色（文案 purposeDesc 由后端下发） */
export const captchaPurposeTagType = (purpose) => dictValue(CAPTCHA_PURPOSE_TAG_TYPES, purpose);

// ---------- 观象台·审计复审状态 ----------

export const DIV_AUDIT_STATUS_TEXT = {
  0: '待审',
  1: '通过',
  2: '不一致',
};

export const DIV_AUDIT_STATUS_TAG_TYPES = {
  0: 'info',
  1: 'success',
  2: 'danger',
};

export const divAuditStatusText = (status) => DIV_AUDIT_STATUS_TEXT[status] || '未知';

export const divAuditStatusTagType = (status) => dictValue(DIV_AUDIT_STATUS_TAG_TYPES, status);

/** 审计复审筛选下拉 */
export const DIV_AUDIT_STATUS_OPTIONS = Object.entries(DIV_AUDIT_STATUS_TEXT).map(
  ([value, label]) => ({
    value: Number(value),
    label,
  })
);

// ---------- 观象台·Prompt 状态 ----------

export const DIV_PROMPT_STATUS_TEXT = {
  0: '草稿',
  1: '激活',
  2: '退役',
};

export const DIV_PROMPT_STATUS_TAG_TYPES = {
  0: 'info',
  1: 'success',
  2: 'warning',
};

export const divPromptStatusText = (status) => DIV_PROMPT_STATUS_TEXT[status] || '未知';

export const divPromptStatusTagType = (status) => dictValue(DIV_PROMPT_STATUS_TAG_TYPES, status);

// ---------- 用户优惠券使用状态 ----------

export const USER_COUPON_USED_TEXT = {
  true: '已使用',
  false: '未使用',
};

export const USER_COUPON_USED_TAG_TYPES = {
  true: 'info',
  false: 'success',
};

export const userCouponUsedText = (isUsed) => USER_COUPON_USED_TEXT[String(isUsed)] || '未知';

export const userCouponUsedTagType = (isUsed) =>
  dictValue(USER_COUPON_USED_TAG_TYPES, String(isUsed));

// ---------- 优惠券状态选项（筛选/表单下拉共用） ----------

/** 优惠券状态选项（筛选/表单下拉共用；后端将 EXPIRED 归并为已失效，不单列筛选值） */
export const COUPON_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '可用' },
  { value: 'INACTIVE', label: '已失效' },
];
