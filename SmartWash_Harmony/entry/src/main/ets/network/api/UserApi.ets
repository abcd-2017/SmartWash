import { BaseResponse } from "../BaseResponse"
import { LoginUser } from "../entity/LoginUser";
import { RegisterUser } from "../entity/RegisterUser";
import { UpdateUserInfo } from "../entity/UpdateUserInfo";
import { httpUtil } from "../HttpUtil"
import { LoginVo } from "../vo/LoginVo";
import { UserInfoVo } from "../vo/UserInfoVo";

//获取验证码
async function getCaptcha(phoneNumber: string): Promise<BaseResponse<string>> {
  return httpUtil.get<string>(`/auth/user/captcha/${phoneNumber}`);
}

//注册
async function register(user: RegisterUser): Promise<BaseResponse<String>> {
  return httpUtil.post<string>("/auth/user/register", user)
}

//登录（后端信封 data 为 {token, role} 对象，不再直接是 token 字符串）
async function login(user: LoginUser): Promise<BaseResponse<LoginVo>> {
  return httpUtil.post<LoginVo>("/auth/user/login", user)
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

//判断当前学号是否已经存在
async function getUserByStudentId(params: Record<string, Object>): Promise<BaseResponse<boolean>> {
  return httpUtil.get<boolean>("/web/auth/user/getUserByStudentId", params)
}

//绑定校园卡
async function bindCampus(campusCard: string): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>(`/web/auth/user/bingCampus/${campusCard}`)
}

//解绑校园卡
async function unBindCampus(): Promise<BaseResponse<boolean>> {
  return httpUtil.post<boolean>("/web/auth/user/unBingCampus")
}

//上传头像
async function uploadAvatar(uri: string): Promise<BaseResponse<string>> {
  let formData = new FormData()
  formData.append("file", { uri: uri, name: "avatar.jpg", type: "image/jpeg" })
  return httpUtil.upload<string>("/web/auth/user/avatar", formData)
}

export { getCaptcha,
  register,
  login,
  getUserSchoolId,
  updateUserInfo,
  getUserInfo,
  getUserByStudentId,
  bindCampus,
  unBindCampus,
  uploadAvatar }