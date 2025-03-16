import {
    createRouter,
    createWebHistory
} from 'vue-router'
import Layout from '@/components/Layout/Layout.vue'
import Home from '@/views/Home.vue'
import SchoolList from "@/views/system/SchoolList.vue"
import UserList from "@/views/system/UserList.vue"
import RechargeList from "@/views/system/RechargeList.vue"
import LaundryList from "@/views/system/LaundryList.vue"
import RoleList from "@/views/system/RoleList.vue"
import AdminUserList from "@/views/system/AdminUserList.vue"
import LockerList from "@/views/system/LockerList.vue"
import PaymentList from "@/views/system/PaymentList.vue"
import OrderList from "@/views/system/OrderList.vue"
import LoginPage from "@/views/LoginPage.vue"

const routes = [{
    path: '/login',
    name: 'LoginPage',
    component: LoginPage,
    meta: {
        requiresAuth: false
    }
}, {
    path: '/',
    component: Layout,
    children: [{
            path: '/',
            name: 'Home',
            component: Home,
            meta: {
                title: '首页',
                showInMenu: true,
                requiresAuth: true
            }
        },
        {
            path: '/schools',
            name: 'SchoolList',
            component: SchoolList,
            meta: {
                title: '学校管理',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/users',
            name: 'Users',
            component: UserList,
            meta: {
                title: '学生管理',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/recharge',
            name: 'RechargeList',
            component: RechargeList,
            meta: {
                title: '充值记录',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/laundry',
            name: 'LaundryList',
            component: LaundryList,
            meta: {
                title: '洗护套餐',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/roles',
            name: 'RoleList',
            component: RoleList,
            meta: {
                title: '角色管理',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/adminUsers',
            name: 'AdminUserList',
            component: AdminUserList,
            meta: {
                title: '管理员角色管理',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/lockers',
            name: 'LockerList',
            component: LockerList,
            meta: {
                title: '寄存柜管理',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/payment',
            name: 'PaymentList',
            component: PaymentList,
            meta: {
                title: '支付记录',
                showInMenu: true,
                requiresAuth: true
            }
        }, {
            path: '/orders',
            name: 'OrderList',
            component: OrderList,
            meta: {
                title: '订单管理',
                showInMenu: true,
                requiresAuth: true
            }
        },
    ]
}]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const isAuthenticated = localStorage.getItem('token');
    // 需要登录但未登录
    if (to.meta.requiresAuth !== false && !isAuthenticated) {
        next('/login')
    } else {
        next()
    }
})

export default router