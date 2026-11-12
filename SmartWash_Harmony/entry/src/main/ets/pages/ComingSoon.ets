// 「建设中」占位页 — 深链兜底落点（对齐安卓各域 DeepLinkModule 的 host 注册形态）
//
// 背景：安卓新增域（lab/mood/review/subscription/delivery/ai-work）在鸿蒙端页面未实现
// （P1-P3 排期），但后端 workbench_entry 种子对这些域统一下发（无 platform 过滤），
// 工作台卡片 target（如 smartwash://lab）经 DeepLinkRegistry 注册到本页，
// 避免「点击卡片只弹 toast」的死路——用户得到一个明确的落点页面。
// 待各域页面实现后，把 DeepLinkRegistry 中对应 host 的 routeName 换成真实路由即可，本页保留兜底。


import { EmptyState, PageHeader } from '../view/common/AppComponents'
import { safeBottomVp, safeTopVp } from '../utils/SafeArea'
import { ThemeRefresh } from '../view/common/ThemeRefresh'
import { resColor } from '../utils/ResourceColor'
import { resString } from '../utils/ResourceString'

@Builder
export function ComingSoonBuilder() {
  ComingSoon()
}

@Component
struct ComingSoon {
  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内资源色直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        PageHeader({ title: resString('coming_soon_1') })

        Column() {
          EmptyState({ message: resString('coming_soon_2') })
        }
        .width("100%")
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .backgroundColor($r('app.color.BACKGROUND'))
      }
    }
    .hideTitleBar(true)
  }
}
