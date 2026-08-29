import { HttpStatusCode } from '@ohos/axios';
import { CommonConstants } from '../constant/CommonConstants';
import { getUserSchoolId } from '../network/api/UserApi';
import { logger } from '../utils/Logger';
import { pathStack } from '../utils/PathStackUtil';
import { getValue } from '../utils/StorageUtil';
import { DesignColor } from '../constant/DesignSystem';
import { AddSchoolBuilder } from './AddSchool';
import { HomeBuilder } from './Home';
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

  async aboutToAppear() {
    this.token = await getValue(CommonConstants.USER_TOKEN, "") as string
    if (this.token !== "") {
      let res = await getUserSchoolId()
      if (res.code === HttpStatusCode.Ok) {
        this.showPageType = res.data == null ? 1 : 2
      } else {
        // 接口异常（含 401，网络层已统一清理并跳登录）时回退登录页
        this.showPageType = 0
      }
    }
    this.isLoading = false
  }

  async onUserTokenChange() {
    if (this.userLoginFlag) {
      this.token = await getValue(CommonConstants.USER_TOKEN, "") as string
      if (this.token !== "") {
        let res = await getUserSchoolId()
        if (res.code === HttpStatusCode.Ok) {
          if (res.data == null) {
            this.showPageType = 1
          } else {
            this.showPageType = 2
            AppStorage.setOrCreate("refreshUserInfo", true)
            AppStorage.setOrCreate("refreshOrderCount", true)
            logger.info("index page show")
          }
        }
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
      }.backgroundColor($r('sys.color.background_primary'))
      .hideTitleBar(true)
      .mode(NavigationMode.Stack)
    }.height('100%')
    .width('100%')
  }
}