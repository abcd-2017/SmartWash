import { HttpStatusCode } from '@ohos/axios';
import { CommonConstants } from '../constant/CommonConstants';
import { getUserSchoolId } from '../network/api/UserApi';
import { logger } from '../utils/Logger';
import { pathStack } from '../utils/PathStackUtil';
import { getValue } from '../utils/StorageUtil';
import { bumpHomeTabBack, bumpOrderRefresh, bumpUserInfoRefresh } from '../utils/RefreshSignal';
import { DesignColor } from '../constant/DesignSystem';
import { AddSchoolBuilder } from './AddSchool';
import { HomeBuilder, HOME_TAB_INDEX_KEY } from './Home';
import { LoginBuilder } from './Login';

@Entry
@Component
struct Index {
  @State token: string = ""
  // 首屏类型：0=登录页 1=完善学校信息 2=首页
  @State showPageType: number = 0
  // 启动加载态：已有 token 的用户在读取 token/学校信息期间展示 loading，避免闪现登录页（评审 #7）
  @State isLoading: boolean = true
  // ========== userLoginFlag 事件时序契约（评审 #22 评估结论：保守保留现有机制）==========
  // 该布尔 flag 实质是"一次性边沿信号"：Login/AddSchool 置 true → 本页 @Watch 触发
  // onUserTokenChange → 重读 token 并切换 showPageType → 末尾复位 false。依赖三条隐含约定：
  // 1) 处理器必须在末尾复位 false，否则后续置 true 不再产生边沿、事件永久丢失；
  // 2) onUserTokenChange 是异步方法：await 期间若又有页面置 true（true→true 无边沿、
  //    @Watch 不触发），该次唤醒会被处理器末尾的复位覆盖而丢失——当前仅靠
  //    "登录 → 完善学校信息"两条触发严格串行于用户操作路径这一事实保证安全；
  // 3) 消费方只有本页（Index），切换页面类型不存在并发消费。
  // 更稳健的替代方案（共享 @ObservedV2 会话对象 + @Monitor，或 CommonEvent emitter）
  // 需要同步改造 Login/AddSchool/Index 并迁移根页面状态管理版本，侵入登录主链路，
  // 对 P2 体验项而言回归风险大于收益，故本批次不改行为、仅固化时序契约说明。
  @StorageLink('userLoginFlag') @Watch("onUserTokenChange") userLoginFlag: boolean = false

  // ========== 会话态（问题 1/2 修复）==========
  // isLoggedIn 是登录根页的唯一切换信号：false 时把 Login 作为 Navigation 根内容渲染
  // 并清空页面栈 —— Home 随之销毁，其 5s 订单轮询停止。
  // 401 / 退出登录只改本会话态，不再 push/replace 登录页：旧写法会让 Home 一直存活
  // （侧滑能回首页、重登停在旧 tab、轮询持续 401、反复替换掉正在输入的登录页）。
  @StorageLink('isLoggedIn') @Watch("onLoginStateChanged") isLoggedIn: boolean = false

  // 系统深浅色（问题17）：build 内读取以建立依赖，色模式变更时整页重建、DesignColor 令牌刷新
  @StorageLink('darkMode') darkMode: boolean = false

  async aboutToAppear() {
    this.token = await getValue(CommonConstants.USER_TOKEN, "") as string
    if (this.token !== "") {
      // 启动分流过程留痕（debug：一次性低频路径，不打 token 内容）
      logger.debug('index', '冷启动检测到本地 token，校验学校信息')
      let res = await getUserSchoolId()
      if (res.code === HttpStatusCode.Ok) {
        this.showPageType = res.data === null ? 1 : 2
      } else {
        // 接口异常（含 401，网络层已统一清理并跳登录）时回退登录页
        // 降级留痕：启动分流回退登录页
        logger.warn('index', `启动分流学校信息校验失败，回退登录页 code=${res.code}`)
        this.showPageType = 0
      }
    }
    this.isLoading = false
    // 冷启动会话态：只有真正分流到首页才算已登录（避免脏 isLoggedIn 误切登录页）
    AppStorage.setOrCreate('isLoggedIn', this.showPageType === 2)
    // 分流结果：0=登录页 1=完善学校信息 2=首页
    logger.debug('index', `启动分流完成 showPageType=${this.showPageType}`)
  }

  /**
   * 会话态切换（问题 1/2）：置 false 时把 Login 作为根内容渲染并清空页面栈。
   * 必须清栈 —— 否则 Home 作为栈底内容继续存活，侧滑可回、轮询不停。
   */
  onLoginStateChanged() {
    if (!this.isLoggedIn && this.showPageType !== 0) {
      this.showPageType = 0
      this.isLoading = false
      if (pathStack.size() > 0) {
        pathStack.clear()
      }
    }
  }

  /**
   * 系统返回键拦截（@Entry 层）：Navigation 栈为空（Login/AddSchool/Home 均为根内容）时，
   * Home 的 NavDestination.onBackPressed 不会触发（API 17 实测按返回直接退后台），
   * 故在 @Entry 层兜底：Home 显示且非首页 tab 时切回首页 tab 并消费返回事件
   * （对齐安卓 HomePage.kt BackHandler enabled = settledPage != 0）；
   * 首页 tab / 登录页 / 完善信息页 → 交还系统默认（退后台）。
   */
  onBackPress(): boolean {
    if (this.showPageType === 2 && (AppStorage.get<number>(HOME_TAB_INDEX_KEY) ?? 0) !== 0) {
      bumpHomeTabBack()
      return true
    }
    return false
  }

  async onUserTokenChange() {
    if (this.userLoginFlag) {
      this.token = await getValue(CommonConstants.USER_TOKEN, "") as string
      if (this.token !== "") {
        let res = await getUserSchoolId()
        if (res.code === HttpStatusCode.Ok) {
          if (res.data === null) {
            this.showPageType = 1
            // 低频关键动作成功：登录成功但学校信息缺失，进入完善信息页
            logger.info('index', '登录成功，学校信息缺失，进入完善信息页')
          } else {
            this.showPageType = 2
            // 会话态置真：后续 401 才能被 Axios 识别为「已登录失效」并切回登录页
            AppStorage.setOrCreate("isLoggedIn", true)
            bumpUserInfoRefresh()
            bumpOrderRefresh()
            // 低频关键动作成功：登录成功分流进入首页（存量复核：级别正确，保留）
            logger.info('index', '登录成功进入首页')
          }
        } else {
          // 降级留痕：学校信息校验失败时维持当前渲染（与冷启动回退登录页不同，此处不改变既有行为）
          logger.warn('index', `登录后学校信息校验失败 code=${res.code}`)
        }
      } else {
        // 异常路径：登录态标记触发但本地 token 为空
        logger.warn('index', '登录态标记触发但本地 token 为空，忽略本次分流')
      }
      this.userLoginFlag = false
    }
  }

  build() {
    Column() {
      Navigation(pathStack) {
        if (this.isLoading) {
          // 启动加载态：读取 token 期间不直接渲染登录页，避免"闪登录页"（评审 #7）
          Column() {
            LoadingProgress()
              .width(48)
              .height(48)
              .color(DesignColor.PRIMARY)
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
        } else if (this.showPageType === 0) {
          LoginBuilder()
        } else if (this.showPageType === 1) {
          AddSchoolBuilder()
        } else {
          HomeBuilder()
        }
      }.backgroundColor(this.darkMode ? DesignColor.DARK_BACKGROUND : DesignColor.BACKGROUND)
      .hideTitleBar(true)
      .mode(NavigationMode.Stack)
    }.height('100%')
    .width('100%')
  }
}