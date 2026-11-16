import { BaseResponse } from '../BaseResponse';
import { httpUtil } from '../HttpUtil';
import { SchoolName, schoolNameFromJSON } from '../vo/SchoolName';
import { mapArray } from '../vo/JsonParse';

async function getAllSchool(param: Record<string, Object>): Promise<BaseResponse<Array<SchoolName>>> {
  const raw: BaseResponse<Object> = await httpUtil.get<Object>(`/web/schools/allName`, param);
  const mapped: BaseResponse<Array<SchoolName>> = {
    code: raw.code,
    message: raw.message,
    data: mapArray(raw.data, (item: Object): SchoolName => schoolNameFromJSON(item))
  }
  return mapped;
}

export { getAllSchool }