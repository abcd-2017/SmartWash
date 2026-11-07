import { BaseResponse } from "../BaseResponse"
import { ChangePasswordForm } from "../entity/ChangePasswordForm";
import { LoginUser } from "../entity/LoginUser";
import { RegisterUser } from "../entity/RegisterUser";
import { ResetPasswordForm } from "../entity/ResetPasswordForm";
import { UpdateUserInfo } from "../entity/UpdateUserInfo";
import { httpUtil } from "../HttpUtil"
import { LoginVo, loginVoFromJSON } from "../vo/LoginVo";
import { TransactionPageData, transactionPageFromJSON } from "../vo/TransactionVo";
import { UserInfoVo } from "../vo/UserInfoVo";
import { FormData, Type } from "@ohos/axios"
import { fileIo as fs } from "@kit.CoreFileKit"

//获取验证码
async function getCaptcha(phoneNumber: string): Promise<BaseResponse<string>> {
  return httpUtil.get<string>(`/auth/user/captcha/${phoneNumber}`);
}

//忘记密码-重置密码（公开接口，凭手机号 + 验证码 + 新密码重置；成功 Result<String>）
async function resetPassword(form: ResetPasswordForm): Promise<BaseResponse<string>> {
  return httpUtil.post<string>("/auth/user/resetPassword", form)
}

//注册（后端注册即自动登录：信封 data 为 {token, refreshToken, role} 对象，与登录 LoginVo 同构）
async function register(user: RegisterUser): Promise<BaseResponse<LoginVo>> {
  const raw: BaseResponse<Object> = await httpUtil.post<Object>("/auth/user/register", user)
  const mapped: BaseResponse<LoginVo> = {
    code: raw.code,
    message: raw.message,
    data: loginVoFromJSON(raw.data)
  }
  return mapped
}

//登录（后端信封 data 为 {token, refreshToken, role} 对象，不再直接是 token 字符串）
async function login(user: LoginUser): Promise<BaseResponse<LoginVo>> {
  const raw: BaseResponse<Object> = await httpUtil.post<Object>("/auth/user/login", user)
  const mapped: BaseResponse<LoginVo> = {
    code: raw.code,
    message: raw.message,
    data: loginVoFromJSON(raw.data)
  }
  return mapped
}

//获取当前登录用户学校id
async function getUserSchoolId(): Promise<BaseResponse<number>> {
  return httpUtil.get<number>("/web/auth/user/school")
}

//完善用户信息
async function updateUserInfo(userInfo: UpdateUserInfo): Promise<BaseResponse<string>> {
  return httpUtil.post<string>("/web/auth/user/updateUserInfo", userInfo)
}

//获取用户详细信息
async function getUserInfo(): Promise<BaseResponse<UserInfoVo>> {
  return httpUtil.get<UserInfoVo>("/web/auth/user/getUserInfo")
}

//获取钱包流水（充值+消费合并分页；page 从 1 起，pageSize 上限 50）
async function getTransactions(page: number, pageSize: number): Promise<BaseResponse<TransactionPageData>> {
  // ArkTS 禁止裸对象字面量作参数（arkts-no-untyped-obj-literals），查询参数用 Record 并按索引赋值
  const params: Record<string, Object> = {}
  params['page'] = page
  params['pageSize'] = pageSize
  const raw: BaseResponse<Object> = await httpUtil.get<Object>("/web/auth/user/transactions", params)
  const mapped: BaseResponse<TransactionPageData> = {
    code: raw.code,
    message: raw.message,
    data: transactionPageFromJSON(raw.data)
  }
  return mapped
}

//修改密码（登录态接口：原密码错误返回业务失败信封 message「原密码不正确」，成功 Result<String>「密码修改成功」）
async function changePassword(form: ChangePasswordForm): Promise<BaseResponse<string>> {
  return httpUtil.put<string>("/web/auth/user/password", form)
}

//判断当前学号是否已经存在
async function getUserByStudentId(params: Record<string, Object>): Promise<BaseResponse<boolean>> {
  return httpUtil.get<boolean>("/web/auth/user/getUserByStudentId", params)
}

//绑定校园卡
// 注：历史 typo `bingCampus` 已四端统一为 `bindCampus`（后端/Android/鸿蒙同步修正）。
async function bindCampus(campusCard: string): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/user/bindCampus/${campusCard}`)
}

//解绑校园卡
async function unBindCampus(): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>("/web/auth/user/unBindCampus")
}

//上传头像
/**
 * 适配 @ohos/axios 的 FormData 契约（ArkTS 无浏览器 DOM FormData，原写法 new FormData() 无法编译）：
 * - 文件内容支持 internal://cache/ URI 或 ArrayBuffer，相册选择器返回的 file://media/... 均不支持；
 * - 故将所选图片读为 ArrayBuffer 后附加，经 option 指定文件名与 MIME（Type 接口）。
 */
async function uploadAvatar(uri: string): Promise<BaseResponse<string>> {
  const formData: FormData = new FormData()
  const stat = fs.statSync(uri)
  const file = fs.openSync(uri, fs.OpenMode.READ_ONLY)
  try {
    const buffer = new ArrayBuffer(stat.size)
    fs.readSync(file.fd, buffer)
    const option: Type = { filename: "avatar.jpg", type: "image/jpeg" }
    formData.append("file", buffer, option)
  } finally {
    // 文件句柄必须释放，避免泄漏
    fs.closeSync(file)
  }
  return httpUtil.upload<string>("/web/auth/user/avatar", formData)
}

export { getCaptcha,
  resetPassword,
  register,
  login,
  getUserSchoolId,
  updateUserInfo,
  getUserInfo,
  getTransactions,
  changePassword,
  getUserByStudentId,
  bindCampus,
  unBindCampus,
  uploadAvatar }