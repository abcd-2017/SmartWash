import { HttpStatusCode } from "@ohos/axios"
import { getAllSchool } from "../network/api/SchoolApi"
import { getUserByStudentId, updateUserInfo } from "../network/api/UserApi"
import { SchoolName } from "../network/vo/SchoolName"
import { promptAction } from "@kit.ArkUI"
import { pathStack } from "../utils/PathStackUtil"
import { CommonConstants } from "../constant/CommonConstants"

@Builder
export function AddSchoolBuilder() {
  AddSchool()
}

@Component
@Entry
struct AddSchool {
  @State schoolId: number | null = null
  @State schoolName: string | null = null
  @State studentId: string | null = null
  @State checkSchoolId: boolean = false
  @State checkStudentId: boolean = false
  searchData: Record<string, Object> = {}
  @State schoolList: Array<SchoolName> | null = null
  private scroller: Scroller = new Scroller()
  @State showSchoolList: boolean = false
  checkStudentData: Record<string, Object> = {}

  aboutToAppear() {
    this.getSchoolName()
  }

  async getSchoolName() {
    this.searchData['schoolName'] = `${this.schoolName ?? ""}`
    let res = await getAllSchool(this.searchData)
    if (res.code == HttpStatusCode.Ok) {
      this.schoolList = res.data
    }
  }

  check() {
    this.checkSchoolId = this.schoolId == null
    this.checkStudentData['studentId'] = `${this.studentId}`
    this.checkStudentId = this.studentId == null || this.studentId == ""
  }

  build() {
    NavDestination() {
      Column({ space: 18 }) {
        Row() {
          Image($r('app.media.logo'))
            .width(160)
        }.height(100)
        .justifyContent(FlexAlign.Center)

        Text("完善信息")
          .fontSize(24)
          .fontWeight(700)

        Text("请填写您的学校信息")
          .fontSize(14)
          .fontWeight(300)
          .margin({ bottom: 18 })

        Row() {
          Column({ space: 20 }) {
            Column() {
              Stack({ alignContent: Alignment.End }) {
                TextInput({ text: this.schoolName, placeholder: "搜索学校" })
                  .backgroundColor($r('sys.color.comp_background_tertiary'))
                  .maxLength(11)
                  .padding({ right: "20" })
                  .onChange(it => {
                    this.schoolName = it
                    this.getSchoolName()
                  })
                  .key("schoolNameInput")
                  .onFocus(() => {
                    this.showSchoolList = true
                  })
                  .onBlur(() => {
                    this.showSchoolList = false
                  })
                SymbolGlyph($r('sys.symbol.xmark'))
                  .onClick(() => {
                    this.schoolName = ""
                    focusControl.requestFocus("schoolNameInput")
                  })
                  .zIndex(1)
                  .margin({ right: "18" })
                  .align(Alignment.Center)
              }

              if (this.checkSchoolId) {
                Text("手机号必须位11位")
                  .fontColor($r('sys.color.warning'))
                  .fontSize(14)
                  .margin(12)
              }
            }.alignItems(HorizontalAlign.Start)

            if (this.showSchoolList && this.schoolList != null && this.schoolList.length > 0) {
              Column() {
                List({
                  scroller: this.scroller
                }) {
                  ForEach(this.schoolList, (school: SchoolName) => {
                    ListItem() {
                      Row() {
                        Text(school.schoolName)
                          .fontSize(14)
                          .fontWeight(300)
                      }.height(42)
                      .width("100%")
                      .padding({ left: "14" })
                      .onClick(() => {
                        this.schoolId = school.schoolId
                        this.schoolName = school.schoolName
                        this.showSchoolList = false
                        //下一个输入框获取焦点
                        focusControl.requestFocus("studentIdInput")
                      })
                    }
                  })
                }
              }.backgroundColor($r('sys.color.comp_background_secondary'))
              .height(200)
              .borderRadius(CommonConstants.BORDER_RADIUS)
            }

            Column() {
              TextInput({ text: this.studentId, placeholder: "学号" })
                .backgroundColor($r('sys.color.comp_background_tertiary'))
                .type(InputType.Number)
                .maxLength(16)
                .onChange(it => this.studentId = it)
                .key("studentIdInput")
              if (this.checkStudentId) {
                Text("学号不能为空")
                  .fontColor($r('sys.color.warning'))
                  .fontSize(14)
                  .margin(12)
              }
            }.alignItems(HorizontalAlign.Start)
          }
        }
        .width("85%")
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .padding(16)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)

        RowSplit()

        Button("确认").onClick(async (event: ClickEvent) => {
          this.check()
          if (this.checkSchoolId || this.checkStudentId) {
            return
          }
          let checkStudentRes = await getUserByStudentId(this.checkStudentData)
          if (checkStudentRes.code == HttpStatusCode.Ok && !checkStudentRes.data) {
            promptAction.showToast({ message: "该学号已注册" })
            return
          }
          let res = await updateUserInfo({
            schoolId: this.schoolId,
            studentId: this.studentId
          })
          if (res.code == HttpStatusCode.Ok) {
            if (pathStack.size() > 0) {
              pathStack.clear()
            }
            AppStorage.setOrCreate("userLoginFlag", true)
          } else {
            promptAction.showToast({ message: res.message })
          }
        }).width("85%")

      }.width("100%")
      .height("100%")
      .backgroundColor($r('sys.color.background_primary'))
    }.hideTitleBar(true)
  }
}