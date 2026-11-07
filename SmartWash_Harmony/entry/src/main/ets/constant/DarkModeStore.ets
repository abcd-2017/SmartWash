// 深浅色全局状态桥（深色模式审计补齐）：EntryAbility.syncDarkMode 唯一写入，
// UI 侧经本单例感知系统色模式变化。
// - V1 组件：直接订阅 AppStorage('darkMode')（@StorageProp/@StorageLink，见 ThemeRefresh.ets）；
// - V2 组件：无法使用 V1 状态装饰器（@StorageProp 不可用于 @ComponentV2），
//   经本 @ObservedV2 单例的 @Trace 属性建立属性级依赖（@Trace 赋值 → 引用它的 V2 build 刷新）。
// 写入方同时维护 AppStorage 与本单例（EntryAbility.syncDarkMode），两条消费链同源同值。

@ObservedV2
export class DarkModeStore {
  private static instance: DarkModeStore | undefined = undefined

  /** 全局单例（应用内唯一写入方 EntryAbility.syncDarkMode） */
  static getInstance(): DarkModeStore {
    if (DarkModeStore.instance === undefined) {
      DarkModeStore.instance = new DarkModeStore()
    }
    return DarkModeStore.instance
  }

  /** 当前是否深色（与 AppStorage('darkMode') 严格同值，EntryAbility 双写保证） */
  @Trace dark: boolean = false
}
