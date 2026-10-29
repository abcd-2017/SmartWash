/**
 * 卦盘页路由入参（对齐安卓 DivChart/{recordId}?animate={animate}）。
 * 鸿蒙 pushPathByName 只接受单个 param 对象，故 recordId / animate 打包为对象经 getObjectParam 读取。
 * recordId 必填；animate 默认 false（复盘入口不播动爻进场动画）。
 */
export interface DivChartParam {
  recordId: number
  animate: boolean
}
