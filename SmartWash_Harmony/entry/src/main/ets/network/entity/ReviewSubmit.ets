/**
 * 订单评价提交请求（对齐后端 POST /web/auth/reviews/add 的 AddReviewFrom）。
 */
export class ReviewSubmit {
  orderId: number | null = null
  rating: number | null = null
  content: string | null = null
}
