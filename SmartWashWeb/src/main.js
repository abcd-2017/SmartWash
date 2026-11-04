import './assets/main.css';
import './assets/pages.css';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';

import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import logger from '@/utils/logger';

// Element Plus 按需引入（评审 #9）：
// - 组件与指令由 unplugin-vue-components 在模板中按需注册（见 vite.config.js），
//   全量 app.use(ElementPlus) 与全量图标注册已移除；
// - 此处仅补齐「脚本中直接调用」的组件样式（Message/MessageBox）与 v-loading 指令样式兜底，
//   避免按需模式下样式缺失；
// - 中文语言包改由 App.vue 的 el-config-provider 下发。
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';
import 'element-plus/es/components/loading/style/css';

dayjs.locale('zh-cn');

// 非 Error 的 Promise 拒绝 reason 摘要：只取 message/类型/键名等低敏信息并截断，
// 不做整体 JSON 序列化，避免把（可能含完整报文的）对象全量打进日志
const REASON_SUMMARY_MAX = 300;
function summarizeReason(reason) {
  if (reason == null) {
    return String(reason);
  }
  if (typeof reason === 'string') {
    return reason.slice(0, REASON_SUMMARY_MAX);
  }
  if (typeof reason === 'object') {
    if (typeof reason.message === 'string') {
      return reason.message.slice(0, REASON_SUMMARY_MAX);
    }
    let keys = [];
    try {
      keys = Object.keys(reason);
    } catch {
      // 不可枚举对象退化为类型名
    }
    return `${reason.constructor?.name || 'object'}(keys: ${keys.slice(0, 10).join(',')})`;
  }
  return String(reason);
}

const app = createApp(App);
app.config.globalProperties.$dayjs = dayjs;

// 全局兜底一：渲染层异常（组件 setup/渲染/生命周期/watch 等未被页面捕获的异常）。
// 输出：组件名 + Vue 错误阶段(info) + 原始 Error（logger 内只保留 name/message/stack，
// message 中手机号自动打码）。接管后 Vue 不再默认向控制台打印，统一由 logger.error 输出，
// 此处不再向上抛出，避免控制台重复报错。
app.config.errorHandler = (err, instance, info) => {
  const componentName = instance?.$options?.name || instance?.$options?.__name || '未知组件';
  logger.error(`[Vue errorHandler] 组件:${componentName} 阶段:${info}`, err);
};

// 全局兜底二：Promise 未捕获拒绝。
// Error 类型输出原始 reason（保留堆栈），其余输出低敏摘要；preventDefault 阻止浏览器
// 再打印一遍默认的 "Uncaught (in promise)"，避免与 logger.error 重复。
window.addEventListener('unhandledrejection', (event) => {
  const reason = event.reason;
  logger.error('[unhandledrejection]', reason instanceof Error ? reason : summarizeReason(reason));
  event.preventDefault();
});

app.use(createPinia());
app.use(router);

app.mount('#app');
