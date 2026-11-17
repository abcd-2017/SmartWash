import { describe, it, expect, beforeEach } from 'vitest';
import { config, mount, flushPromises } from '@vue/test-utils';

// 默认会 stub 掉 <Transition>，就测不到 el-menu 内置折叠过渡对 v-show 的劫持；关闭后才能复现真实浏览器行为
config.global.stubs.transition = false;
config.global.stubs['transition-group'] = false;
import { createRouter, createMemoryHistory } from 'vue-router';
import ElementPlus from 'element-plus';
import Sidebar from '@/components/Layout/Sidebar.vue';

// Sidebar 依赖 router.options.routes 生成菜单，这里构造与 MENU_GROUP_CONFIG 对齐的最小路由表
const pageStub = { template: '<div />' };
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    {
      path: '/',
      component: pageStub,
      children: [
        {
          path: '/',
          meta: { title: '数据看板', icon: 'HomeFilled', showInMenu: true },
          component: pageStub,
        },
        {
          path: '/schools',
          meta: { title: '学校管理', icon: 'School', showInMenu: true },
          component: pageStub,
        },
      ],
    },
  ],
});

function mountSidebar() {
  return mount(Sidebar, {
    global: { plugins: [router, ElementPlus] },
  });
}

// 取侧边栏直下的两个顶层 el-menu：分组菜单（上）与搜索平铺菜单（下）；
// 子分组展开时内部还会渲染 el-menu--inline，不能混入
function findMenus(wrapper) {
  const menus = wrapper.findAll('.sidebar > .el-menu');
  expect(menus.length).toBe(2);
  return menus;
}

describe('Sidebar 菜单搜索', () => {
  beforeEach(async () => {
    await router.push('/');
    await router.isReady();
  });

  it('输入搜索词切换菜单时不应改写分组菜单的宽度（否则清空后菜单卡成只显示图标）', async () => {
    const wrapper = mountSidebar();
    await flushPromises();
    const [groupMenu, searchMenu] = findMenus(wrapper);

    // 初始两个菜单都不应带 inline width / overflow
    expect(groupMenu.element.style.width).toBe('');
    expect(groupMenu.element.style.overflow).toBe('');
    expect(searchMenu.element.style.width).toBe('');

    // 输入搜索词：分组菜单被 v-show 隐藏，搜索菜单显示
    wrapper.vm.menuQuery = '学校';
    await flushPromises();

    // 回归断言：Element Plus 内置折叠过渡会响应 v-show 的隐藏动作，
    // 在 leave 钩子里写死 inline width/overflow 且重新显示时不还原 ——
    // 这正是「清空搜索后菜单只剩图标」的根因，这里在写样式的一刻就应拦住
    expect(groupMenu.element.style.width).toBe('');
    expect(groupMenu.element.style.overflow).toBe('');

    // 清空搜索词：分组菜单恢复，任何残留的内联宽度/溢出都必须不存在
    wrapper.vm.menuQuery = '';
    await flushPromises();
    expect(groupMenu.element.style.width).toBe('');
    expect(groupMenu.element.style.overflow).toBe('');
  });
});
