// src/composables/useConfirm.js
// 统一确认弹窗工具（评审 #23）：消除散落各页的 `error !== 'cancel'` 字符串判断。
// 约定：用户点"取消"或右上角关闭 → 静默返回 false；点"确认" → 返回 true。
// 弹窗之外的业务异常（如删除接口报错）仍由调用方 try/catch 自行提示。
import { ElMessageBox } from 'element-plus'

/**
 * @param {String} message 提示内容
 * @param {String} [title='警告'] 弹窗标题
 * @param {Object} [options] 透传给 ElMessageBox.confirm 的其余配置
 * @returns {Promise<Boolean>} 是否确认
 */
export function useConfirm(message, title = '警告', options = {}) {
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
    ...options,
  }).then(
    () => true,
    // 取消/关闭均视为放弃操作，静默处理；其余异常原样抛出
    (error) => {
      if (error === 'cancel' || error === 'close') {
        return false
      }
      throw error
    }
  )
}
