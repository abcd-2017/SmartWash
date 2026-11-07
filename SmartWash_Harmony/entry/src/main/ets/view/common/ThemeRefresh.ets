// 主题重建宿主（深色模式审计补齐）：
// ArkUI 状态管理不追踪静态类字段——applyDesignTheme 回写 DesignColor 令牌后，
// build 内直读静态字段的节点不会因字段变更而刷新，必须让「子树整体重建」来重读新值。
// 本组件以 if 双分支实现：darkMode 翻转 → 分支身份切换 → 子树销毁重建 →
// 子树内所有 DesignColor.XXX 直读点重新求值，颜色即新。
// 约束：子树内自定义组件会随重建重放 aboutToAppear/aboutToDisappear，
// 因此只包「纯展示内容」——页面级数据请求保持在 ThemeRefresh 之外（页面组件本体不在子树内）。
//
// 用法（页面 build 内，NavDestination 内容层）：
//   NavDestination() {
//     ThemeRefresh() {
//       Column() { ...原内容... }
//     }
//   }
//
// 版本选择：V1 页面（@Component）用 ThemeRefresh，V2 页面（@ComponentV2）用 ThemeRefreshV2。

import { DarkModeStore } from '../../constant/DarkModeStore'

// @BuilderParam 的默认空插槽（ArkTS 要求默认值必须是 @Builder 函数，不能用内联箭头函数）
@Builder
function emptyContent() {
}

/** V1 版主题重建宿主：@StorageProp 订阅 AppStorage('darkMode')，@Watch 翻转分支 */
@Component
export struct ThemeRefresh {
  // 深浅色实时跟随：@StorageProp 建立 AppStorage 依赖；字段变更本身不驱动 UI，
  // 经 @Watch 翻转 branch 使 if 双分支身份切换、触发子树重建（机制见文件头注释）
  @StorageProp('darkMode') @Watch('onDarkModeChange') darkMode: boolean = false
  @State branch: boolean = false
  @BuilderParam content: () => void = emptyContent

  onDarkModeChange(): void {
    this.branch = !this.branch
  }

  build() {
    if (this.branch === true) {
      this.content()
    } else {
      this.content()
    }
  }
}

/** V2 版主题重建宿主：@ComponentV2 不能用 @StorageProp，经 DarkModeStore 单例 @Trace 感知 */
@ComponentV2
export struct ThemeRefreshV2 {
  // @Local 持有单例实例，dark 为 @Trace 属性：EntryAbility 写入 → 依赖 dark 的 if 分支
  // 具备重建条件（分支翻转机制与 V1 版一致，见 onDarkChanged）
  @Local darkStore: DarkModeStore = DarkModeStore.getInstance()
  @Local branch: boolean = false
  @BuilderParam content: () => void = emptyContent

  // V2 无 @Watch：@Monitor 监听 @Local 持有的 @ObservedV2 实例的嵌套 @Trace 路径
  @Monitor('darkStore.dark')
  onDarkChanged(): void {
    this.branch = !this.branch
  }

  build() {
    if (this.branch === true) {
      this.content()
    } else {
      this.content()
    }
  }
}
