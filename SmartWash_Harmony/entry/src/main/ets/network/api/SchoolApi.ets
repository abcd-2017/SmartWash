import { BaseResponse } from '../BaseResponse';
import { httpUtil } from '../HttpUtil';
import { SchoolName } from '../vo/SchoolName';

//获取所有学校信息
async function getAllSchool(param: Record<string, Object>): Promise<BaseResponse<Array<SchoolName>>> {
  return httpUtil.get<Array<SchoolName>>(`/web/schools/allName`, param);
}

export { getAllSchool }