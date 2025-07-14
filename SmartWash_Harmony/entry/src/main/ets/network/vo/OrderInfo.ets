import { LaundryItem } from "./LaundryItem"
import { LockerVo } from "./LockerVo"
import { SchoolVo } from "./SchoolVo"
import { UserInfoVo } from "./UserInfoVo"

export class OrderInfo {
  orderId: number | null = null
  userVo: UserInfoVo | null = null
  schoolsVo: SchoolVo | null = null
  lockersVo: LockerVo | null = null
  orderNo: string | null = null
  laundryPackageVo: LaundryItem | null = null
  totalPrice: number | null = null
  payPrice: number | null = null
  status: string | null = null
  pickupCode: string | null = null
  createdAt: string | null = null
  updatedAt: string | null = null
}