import { HttpStatusCode } from "@ohos/axios"
import { getAllSchool } from "../network/api/SchoolApi"
import { getUserByStudentId, getUserInfo, updateUserInfo } from "../network/api/UserApi"
import { SchoolName } from "../network/vo/SchoolName"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { Debouncer } from "../utils/DebounceUtil"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight, MotionTiming } from "../constant/DesignSystem"
import { AppButton } from "../view/common/AppComponents"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"

@Builder
export function AddSchoolBuilder() {
  AddSchool()
}

// 学校搜索防抖间隔（毫秒）：停顿该时长后才发起请求（评审 #20）
const SEARCH_DEBOUNCE_MS: number = 300

// 选中高亮至少可见的时长——颜色过渡走完再收焦点收起下拉（对齐安卓 SELECTION_SETTLE_MS）
const SELECTION_SETTLE_MS: number = 180

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

// ========== 完善信息页（模板 C，对齐安卓 UpdateUserInfoPage.kt）==========
@Component
struct AddSchool {
  @State schoolId: number | null = null
  @State schoolName: string | null = null
  @State studentId: string | null = null
  @State checkSchoolId: boolean = false
  @State checkStudentId: boolean = false
  // 信息锁定只读态：学校/学号已填写的用户回填并禁用输入与提交（对齐安卓 UpdateUserInfoPage.kt:91-93,114-125）
  @State isInfoLocked: boolean = false
  // 提交进行中标记：AppButton loading 展示并拦截连点，防止重复提交写库接口（评审 #P0）
  @State submitting: boolean = false
  @State schoolList: Array<SchoolName> | null = null
  @State showSchoolList: boolean = false
  searchData: Record<string, Object> = {}
  checkStudentData: Record<string, Object> = {}
  private scroller: Scroller = new Scroller()
  // 学校搜索防抖（统一防抖工具，差异分析 01 §4.3）
  private searchDebouncer: Debouncer = new Debouncer()
  // 选中后延迟收起下拉防抖（对齐安卓 dismissJob：先让高亮落地再收焦点）
  private dismissDebouncer: Debouncer = new Debouncer()
  // 最近一次选中时刻：延迟收起窗口内忽略 onBlur，避免选中高亮未落地就被收起
  private lastSelectTime: number = 0
  // 搜索请求序号：响应返回时仅采纳序号等于当前值的最新请求结果（异步竞态防护，评审 #20）
  private searchSeq: number = 0

  aboutToAppear() {
    logger.debug('addSchool', '完善信息页挂载，拉取用户信息与学校列表')
    // 先拉一次当前用户信息：已填写学校/学号的进入只读态，防止本页被反复提交变更
    this.loadCurrentInfo()
    this.getSchoolName()
  }

