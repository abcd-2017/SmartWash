// 内置浏览器页（壳层留守页面，对齐安卓 app ui/page/webview/WebViewPage.kt）：
// 深链 web 分支与运营位 H5 承载，路由名 WebPage（router_map + RouteNames.WEB_PAGE）。
// - 仅接受 http/https，否则 EmptyState 错误态，杜绝 javascript: 等伪协议进 WebView；
// - 原生 Web 组件（@kit.ArkWeb webview.WebviewController，零新依赖），JavaScript 与 DOM 存储开启
//   （常规 H5 含登录态 localStorage 依赖 DOM 存储，关闭会导致大量页面异常）；
// - 外部 scheme（tel/mailto 等）经 onLoadIntercept 交系统处理，不在应用内加载
//   （对齐安卓 WebViewClient.shouldOverrideUrlLoading）；
// - 返回语义：网页历史可回退则回退，历史为空才出栈本页（对齐安卓 BackHandler(canGoBack)）；
// - 加载进度为真实 onProgressChange 回调驱动的清氧细进度条（2vp 主色 + 页面底色轨道，
//   非伪造动画，无自主动效，项目无 reduced motion 机制，本页也不新增长时间动画）；
// - 标题取路由参数，缺省「详情页」。
//
// 导航条避让（安卓 navigationBarsPadding）：本端窗口未开启 setWindowLayoutFullScreen，
// 窗口内容默认已按系统安全区布局（NavDestination 内容不伸进系统导航条），
// 安卓该 padding 是 edge-to-edge 的补偿项，此处无对应内边距可消费，加 pad 反而双重避让
// （同 recap G1/G2 真机复核结论）。

import { webview } from '@kit.ArkWeb'
import { common, Want } from '@kit.AbilityKit'
import { RouteNames } from '../constant/RouteNames'
import { DesignColor, DesignFontSize } from '../constant/DesignSystem'
import { AppButton, AppButtonVariant, EmptyState, PageHeader } from '../view/common/AppComponents'
import { getObjectParam, pathStack } from '../utils/PathStackUtil'

/** WebView 页路由入参（对齐安卓 ShellRoute.WEB_VIEW 的 url/title 两个 query 参数） */
export class WebPageParam {
  /** 网页地址（调用方保证 http/https，WebPage 内仍做白名单校验兜底） */
  url: string = ''
  /** 页面标题，空串 = 兜底「详情页」 */
  title: string = ''
}

// 标题兜底文案（对齐安卓 webview_default_title「详情页」）
const WEB_DEFAULT_TITLE: string = '详情页'

// 非法链接错误态文案（对齐安卓 webview_error_url_unsupported）
const WEB_ERROR_URL_UNSUPPORTED: string = '仅支持打开网页链接'

// 加载失败错误态文案（对齐安卓 webview_error_load）
const WEB_ERROR_LOAD: string = '页面加载失败，请检查网络后重试'
// 加载失败重试按钮文案（对齐安卓 webview_action_retry）
const WEB_ACTION_RETRY: string = '重试'

@Builder
export function WebPageBuilder() {
  WebPage()
}

@Component
struct WebPage {
  @State url: string = ''
  @State title: string = WEB_DEFAULT_TITLE
  // 0-100 真实加载进度（onProgressChange 回调驱动；1-99 显示细进度条）
  @State progress: number = 0
  // 主框架加载失败标记（仅主框架错误置位，子资源失败不打断页面）
  @State loadError: boolean = false
  // 网页历史回退可用性（onPageEnd 后刷新，返回键语义跟随用户浏览预期）
  @State canGoBack: boolean = false
  private controller: webview.WebviewController = new webview.WebviewController()

  aboutToAppear(): void {
    // 安全取参（评审 #31 约定）：参数缺失/非法时不崩溃，保持错误态
    const param = getObjectParam<WebPageParam>(RouteNames.WEB_PAGE)
    if (param !== null) {
      this.url = param.url
      this.title = param.title.length > 0 ? param.title : WEB_DEFAULT_TITLE
    }
  }

  /** url 白名单：仅 http/https 放行进 Web 容器（错误态兜底，不渲染 Web） */
  private isHttpUrl(): boolean {
    return this.isHttpScheme(this.url)
  }

  /** scheme 判定（大小写不敏感，对齐安卓 request.url.scheme?.lowercase()） */
  private isHttpScheme(target: string): boolean {
    const colonIndex: number = target.indexOf(':')
    if (colonIndex < 0) {
      return false
    }
    const scheme: string = target.substring(0, colonIndex).toLowerCase()
    return scheme === 'http' || scheme === 'https'
  }

