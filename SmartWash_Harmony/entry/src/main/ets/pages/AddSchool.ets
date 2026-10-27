import { HttpStatusCode } from "@ohos/axios"
import { getAllSchool } from "../network/api/SchoolApi"
import { getUserByStudentId, getUserInfo, updateUserInfo } from "../network/api/UserApi"
import { SchoolName } from "../network/vo/SchoolName"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { Debouncer } from "../utils/DebounceUtil"
import { DesignColor, DesignDimens } from "../constant/DesignSystem"

@Builder
export function AddSchoolBuilder() {
  AddSchool()
}

// 学校搜索防抖间隔（毫秒）：停顿该时长后才发起请求（评审 #20）
const SEARCH_DEBOUNCE_MS: number = 300

// 学校列表内存缓存（学校数据量小、变化少）：
// 首次全量拉取后驻留内存，后续按关键字本地过滤，避免每次进入页面都打网络（对齐安卓 SchoolRepository 内存级缓存）
let schoolListCache: Array<SchoolName> | null = null

/**
 * 在缓存的学校列表里按关键字过滤（ArkTS 不用 Array.filter，改为显式循环保证类型安全）。
 */
function filterSchoolList(keyword: string): Array<SchoolName> {
  const source = schoolListCache ?? []
  const matched: Array<SchoolName> = []
  for (const school of source) {
    const name = school.schoolName ?? ""
    if (name.indexOf(keyword) >= 0) {
      matched.push(school)
    }
  }
  return matched
}

@Component
struct AddSchool {
  @State schoolId: number | null = null
  @State schoolName: string | null = null
  @State studentId: string | null = null
  @State checkSchoolId: boolean = false
  @State checkStudentId: boolean = false
  // 信息锁定只读态：学校/学号已填写的用户回填并禁用输入与提交（对齐安卓 UpdateUserInfoPage.kt:91-93,114-125）
  @State isInfoLocked: boolean = false
  searchData: Record<string, Object> = {}
  @State schoolList: Array<SchoolName> | null = null
  private scroller: Scroller = new Scroller()
  @State showSchoolList: boolean = false
  checkStudentData: Record<string, Object> = {}
  // 学校搜索防抖（统一防抖工具，差异分析 01 §4.3）
  private searchDebouncer: Debouncer = new Debouncer()
  // 搜索请求序号：响应返回时仅采纳序号等于当前值的最新请求结果（异步竞态防护，评审 #20）
  private searchSeq: number = 0

  aboutToAppear() {
    // 先拉一次当前用户信息：已填写学校/学号的进入只读态，防止本页被反复提交变更
    this.loadCurrentInfo()
    this.getSchoolName()
  }

  aboutToDisappear() {
    // 页面销毁前清理未触发的防抖定时器，避免页面销毁后仍回调（定时器硬规则）
    this.searchDebouncer.cancel()
  }

  /**
   * 拉取当前用户信息并回填（对齐安卓 UpdateUserInfoViewModel.loadCurrentInfo）。
   * 学校已填写（schoolId > 0）且学号非空即视为信息完备，进入只读态。
   */
  async loadCurrentInfo() {
    let res = await getUserInfo()
    if (res.code === HttpStatusCode.Ok && res.data !== null) {
      const schoolId = res.data.schoolVo?.schoolId ?? null
      const studentId = res.data.studentId ?? null
      if (schoolId !== null && schoolId > 0 && studentId !== null && studentId !== "") {
        this.schoolId = schoolId
        this.schoolName = res.data.schoolVo?.schoolName ?? null
        this.studentId = studentId
        this.isInfoLocked = true
      }
    }
  }

  /**
   * 学校搜索 300ms 防抖（评审 #20）：连续输入只保留最后一次，停顿 300ms 才发起请求。
   */
  debounceSearch() {
    this.searchDebouncer.debounce(() => {
      this.getSchoolName()
    }, SEARCH_DEBOUNCE_MS)
  }

  async getSchoolName() {
    const seq = ++this.searchSeq
    const keyword = `${this.schoolName ?? ""}`.trim()
    // 命中内存缓存：关键字为空直接复用全量列表，非空则本地过滤，均不再打网络
    if (schoolListCache !== null) {
      if (seq !== this.searchSeq) {
        return
      }
      this.schoolList = keyword.length === 0 ? schoolListCache : filterSchoolList(keyword)
      return
    }
    this.searchData['schoolName'] = keyword
    let res = await getAllSchool(this.searchData)
    if (seq !== this.searchSeq) {
      // 等待期间又发起了更新的搜索，丢弃本次过期结果，避免慢响应覆盖新结果（评审 #20）
      return
    }
    if (res.code === HttpStatusCode.Ok) {
      schoolListCache = res.data ?? []
      this.schoolList = keyword.length === 0 ? schoolListCache : filterSchoolList(keyword)
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

        // 信息锁定提示（对齐安卓 UpdateUserInfoPage.kt:300-304）
        if (this.isInfoLocked) {
          Text("学校与学号已填写，如需修改请联系管理员")
            .fontSize(12)
            .fontColor(DesignColor.WARNING)
            .margin({ bottom: 12 })
        }

        Row() {
          Column({ space: 20 }) {
            Column() {
              Stack({ alignContent: Alignment.End }) {
                TextInput({ text: this.schoolName, placeholder: "搜索学校" })
                  .backgroundColor(DesignColor.SURFACE_VARIANT)
                  // 学校名输入不再限长（原 maxLength(11) 会截断长校名，对齐安卓无长度限制）
                  .padding({ right: "20" })
                  .enabled(!this.isInfoLocked)
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
                .enabled(!this.isInfoLocked)
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
            // 信息锁定态禁止提交（对齐安卓 enabled = !isInfoLocked）
            if (this.isInfoLocked) {
              return
            }
            this.check()
            if (this.checkSchoolId || this.checkStudentId) {
              return
            }
            let checkStudentRes = await getUserByStudentId(this.checkStudentData)
            // 学号查重（对齐决策 D6）：接口失败统一 toast 网络错误并阻止提交，
            // 既不像安卓失败误报「已注册」，也不像原实现失败放行导致绕过查重
            if (checkStudentRes.code !== HttpStatusCode.Ok) {
              showToastSafety("网络异常，请稍后重试")
              return
            }
            // 后端 getUserByStudentId 返回 data = (该学号用户不存在)，true 才可提交（对齐安卓 checkStudentId）
            if (checkStudentRes.data !== true) {
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
          .enabled(!this.isInfoLocked)
          .width("85%")
          .backgroundColor(this.isInfoLocked ? DesignColor.TEXT_TERTIARY : DesignColor.PRIMARY)
          .fontColor(DesignColor.ON_PRIMARY)
          .borderRadius(DesignDimens.BUTTON_RADIUS)

      }.width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }.hideTitleBar(true)
  }
}