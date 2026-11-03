package com.smartwash.common.utils.navigation

/**
 * 类型安全路由契约（Navigation 2.8）：所有 @Serializable 路由对象实现本接口。
 *
 * 存在意义：公共层（common:utils 的深链契约 DeepLinkAction / DeepLinkParser /
 * DeepLinkContributor）需要持有「具体路由对象」以便直接 navigate，但公共层
 * 禁止依赖任何 feature 域。本接口是唯一的公共抽象——各域把 @Serializable
 * 路由对象声明为实现本接口，深链解析结果即可类型安全地携带并导航。
 *
 * 约束：
 * - 实现类必须标注 @Serializable（Navigation 2.8 类型安全 composable<T> /
 *   navigate(T) 的前提）；
 * - 路由串由 @SerialName 固定（与迁移前的字符串常量保持一致，保证页面栈/
 *   deep link/restoreState 行为等价）；
 * - 带参路由的属性名即导航参数名（SavedStateHandle / arguments 读取同源）。
 */
interface SmartWashRoute
