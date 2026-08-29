import { createRouter, createWebHistory } from 'vue-router';
// 布局壳保持静态引入（所有登录页共用，无需分包），
// 页面组件全部改为路由懒加载（评审 #8）：按路由拆 chunk，首屏只加载当前页面
import Layout from '@/components/Layout/Layout.vue';
import { useAuthStore } from '@/stores/auth';

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
      {
        path: '/schools',
        name: 'SchoolList',
        component: () => import('@/views/system/SchoolList.vue'),
        meta: {
          title: '学校管理',
          showInMenu: true,
          icon: 'School',
          requiresAuth: true,
        },
      },
      {
        path: '/users',
        name: 'Users',
        component: () => import('@/views/system/UserList.vue'),
        meta: {
          title: '学生管理',
          showInMenu: true,
          icon: 'UserFilled',
          requiresAuth: true,
        },
      },
      {
        path: '/recharge',
        name: 'RechargeList',
        component: () => import('@/views/system/RechargeList.vue'),
        meta: {
          title: '充值记录',
          showInMenu: true,
          icon: 'Coin',
          requiresAuth: true,
        },
      },
      {
        path: '/laundry',
        name: 'LaundryList',
        component: () => import('@/views/system/LaundryList.vue'),
        meta: {
          title: '洗护套餐',
          showInMenu: true,
          icon: 'ShoppingBag',
          requiresAuth: true,
        },
      },
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
        path: '/lockers',
        name: 'LockerList',
        component: () => import('@/views/system/LockerList.vue'),
        meta: {
          title: '寄存柜管理',
          showInMenu: true,
          icon: 'Box',
          requiresAuth: true,
        },
      },
      {
        path: '/payment',
        name: 'PaymentList',
        component: () => import('@/views/system/PaymentList.vue'),
        meta: {
          title: '支付记录',
          showInMenu: true,
          icon: 'CreditCard',
          requiresAuth: true,
        },
      },
      {
        path: '/orders',
        name: 'OrderList',
        component: () => import('@/views/system/OrderList.vue'),
        meta: {
          title: '订单管理',
          showInMenu: true,
          icon: 'Document',
          requiresAuth: true,
        },
      },
      {
        path: '/coupon',
        name: 'CouponList',
        component: () => import('@/views/system/CouponList.vue'),
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
        component: () => import('@/views/system/UserCouponList.vue'),
        meta: {
          title: '用户优惠券领取管理',
          showInMenu: true,
          icon: 'CollectionTag',
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

export default router;