  aboutToDisappear() {
    // 页面销毁前清理未触发的防抖定时器，避免页面销毁后仍回调（定时器硬规则）
    this.searchDebouncer.cancel()
    this.dismissDebouncer.cancel()
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
        // 只读态成因留痕（低频一次性状态，记 debug）
        logger.debug('addSchool', '学校与学号已完备，进入只读态')
      }
    } else {
      // 降级留痕：信息回填失败按未填写处理（不打断页面，不打用户数据）
      logger.warn('addSchool', `用户信息未生效，按未填写处理 code=${res.code} message=${res.message ?? ""}`)
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
    } else {
      // 学校搜索失败留痕（搜索关键字不落日志）
      logger.warn('addSchool', `学校搜索失败 code=${res.code} message=${res.message ?? ""}`)
    }
  }

  check() {
    this.checkSchoolId = this.schoolId === null
    this.checkStudentData['studentId'] = `${this.studentId}`
    this.checkStudentId = this.studentId === null || this.studentId === ""
  }

  /** 收焦点 + 收键盘（对齐安卓 focusManager.clearFocus() + keyboardController.hide()） */
  private clearFocusAndKeyboard(): void {
    try {
      this.getUIContext().getFocusController().clearFocus()
    } catch (err) {
      // 忽略不支持的设备
    }
  }

  /** 清空学校名（对齐安卓 trailingIcon clearOnClick） */
  private clearSchoolName(): void {
    this.schoolName = ""
    this.schoolId = null
    this.debounceSearch()
    focusControl.requestFocus("schoolNameInput")
  }

  /**
   * 选中学校：先让高亮落地，延迟 180ms 再收焦点收起下拉（对齐安卓 UpdateUserInfoPage.kt:271-278）。
   */
  private selectSchool(school: SchoolName): void {
    this.schoolId = school.schoolId
    this.schoolName = school.schoolName
    this.checkSchoolId = false
    this.lastSelectTime = Date.now()
    this.dismissDebouncer.debounce(() => {
      this.showSchoolList = false
      this.clearFocusAndKeyboard()
    }, SELECTION_SETTLE_MS)
  }

  /**
   * 提交学校/学号（P0 防重）：submitting 标记 + AppButton loading/禁用双保险，
   * 请求期间不可连点，避免写库接口重复提交（对齐安卓 UpdateUserInfoPage.kt:359-371）。
   */
  async submit() {
    // 信息锁定态禁止提交（对齐安卓 enabled = !isInfoLocked）
    if (this.isInfoLocked || this.submitting) {
      return
    }
    this.check()
    if (this.checkSchoolId || this.checkStudentId) {
      return
    }
    // 提交时收起键盘（对齐安卓 UpdateUserInfoPage.kt:365）
    this.clearFocusAndKeyboard()
    this.submitting = true
    try {
      let checkStudentRes = await getUserByStudentId(this.checkStudentData)
      // 学号查重（对齐决策 D6）：接口失败统一 toast 网络错误并阻止提交，
      // 既不像安卓失败误报「已注册」，也不像原实现失败放行导致绕过查重
      if (checkStudentRes.code !== HttpStatusCode.Ok) {
        // 学号查重接口失败留痕（学号不落日志）
        logger.warn('addSchool', `学号查重失败，阻止提交 code=${checkStudentRes.code} message=${checkStudentRes.message ?? ""}`)
        showToastSafety("网络异常，请稍后重试")
        return
      }
      // 后端 getUserByStudentId 返回 data = (该学号用户不存在)，true 才可提交（对齐安卓 checkStudentId）
      if (checkStudentRes.data !== true) {
        // 业务拒绝留痕：学号已被注册（学号不落日志）
        logger.warn('addSchool', '学号查重未通过（学号已注册），阻止提交')
        showToastSafety("该学号已注册")
        return
      }
      let res = await updateUserInfo({
        schoolId: this.schoolId,
        studentId: this.studentId
      })
      if (res.code === HttpStatusCode.Ok) {
        // 低频关键动作成功：完善学校信息留 info（仅记 schoolId 摘要）
        logger.info('addSchool', `学校信息保存成功 schoolId=${this.schoolId}`)
        showToastSafety("修改成功")
        if (pathStack.size() > 0) {
          pathStack.clear()
        }
        AppStorage.setOrCreate("userLoginFlag", true)
      } else {
        // 业务拒绝留痕：保存被拒绝（仅记 code/message 摘要）
        logger.warn('addSchool', `学校信息保存被拒绝 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "学校信息保存失败")
      }
    } finally {
      this.submitting = false
    }
  }

  build() {
    NavDestination() {
      // 修复（问题4）：原 Stack 内 layoutWeight 失效 + .align(Alignment.Bottom) 只作用于自身子组件，
      // 导致滚动内容收成内容高并居中、CTA 栏叠在表单中间。改 Column：Scroll 撑满余量，CTA 栏固定底部。
      Column() {
        // 滚动内容
        Scroll() {
          Column() {
            // ===== Hero 区（模板 C：品牌渐变卡 + 64dp 圆图标容器 + 标题 + 副标题）=====
            Column() {
              Column() {
                SymbolGlyph($r('sys.symbol.book_pages'))
                  .fontSize(28)
                  .fontColor([DesignColor.ON_PRIMARY])
              }
              .width(64)
              .height(64)
              .borderRadius(32)
              .backgroundColor(0x29FFFFFF)
              .border({ width: 1, color: 0x38FFFFFF })
              .justifyContent(FlexAlign.Center)
              .alignItems(HorizontalAlign.Center)

              Text("完善信息")
                .fontSize(DesignFontSize.STATUS_LARGE)
                .fontWeight(DesignFontWeight.SEMI_BOLD)
                .fontColor(DesignColor.ON_PRIMARY)
                .margin({ top: 14 })

              Text("请填写您的学校信息")
                .fontSize(DesignFontSize.BODY)
                .fontColor(DesignColor.ON_PRIMARY)
            }
            .width("100%")
            .padding({ top: 24, bottom: 20 })
            .borderRadius(DesignDimens.CARD_RADIUS)
            .linearGradient({
              angle: 135,
              colors: [[DesignColor.PRIMARY, 0], [DesignColor.PRIMARY_DARK, 1]]
            })
            .alignItems(HorizontalAlign.Center)
            .margin({ top: DesignDimens.SPACE_XXL })

            // ===== 表单卡（可见标签 + 输入行 + 错误文案）=====
            Column({ space: 16 }) {
              // 学校标签（12/400 textSecondary）
              Text("搜索学校")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)

              // 学校输入行（48dp / r14 / surfaceVariant 底 / 前缀 16 primary/.5 / 清空 48 热区）
              Row() {
                SymbolGlyph($r('sys.symbol.book_pages'))
                  .fontSize(16)
                  .fontColor([DesignColor.PRIMARY])
                  .opacity(0.5)

                TextInput({ text: this.schoolName, placeholder: "搜索学校" })
                  .type(InputType.Normal)
                  .fontSize(DesignFontSize.BODY)
                  .fontColor(DesignColor.TEXT_PRIMARY)
                  .placeholderColor(DesignColor.TEXT_SECONDARY)
                  .backgroundColor(Color.Transparent)
                  .layoutWeight(1)
                  .margin({ left: 12 })
                  .enabled(!this.isInfoLocked)
                  .key("schoolNameInput")
                  .onChange((it: string) => {
                    this.schoolName = it
                    this.debounceSearch()
                  })
                  .onFocus(() => {
                    this.showSchoolList = true
                  })
                  .onBlur(() => {
                    // 延迟收起窗口内不立即隐藏，保证选中高亮可见
                    if (Date.now() - this.lastSelectTime < SELECTION_SETTLE_MS) {
                      return
                    }
                    this.dismissDebouncer.debounce(() => {
                      this.showSchoolList = false
                    }, 60)
                  })

                // 清空钮（48dp 热区，图标 20）
                Column() {
                  SymbolGlyph($r('sys.symbol.xmark'))
                    .fontSize(20)
                    .fontColor([DesignColor.TEXT_SECONDARY])
                }
                .width(48)
                .height(48)
                .justifyContent(FlexAlign.Center)
                .alignItems(HorizontalAlign.Center)
                .enabled(!this.isInfoLocked)
                .onClick(() => {
                  this.clearSchoolName()
                })
              }
              .width("100%")
              .height(48)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .border({ width: 1, color: this.checkSchoolId ? DesignColor.ERROR : DesignColor.OUTLINE })
              .padding({ left: 12, right: 4 })
              .alignItems(VerticalAlign.Center)

              if (this.checkSchoolId) {
                Text("请选择学校")
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(DesignColor.ERROR)
                  .width("100%")
                  .margin({ top: 4 })
              }

              // 下拉列表（白卡 + 56dp 行 + 选中 primaryContainer 高亮 + 0.5 hairline）
              if (this.showSchoolList && this.schoolList !== null && this.schoolList.length > 0) {
                List({ scroller: this.scroller }) {
                  ForEach(this.schoolList, (school: SchoolName) => {
                    ListItem() {
                      Column() {
                        Row() {
                          Text(school.schoolName)
                            .fontSize(12)
                            .fontColor(this.schoolId === school.schoolId ? DesignColor.PRIMARY_DARK : DesignColor.TEXT_PRIMARY)
                            .layoutWeight(1)
                        }
                        .width("100%")
                        .height(56)
                        .padding({ left: 12, right: 12 })
                        .alignItems(VerticalAlign.Center)
                        .backgroundColor(this.schoolId === school.schoolId ? DesignColor.PRIMARY_CONTAINER : Color.Transparent)
                        .animation(MotionTiming.ENTER)
                        .onClick(() => {
                          this.selectSchool(school)
                        })

                        Divider()
                          .color(DesignColor.HAIRLINE)
                          .strokeWidth(0.5)
                          .margin({ left: 14, right: 14 })
                      }
                      .width("100%")
                    }
                  }, (school: SchoolName) => `${school.schoolId}`)
                }
                .width("100%")
                .height(Math.min(this.schoolList.length * 57, 200))
                .backgroundColor(DesignColor.SURFACE)
                .borderRadius(DesignDimens.CARD_RADIUS)
                .border({ width: 1, color: DesignColor.OUTLINE })
                .shadow({
                  radius: DesignElevation.CARD_SHADOW_RADIUS,
                  color: DesignColor.SHADOW_COLOR_CARD,
                  offsetX: 0,
                  offsetY: DesignElevation.CARD_SHADOW_OFFSET
                })
                .margin({ top: 8 })
              }

              // 学号标签
              Text("学号")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)

              // 学号输入行（48dp / r14 / 前缀 16 primary/.5）
              Row() {
                SymbolGlyph($r('sys.symbol.label'))
                  .fontSize(16)
                  .fontColor([DesignColor.PRIMARY])
                  .opacity(0.5)

                TextInput({ text: this.studentId, placeholder: "学号" })
                  .type(InputType.Number)
                  .maxLength(16)
                  .fontSize(DesignFontSize.BODY)
                  .fontColor(DesignColor.TEXT_PRIMARY)
                  .placeholderColor(DesignColor.TEXT_SECONDARY)
                  .backgroundColor(Color.Transparent)
                  .layoutWeight(1)
                  .margin({ left: 12 })
                  .enabled(!this.isInfoLocked)
                  .key("studentIdInput")
                  .onChange((it: string) => {
                    this.studentId = it
                  })
              }
              .width("100%")
              .height(48)
              .borderRadius(DesignDimens.INPUT_RADIUS)
              .backgroundColor(DesignColor.SURFACE_VARIANT)
              .border({ width: 1, color: this.checkStudentId ? DesignColor.ERROR : DesignColor.OUTLINE })
              .padding({ left: 12, right: 12 })
              .alignItems(VerticalAlign.Center)

              if (this.checkStudentId) {
                Text("请输入正确的学号")
                  .fontSize(DesignFontSize.CAPTION)
                  .fontColor(DesignColor.ERROR)
                  .width("100%")
                  .margin({ top: 4 })
              }
            }
            .width("100%")
            .padding(DesignDimens.CARD_PADDING)
            .backgroundColor(DesignColor.SURFACE)
            .borderRadius(DesignDimens.CARD_RADIUS)
            .border({ width: 1, color: DesignColor.OUTLINE })
            .shadow({
              radius: DesignElevation.CARD_SHADOW_RADIUS,
              color: DesignColor.SHADOW_COLOR_CARD,
              offsetX: 0,
              offsetY: DesignElevation.CARD_SHADOW_OFFSET
            })
            .alignItems(HorizontalAlign.Start)
            .margin({ top: DesignDimens.SPACE_MD })
          }
          .width("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: safeTopVp(), bottom: 24 })
          // 空白处点击收键盘（对齐安卓 UpdateUserInfoPage.kt:164-169 根 pressable clearFocus）
          .onClick(() => {
            this.clearFocusAndKeyboard()
          })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .width("100%")
        // 内容不足一屏时顶对齐（Scroll 默认居中，会把表单挤到屏幕中部）
        .align(Alignment.Top)

        // ===== 底部固定 CTA 栏（锁定提示常驻 + 主按钮 48 胶囊）=====
        Column() {
          Text("学校信息提交后不可自行修改，如需变更请联系管理员")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .width("100%")

          AppButton({
            text: "确认",
            loading: this.submitting,
            isEnabled: !this.isInfoLocked,
            click: () => {
              this.submit()
            }
          })
            .margin({ top: DesignDimens.SPACE_MD })
        }
        .width("100%")
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: 12, bottom: 16 + safeBottomVp() })
        .backgroundColor(DesignColor.BACKGROUND)
      }
      .width("100%")
      .height("100%")
      .backgroundColor(DesignColor.BACKGROUND)
    }
    .hideTitleBar(true)
  }
}
