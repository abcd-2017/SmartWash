import { HttpStatusCode } from "@ohos/axios"
import { getAllSchool } from "../network/api/SchoolApi"
import { getUserByStudentId, updateUserInfo } from "../network/api/UserApi"
import { SchoolName } from "../network/vo/SchoolName"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { DesignColor, DesignDimens } from "../constant/DesignSystem"

@Builder
export function AddSchoolBuilder() {
  AddSchool()
}

// 学校搜索防抖间隔（毫秒）：停顿该时长后才发起请求（评审 #20）
const SEARCH_DEBOUNCE_MS: number = 300

@Component
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
  // 防抖定时器句柄，-1 表示当前无待触发的搜索
  private searchTimerId: number = -1
  // 搜索请求序号：响应返回时仅采纳序号等于当前值的最新请求结果（异步竞态防护，评审 #20）
  private searchSeq: number = 0

  aboutToAppear() {
    this.getSchoolName()
  }

  aboutToDisappear() {
    // 页面销毁前清理未触发的防抖定时器，避免页面销毁后仍回调（定时器硬规则）
    if (this.searchTimerId !== -1) {
      clearTimeout(this.searchTimerId)
      this.searchTimerId = -1
    }
  }

  /**
   * 学校搜索 300ms 防抖（评审 #20）：连续输入只保留最后一次，停顿 300ms 才发起请求。
   */
  debounceSearch() {
    if (this.searchTimerId !== -1) {
      clearTimeout(this.searchTimerId)
    }
    this.searchTimerId = setTimeout(() => {
      this.searchTimerId = -1
      this.getSchoolName()
    }, SEARCH_DEBOUNCE_MS)
  }

  async getSchoolName() {
    const seq = ++this.searchSeq
    this.searchData['schoolName'] = `${this.schoolName ?? ""}`
    let res = await getAllSchool(this.searchData)
    if (seq !== this.searchSeq) {
      // 等待期间又发起了更新的搜索，丢弃本次过期结果，避免慢响应覆盖新结果（评审 #20）
      return
    }
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
          .fontWeight(FontWeight.Bold)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Text("请填写您的学校信息")
          .fontSize(14)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ bottom: 18 })

        Row() {
          Column({ space: 20 }) {
            Column() {
              Stack({ alignContent: Alignment.End }) {
                TextInput({ text: this.schoolName, placeholder: "搜索学校" })
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  .maxLength(11)
                  .padding({ right: "20" })
                  .onChange(it => {
                    this.schoolName = it
                    this.debounceSearch()
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
                Text("学校不能为空")
                  .fontColor(DesignColor.ERROR)
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
                          .fontColor(DesignColor.TEXT_PRIMARY)
                      }.height(42)
                      .width("100%")
                      .padding({ left: "14" })
                      .onClick(() => {
                        this.schoolId = school.schoolId
                        this.schoolName = school.schoolName
                        this.showSchoolList = false
                        // 下一个输入框获取焦点
                        focusControl.requestFocus("studentIdInput")
                      })
                    }
                  })
                }
              }.backgroundColor(DesignColor.SURFACE_VARIANT)
              .height(200)
              .borderRadius(DesignDimens.CARD_RADIUS)
              .border({ width: 0.5, color: DesignColor.OUTLINE })
            }

            Column() {
              TextInput({ text: this.studentId, placeholder: "学号" })
                .backgroundColor(DesignColor.SURFACE_VARIANT)
                .type(InputType.Number)
                .maxLength(16)
                .onChange(it => this.studentId = it)
                .key("studentIdInput")
              if (this.checkStudentId) {
                Text("学号不能为空")
                  .fontColor(DesignColor.ERROR)
                  .fontSize(14)
                  .margin(12)
              }
            }.alignItems(HorizontalAlign.Start)
          }
        }
        .width("85%")
        .borderRadius(DesignDimens.CARD_RADIUS)
        .padding(DesignDimens.CARD_PADDING)
        .backgroundColor(DesignColor.SURFACE)
        .border({ width: 0.5, color: DesignColor.OUTLINE })

        RowSplit()

        Button("确认")
          .onClick(async (event: ClickEvent) => {
            this.check()
            if (this.checkSchoolId || this.checkStudentId) {
              return
            }
            let checkStudentRes = await getUserByStudentId(this.checkStudentData)
            if (checkStudentRes.code == HttpStatusCode.Ok && !checkStudentRes.data) {
              showToastSafety("该学号已注册")
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
              showToastSafety(res.message, "学校信息保存失败")
            }
          })
          .width("85%")
          .backgroundColor(DesignColor.PRIMARY)
          .fontColor(DesignColor.ON_PRIMARY)
          .borderRadius(DesignDimens.BUTTON_RADIUS)

      }.width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }.hideTitleBar(true)
  }
}