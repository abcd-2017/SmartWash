// src/utils/download.js
// 浏览器端 blob 落盘：创建临时 <a> 触发下载后释放对象 URL，导出类功能共用
//（首次消费方：充值记录导出；与 delivery 公开页内联实现等价，后续新导出统一走这里）

/**
 * 将二进制内容保存为本地文件
 * @param {Blob} blob 文件内容
 * @param {String} filename 下载展示的文件名
 */
export function saveBlobAs(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  // 延迟释放：浏览器异步接管下载流，立即 revoke 可能中断尚未开始的下载
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}
