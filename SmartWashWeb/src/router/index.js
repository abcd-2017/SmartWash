import { createRouter, createWebHistory } from 'vue-router';
// 布局壳保持静态引入（所有登录页共用，无需分包），
// 页面组件全部改为路由懒加载（评审 #8）：按路由拆 chunk，首屏只加载当前页面
import Layout from '@/components/Layout/Layout.vue';
import { useAuthStore } from '@/stores/auth';

// 子路由声明顺序与侧边栏 MENU_GROUP_CONFIG 的六组一一对应：
// 基础数据（campus/）→ 业务管理（operations/）→ 用户运营（users/）
// → 平台模块（platform/）→ 观象台（divination/）→ 系统与安全（system/）
const routes = [
  {
    path: '/login',
    name: 'LoginPage',
    component: () => import('@/views/LoginPage.vue'),
    meta: {
      requiresAuth: false,
    },
  },
  {
    // 设备投递公开取件页（对端 = 电脑浏览器）：免登录匿名访问，码即凭证。
    // 独立可达，不挂 Layout、不进导航菜单；:code 可选——无码访问时进入页提供输码表单
    path: '/d/:code?',
    name: 'DeliveryPickup',
    component: () => import('@/views/delivery/DeliveryPickup.vue'),
    meta: {
      title: '设备投递',
      requiresAuth: false,
    },
  },
  {
    path: '/',
    component: Layout,
    children: [
      {
        path: '/',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: {
          title: '首页',
          showInMenu: true,
          icon: 'HomeFilled',
          requiresAuth: true,
        },
      },
      // ===== 基础数据 =====
      {
        path: '/schools',
        name: 'SchoolList',
        component: () => import('@/views/campus/SchoolList.vue'),
        meta: {
          title: '学校管理',
          showInMenu: true,
          icon: 'School',
          requiresAuth: true,
        },
      },
      {
        path: '/lockers',
        name: 'LockerList',
        component: () => import('@/views/campus/LockerList.vue'),
        meta: {
          title: '寄存柜管理',
          showInMenu: true,
          icon: 'Box',
          requiresAuth: true,
        },
      },
      {
        path: '/laundry',
        name: 'LaundryList',
        component: () => import('@/views/campus/LaundryList.vue'),
        meta: {
          title: '洗护套餐',
          showInMenu: true,
          icon: 'ShoppingBag',
          requiresAuth: true,
        },
      },
      // ===== 业务管理 =====
      {
        path: '/orders',
        name: 'OrderList',
        component: () => import('@/views/operations/OrderList.vue'),
        meta: {
          title: '订单管理',
          showInMenu: true,
          icon: 'Document',
          requiresAuth: true,
        },
      },
      {
        path: '/payment',
        name: 'PaymentList',
        component: () => import('@/views/operations/PaymentList.vue'),
        meta: {
          title: '支付记录',
          showInMenu: true,
          icon: 'CreditCard',
          requiresAuth: true,
        },
      },
      {
        path: '/recharge',
        name: 'RechargeList',
        component: () => import('@/views/operations/RechargeList.vue'),
        meta: {
          title: '充值记录',
          showInMenu: true,
          icon: 'Coin',
          requiresAuth: true,
        },
      },
      // ===== 用户运营 =====
      {
        path: '/users',
        name: 'Users',
        component: () => import('@/views/users/UserList.vue'),
        meta: {
          title: '学生管理',
          showInMenu: true,
          icon: 'UserFilled',
          requiresAuth: true,
        },
      },
      {
        path: '/coupon',
        name: 'CouponList',
        component: () => import('@/views/users/CouponList.vue'),
        meta: {
          title: '优惠券管理',
          showInMenu: true,
          icon: 'Ticket',
          requiresAuth: true,
        },
      },
      {
        path: '/userCoupon',
        name: 'UserCouponList',
        component: () => import('@/views/users/UserCouponList.vue'),
        meta: {
          title: '用户优惠券领取管理',
          showInMenu: true,
          icon: 'CollectionTag',
          requiresAuth: true,
        },
      },
      {
        // 站内信：用户触达工具，与优惠券同属用户运营（不再单独成组）
        path: '/notices',
        name: 'NoticeList',
        component: () => import('@/views/users/NoticeList.vue'),
        meta: {
          title: '站内信管理',
          showInMenu: true,
          icon: 'Bell',
          requiresAuth: true,
        },
      },
      // ===== 平台模块 =====
      {
        path: '/toolbox/short-codes',
        name: 'ShortCodeList',
        component: () => import('@/views/platform/toolbox/ShortCodeList.vue'),
        meta: {
          title: '短链管理',
          showInMenu: true,
          icon: 'Link',
          requiresAuth: true,
        },
      },
      {
        path: '/workbench/entries',
        name: 'WorkbenchEntryList',
        component: () => import('@/views/platform/workbench/WorkbenchEntryList.vue'),
        meta: {
          title: '工作台入口',
          showInMenu: true,
          icon: 'Grid',
          requiresAuth: true,
        },
      },
      {
        path: '/workbench/deeplink-routes',
        name: 'DeeplinkRouteList',
        component: () => import('@/views/platform/workbench/DeeplinkRouteList.vue'),
        meta: {
          title: '跳转路由',
          showInMenu: true,
          icon: 'Guide',
          requiresAuth: true,
        },
      },
      {
        path: '/game/list',
        name: 'GameList',
        component: () => import('@/views/platform/game/GameList.vue'),
        meta: {
          title: '游戏库',
          showInMenu: true,
          icon: 'VideoPlay',
          requiresAuth: true,
        },
      },
      {
        // 游戏运行期数据管理：关卡维护（热更新，不重传游戏包）+ 存档/成绩查看
        path: '/game/data',
        name: 'GameData',
        component: () => import('@/views/platform/game/GameData.vue'),
        meta: {
          title: '游戏数据',
          showInMenu: true,
          icon: 'DataAnalysis',
          requiresAuth: true,
        },
      },
      {
        path: '/subscriptions',
        name: 'SubscriptionList',
        component: () => import('@/views/platform/SubscriptionList.vue'),
        meta: {
          title: '订阅管理',
          showInMenu: true,
          icon: 'AlarmClock',
          requiresAuth: true,
        },
      },
      // ===== 观象台（占卜模块）管理端 =====
      {
        path: '/divination/prompts',
        name: 'DivPromptList',
        component: () => import('@/views/divination/DivPromptList.vue'),
        meta: {
          title: 'Prompt 管理',
          showInMenu: true,
          icon: 'ChatLineRound',
          requiresAuth: true,
        },
      },
      {
        path: '/divination/rag',
        name: 'DivRagList',
        component: () => import('@/views/divination/DivRagList.vue'),
        meta: {
          title: '语料管理',
          showInMenu: true,
          icon: 'Reading',
          requiresAuth: true,
        },
      },
      {
        path: '/divination/audits',
        name: 'DivAuditList',
        component: () => import('@/views/divination/DivAuditList.vue'),
        meta: {
          title: '审计复审',
          showInMenu: true,
          icon: 'Warning',
          requiresAuth: true,
        },
      },
      {
        path: '/divination/usage',
        name: 'DivUsage',
        component: () => import('@/views/divination/DivUsage.vue'),
        meta: {
          title: '用量看板',
          showInMenu: true,
          icon: 'DataAnalysis',
          requiresAuth: true,
        },
      },
      {
        path: '/divination/blocked',
        name: 'DivBlockedList',
        component: () => import('@/views/divination/DivBlockedList.vue'),
        meta: {
          title: '拦截日志',
          showInMenu: true,
          icon: 'CircleClose',
          requiresAuth: true,
        },
      },
      {
        path: '/divination/models',
        name: 'DivModelList',
        component: () => import('@/views/divination/DivModelList.vue'),
        meta: {
          title: '模型管理',
          showInMenu: true,
          icon: 'Cpu',
          requiresAuth: true,
        },
      },
      {
        path: '/divination/settings',
        name: 'DivSettings',
        component: () => import('@/views/divination/DivSettings.vue'),
        meta: {
          title: '平台设置',
          showInMenu: true,
          icon: 'Setting',
          requiresAuth: true,
        },
      },
      // ===== 系统与安全 =====
      {
        path: '/roles',
        name: 'RoleList',
        component: () => import('@/views/system/RoleList.vue'),
        meta: {
          title: '角色管理',
          showInMenu: true,
          icon: 'Key',
          requiresAuth: true,
        },
      },
      {
        path: '/adminUsers',
        name: 'AdminUserList',
        component: () => import('@/views/system/AdminUserList.vue'),
        meta: {
          title: '管理员角色管理',
          showInMenu: true,
          icon: 'Avatar',
          requiresAuth: true,
        },
      },
      {
        path: '/audit-logs',
        name: 'AuditLogList',
        component: () => import('@/views/system/AuditLogList.vue'),
        meta: {
          title: '操作审计',
          showInMenu: true,
          icon: 'DocumentChecked',
          requiresAuth: true,
        },
      },
      {
        // 演示工具：验证码查询，仅供演示环境快速取码，生产应考虑下线
        path: '/captcha',
        name: 'CaptchaQuery',
        component: () => import('@/views/system/CaptchaQuery.vue'),
        meta: {
          title: '验证码查询',
          showInMenu: true,
          icon: 'Message',
          requiresAuth: true,
        },
      },
      // ===== 家庭点单（dining 域）=====
      {
        path: '/dining/categories',
        name: 'DiningCategoryList',
        component: () => import('@/views/dining/DiningCategoryList.vue'),
        meta: {
          title: '分类管理',
          showInMenu: true,
          icon: 'Dish',
          requiresAuth: true,
        },
      },
      {
        path: '/dining/recipes',
        name: 'DiningRecipeList',
        component: () => import('@/views/dining/DiningRecipeList.vue'),
        meta: {
          title: '菜谱管理',
          showInMenu: true,
          icon: 'ForkSpoon',
          requiresAuth: true,
        },
      },
    ],
  },
  {
    // 404 兜底页：未匹配到的路径不再静默重定向首页，而是明确提示页面不存在
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: {
      title: '页面不存在',
      requiresAuth: false,
    },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  // 登录态统一从 Pinia store 读取（store 初始化时自动从 localStorage 恢复）
  const auth = useAuthStore();
  // 已登录用户访问登录页，重定向到首页
  if (to.path === '/login' && auth.token) {
    next('/');
  } else if (to.meta.requiresAuth !== false && !auth.token) {
    next('/login');
  } else if (to.meta.requiresAuth !== false && !auth.role) {
    // 仅拦截角色缺失（后端登录接口必返回 role）；管理端账号均为管理员，
    // 角色名为中文展示名（如"超级管理员"），具体接口权限由后端 /admin/** 的 ROLE_ADMIN 强校验兜底
    auth.clearLogin();
    next('/login');
  } else {
    next();
  }
});

// 各路由 meta.title 此前无任何消费方，浏览器标签恒显示 index.html 的静态标题；
// 路由切换时同步标签标题，让多标签页管理后台可按页面区分
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · SmartWash 管理系统` : 'SmartWash 管理系统';
});

export default router;
