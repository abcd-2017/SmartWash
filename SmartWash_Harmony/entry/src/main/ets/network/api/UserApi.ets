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

export { getCaptcha, register }