  /**
   * 外部 scheme 分发（对齐安卓 shouldOverrideUrlLoading）：tel/mailto 等非 http(s) 链接
   * 不在应用内加载，交系统 viewData 处理；无应用可处理时静默（与安卓 runCatching{...}.isSuccess 一致）。
   */
  private openExternal(url: string): void {
    try {
      const context = getContext(this) as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        uri: url,
        entities: ['entity.system.browsable']
      }
      context.startAbility(want)
    } catch (err) {
      // 无系统应用可处理该 scheme：静默返回
    }
  }

  /**
   * 返回语义（PageHeader 返回键与系统返回键共用）：
   * 网页历史可回退则回退并消费本次返回（true），历史为空交还导航默认出栈（false）。
   */
  private handleBack(): boolean {
    if (!this.isHttpUrl() || !this.canGoBack) {
      return false
    }
    try {
      this.controller.backward()
      return true
    } catch (err) {
      // controller 未附加等异常（理论不可达：canGoBack 仅在页面加载后置位），降级出栈
      return false
    }
  }

  /** 重试：清错误态复位进度后重新加载当前页（对齐安卓 webView.reload()；ArkWeb 侧方法名为 refresh） */
  private retryLoad(): void {
    this.loadError = false
    this.progress = 0
    try {
      this.controller.refresh()
    } catch (err) {
      // controller 异常时按当前 url 重新装载兜底
      this.controller.loadUrl(this.url)
    }
  }

  build() {
    NavDestination() {
      Column() {
        PageHeader({
          title: this.title,
          backClick: () => {
            if (!this.handleBack()) {
              pathStack.pop()
            }
          }
        })

        if (this.isHttpUrl()) {
          // Web 容器 + 细进度条 + 加载失败错误态叠加
          Stack({ alignContent: Alignment.TopStart }) {
            Web({ src: this.url, controller: this.controller })
              .javaScriptAccess(true)
              .domStorageAccess(true)
              // 外部 scheme 拦截（对齐安卓 shouldOverrideUrlLoading）：http(s) 留应用内，其余交系统
              .onLoadIntercept((event: OnLoadInterceptEvent) => {
                const requestUrl: string = event.data.getRequestUrl()
                if (this.isHttpScheme(requestUrl)) {
                  return false
                }
                this.openExternal(requestUrl)
                return true
              })
              .onPageBegin(() => {
                // 新一轮导航开始时复位错误态与进度
                this.loadError = false
                this.progress = 0
              })
              .onPageEnd(() => {
                try {
                  this.canGoBack = this.controller.accessBackward()
                } catch (err) {
                  // controller 异常时按不可回退处理，返回键语义降级为直接出栈
                  this.canGoBack = false
                }
              })
              .onProgressChange((event: OnProgressChangeEvent) => {
                this.progress = event.newProgress
              })
              .onErrorReceive((event: OnErrorReceiveEvent) => {
                // 仅主框架失败才进错误态（子资源 404/超时不应打断整页）
                if (event.request.isMainFrame()) {
                  this.loadError = true
                }
              })
              .width('100%')
              .height('100%')

            // 清氧细进度条：真实进度驱动（隐藏 0 与 100 两端，避免空载/完成残留）。
            // 轨道 = 页面底色 2vp（对齐安卓 LinearProgressIndicator 的 trackColor = background），
            // 原实现只有主色填充、无底轨，深色网页下进度条会「消失」。
            if (this.progress >= 1 && this.progress <= 99) {
              Stack() {
                Row()
                  .width('100%')
                  .height(2)
                  .backgroundColor(DesignColor.BACKGROUND)
                Row()
                  .width(`${this.progress}%`)
                  .height(2)
                  .backgroundColor(DesignColor.PRIMARY)
              }
              .width('100%')
              .height(2)
              .alignContent(Alignment.TopStart)
            }

            // 加载失败错误态：说明发生了什么 + 给重试（规范 §3.6）
            if (this.loadError) {
              Column() {
                EmptyState({ message: WEB_ERROR_LOAD }) {
                  AppButton({
                    text: WEB_ACTION_RETRY,
                    variant: AppButtonVariant.TEXT,
                    click: () => {
                      this.retryLoad()
                    }
                  })
                }
              }
              .width('100%')
              .height('100%')
              .backgroundColor(DesignColor.BACKGROUND)
              .justifyContent(FlexAlign.Center)
            }
          }
          .width('100%')
          .layoutWeight(1)
        } else {
          // 非法链接错误态：说明发生了什么（规范 §3.6）
          Column() {
            EmptyState({ message: WEB_ERROR_URL_UNSUPPORTED })
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
    .onBackPressed(() => {
      // 系统返回键与 PageHeader 返回键同语义：网页历史优先，否则默认出栈
      return this.handleBack()
    })
  }
}
