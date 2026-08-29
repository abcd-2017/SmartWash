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