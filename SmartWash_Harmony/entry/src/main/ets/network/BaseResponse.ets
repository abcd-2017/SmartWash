export interface BaseResponse<T> {
  data: T | null,
  code?: number,
  message?: string
}