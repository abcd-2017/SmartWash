/**
 * 摇卦页路由入参（对齐安卓 DivCast?question={question}&category={category}）。
 * 鸿蒙 pushPathByName 只接受单个 param 对象，故 question/category 打包为对象经 getObjectParam 读取。
 */
export interface DivCastParam {
  question: string
  category: string
}
