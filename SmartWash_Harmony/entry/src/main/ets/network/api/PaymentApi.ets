import { BaseResponse } from '../BaseResponse';
import { OrderPayment } from '../entity/OrderPayment';
import { httpUtil } from '../HttpUtil';

async function payment(payment: OrderPayment): Promise<BaseResponse<string>> {
  return httpUtil.post<string>(`/web/auth/payments/payment`, payment);
}

export { payment }