import {
    createRouter,
    createWebHistory
} from 'vue-router'
import Layout from '@/components/Layout/Layout.vue'
import Home from '@/views/Home.vue'
import SchoolList from "@/views/system/SchoolList.vue"
import UserList from "@/views/system/UserList.vue"
import RechargeList from "@/views/system/RechargeList.vue"

const routes = [{
    path: '/',
    component: Layout,
    children: [{
            path: '/',
            name: 'Home',
            component: Home,
            meta: {
                title: '首页',
                showInMenu: true
            }
        },
        {
            path: '/schools',
            name: 'SchoolList',
            component: SchoolList,
            meta: {
                title: '学校管理',
                showInMenu: true
            }
        }, {
            path: '/users',
            name: 'Users',
            component: UserList,
            meta: {
                title: '学生管理',
                showInMenu: true
            }
        }, {
            path: '/recharge',
            name: 'RechargeList',
            component: RechargeList,
            meta: {
                title: '充值记录',
                showInMenu: true
            }
        }
    ]
}]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router