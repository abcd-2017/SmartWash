import { SchoolVo } from "./SchoolVo"

export class UserInfoVo {
  phoneNumber: string | null = null
  studentId: string | null = null
  campusCard: string | null = null
  balance: number | null = null
  avatar: string | null = null
  schoolVo: SchoolVo | null = null
  // 数据卡计数（对齐安卓 UserInfoVo.kt:21-22；后端 UserInfoVo 当前未下发，缺省为 0）
  couponCount: number | null = null
  orderCount: number | null = null
}