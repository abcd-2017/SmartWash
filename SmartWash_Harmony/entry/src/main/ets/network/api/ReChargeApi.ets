import { BaseResponse } from '../BaseResponse';
import { UserRecharge } from '../entity/UserRecharge';
import { PageData, pageDataFromJSON } from '../vo/PageData';
import { httpUtil } from '../HttpUtil';

async function userRecharge(param: UserRecharge): Promise<BaseResponse<string>> {
  return httpUtil.post<string>(`/web/auth/recharge/userRecharge`, param);
}

async function getRechargeRecordList(param: Record<string, Object>): Promise<BaseResponse<PageData>> {
  const raw: BaseResponse<Object> = await httpUtil.get<Object>(`/web/auth/recharge/list`, param);
  const mapped: BaseResponse<PageData> = {
    code: raw.code,
    message: raw.message,
    data: pageDataFromJSON(raw.data)
  }
  return mapped;
}

export { userRecharge, getRechargeRecordList }