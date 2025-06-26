import { logger } from "../../utils/Logger";
import { BaseResponse } from "../BaseResponse"
import { LoginUser } from "../entity/LoginUser";
import { RegisterUser } from "../entity/RegisterUser";
import { httpUtil } from "../HttpUtil"

async function getCaptcha(phoneNumber: string): Promise<BaseResponse<string>> {
  return httpUtil.get<string>(`/auth/user/captcha/${phoneNumber}`);
}

async function register(user: RegisterUser): Promise<BaseResponse<String>> {
  return httpUtil.post<string>("/auth/user/register", user)
}

async function login(user: LoginUser): Promise<BaseResponse<String>> {
  return httpUtil.post<string>("/auth/user/login", user)
}

//获取当前登录用户学校id
async function getUserSchoolId(): Promise<BaseResponse<number>> {
  return httpUtil.post<number>("/web/auth/user/getUserSchool")
}

export { getCaptcha, register, login }