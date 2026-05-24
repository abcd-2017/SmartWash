import { RechargeRecordVo } from './RechargeRecordVo';

export interface PageData {
  records: Array<RechargeRecordVo>
  total: number
  size: number
  current: number
}
