/**
 * 滚筒插画 — 120vp Canvas，逐属性复刻安卓 OrderDetailPage.kt DrumIllustration（D-OD8）。
 *
 * 设计稿 120×120 坐标直绘：unit = sizeValue / 120，全部尺寸按 unit 等比缩放。
 * 机身 roundRect(10,10,100×100, rx26) + 2.6 描边；顶线 (10,40)-(110,40)；
 * 控制面板三点 r4；滚筒外圆 r30 + 2.6 描边、内圆 r21、中心轴 r7、水位波线 w2.2、高光两点。
 *
 * 主题：鸿蒙当前固定亮色（G6：Dark* 令牌已备但未开放跟随系统），故此处取亮色值；
 * 品牌绿 #2D9B6A 与白色高光两主题不变。无位图、无动画（静态复刻，动效由首页滚筒承担）。
 */
@ComponentV2
export struct DrumIllustration {
  @Param sizeValue: number = 120
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true))

  aboutToAppear(): void {
    this.draw()
  }

  private draw(): void {
    const unit: number = this.sizeValue / 120

    // 亮色令牌（对齐安卓 OrderDetailPage.kt:551-559）
    const bodyFill: string = '#FFFFFF'
    const topLine: string = '#E8F6EF'
    const dot1: string = '#2D9B6A'
    const dot2: string = '#8FD4B4'
    const dot3: string = '#C9E8D8'
    const drumFill: string = '#F0FAF5'
    const drumInner: string = '#D8F0E4'
    const brand: string = '#2D9B6A'
    const highlight: string = '#FFFFFF'

    this.ctx.clearRect(0, 0, this.sizeValue, this.sizeValue)
    this.ctx.lineCap = 'round'

    // 机身 roundRect(10,10,100×100, rx26)：fill + 2.6 描边
    this.ctx.fillStyle = bodyFill
    this.ctx.beginPath()
    this.ctx.roundRect(10 * unit, 10 * unit, 100 * unit, 100 * unit, 26 * unit)
    this.ctx.fill()
    this.ctx.strokeStyle = brand
    this.ctx.lineWidth = 2.6 * unit
    this.ctx.beginPath()
    this.ctx.roundRect(10 * unit, 10 * unit, 100 * unit, 100 * unit, 26 * unit)
    this.ctx.stroke()

    // 顶线 (10,40)-(110,40) w2
    this.ctx.strokeStyle = topLine
    this.ctx.lineWidth = 2 * unit
    this.ctx.beginPath()
    this.ctx.moveTo(10 * unit, 40 * unit)
    this.ctx.lineTo(110 * unit, 40 * unit)
    this.ctx.stroke()

    // 控制面板三点 r4
    this.ctx.fillStyle = dot1
    this.ctx.beginPath()
    this.ctx.arc(28 * unit, 25 * unit, 4 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()
    this.ctx.fillStyle = dot2
    this.ctx.beginPath()
    this.ctx.arc(42 * unit, 25 * unit, 4 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()
    this.ctx.fillStyle = dot3
    this.ctx.beginPath()
    this.ctx.arc(56 * unit, 25 * unit, 4 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()

    // 滚筒外圆 r30 fill + 2.6 描边
    this.ctx.fillStyle = drumFill
    this.ctx.beginPath()
    this.ctx.arc(60 * unit, 76 * unit, 30 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()
    this.ctx.strokeStyle = brand
    this.ctx.lineWidth = 2.6 * unit
    this.ctx.beginPath()
    this.ctx.arc(60 * unit, 76 * unit, 30 * unit, 0, Math.PI * 2, false)
    this.ctx.stroke()

    // 滚筒内圆 r21
    this.ctx.fillStyle = drumInner
    this.ctx.beginPath()
    this.ctx.arc(60 * unit, 76 * unit, 21 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()

    // 中心轴 r7
    this.ctx.fillStyle = brand
    this.ctx.beginPath()
    this.ctx.arc(60 * unit, 76 * unit, 7 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()

    // 水位波线 w2.2 round
    this.ctx.strokeStyle = brand
    this.ctx.lineWidth = 2.2 * unit
    this.ctx.beginPath()
    this.ctx.moveTo(42 * unit, 76 * unit)
    this.ctx.bezierCurveTo(
      46 * unit, 71.5 * unit,
      50 * unit, 78.5 * unit,
      54 * unit, 75.5 * unit
    )
    this.ctx.bezierCurveTo(
      58 * unit, 72.5 * unit,
      62 * unit, 77 * unit,
      66 * unit, 74 * unit
    )
    this.ctx.stroke()

    // 高光两点
    this.ctx.fillStyle = highlight
    this.ctx.beginPath()
    this.ctx.arc(51 * unit, 66 * unit, 2.6 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()
    this.ctx.beginPath()
    this.ctx.arc(70 * unit, 87 * unit, 2.2 * unit, 0, Math.PI * 2, false)
    this.ctx.fill()
  }

  build() {
    Canvas(this.ctx)
      .width(this.sizeValue)
      .height(this.sizeValue)
  }
}
