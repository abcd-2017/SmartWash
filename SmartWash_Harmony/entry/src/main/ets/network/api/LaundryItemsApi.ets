import { BaseResponse } from "../BaseResponse";
import { LaundryItem } from "../vo/LaundryItem";
import { httpUtil } from "../HttpUtil";

async function getLaundryItems(): Promise<BaseResponse<Array<LaundryItem>>> {
  return httpUtil.get<Array<LaundryItem>>(`/web/laundryItems/all`);
}

export { getLaundryItems }