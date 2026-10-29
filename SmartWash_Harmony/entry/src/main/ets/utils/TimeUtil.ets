export function formatDate(date: Date, format: string = 'yyyy-MM-dd HH:mm:ss'): string {
  const pad = (n: number) => n.toString().padStart(2, '0')

  return format
    .replace('yyyy', date.getFullYear().toString())
    .replace('MM', pad(date.getMonth() + 1))
    .replace('dd', pad(date.getDate()))
    .replace('HH', pad(date.getHours()))
    .replace('mm', pad(date.getMinutes()))
    .replace('ss', pad(date.getSeconds()))
}

/**
 * 订单时间相对化展示（对齐安卓 OrderPage.kt formatOrderTime，差异分析 02 §3.3）：
 * 同天「今天 HH:mm」、前一天「昨天 HH:mm」、其余仅「M月d日」（不带时分）；
 * createdAt 解析失败兜底返回原文。时区用系统默认。
 *
 * @param createdAt 后端 "yyyy-MM-dd HH:mm:ss" 格式时间串，可为 null
 */
export function formatOrderTime(createdAt: string | null | undefined): string {
  const raw = createdAt ?? ""
  if (raw.length < 10) {
    return raw
  }
  // 仅取日期段解析（yyyy-MM-dd），带 T0 点避免时区偏移到前一天
  const parsed = new Date(`${raw.substring(0, 10)}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  // 时间部分取 createdAt 第 11-16 位（"HH:mm"）
  const time = raw.length >= 16 ? raw.substring(11, 16) : ""
  const startOfDay = (d: Date): number => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const dayMs = 24 * 60 * 60 * 1000
  const diffDays = Math.round((startOfDay(new Date()) - startOfDay(parsed)) / dayMs)
  if (diffDays === 0 && time !== "") {
    return `今天 ${time}`
  }
  if (diffDays === 1 && time !== "") {
    return `昨天 ${time}`
  }
  return `${parsed.getMonth() + 1}月${parsed.getDate()}日`
}
