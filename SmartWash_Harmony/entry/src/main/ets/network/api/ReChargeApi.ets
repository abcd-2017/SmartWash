import { BaseResponse } from '../BaseResponse';
import { UserRecharge } from '../entity/UserRecharge';
import { httpUtil } from '../HttpUtil';

//用户充值
async function userRecharge(param: UserRecharge): Promise<BaseResponse<string>> {
  return httpUtil.post<string>(`/web/auth/recharge/userRecharge`, param);
}

export { userRecharge }