import { HttpStatusCode } from '@ohos/axios';
import { CommonConstants } from '../constant/CommonConstants';
import { getUserSchoolId } from '../network/api/UserApi';
import { logger } from '../utils/Logger';
import { pathStack } from '../utils/PathStackUtil';
import { getValue, initPreferences } from '../utils/StoreageUtil';
import { AddSchoolBuilder } from './AddSchool';
import { HomeBuilder } from './Home';
import { LoginBuilder } from './Login';

@Entry
@Component
struct Index {
  @State token: string = ""
  @State showPageType: number = 0
  @StorageLink('userLoginFlag') @Watch("onUserTokenChange") userLoginFlag: boolean = false

  async aboutToAppear() {
    this.token = await getValue(CommonConstants.USER_TOKEN, "") as string
    if (this.token != "") {
      let res = await getUserSchoolId()
      if (res.code == HttpStatusCode.Ok) {
        if (res.data == null) {
          this.showPageType = 1
        } else {
          this.showPageType = 2
        }
      }
    }
  }

  async onUserTokenChange() {
    if (this.userLoginFlag) {
      this.token = await getValue(CommonConstants.USER_TOKEN, "") as string
      if (this.token != null) {
        let res = await getUserSchoolId()
        if (res.code == HttpStatusCode.Ok) {
          if (res.data == null) {
            this.showPageType = 1
          } else {
            this.showPageType = 2
          }
        }
      }
      this.userLoginFlag = false
    }
  }

  build() {
    Column() {
      Navigation(pathStack) {
        if (this.showPageType == 0) {
          LoginBuilder()
        } else if (this.showPageType == 1) {
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