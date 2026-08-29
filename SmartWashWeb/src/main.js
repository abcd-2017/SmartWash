import './assets/main.css';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';

import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';

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

const app = createApp(App);
app.config.globalProperties.$dayjs = dayjs;
app.use(createPinia());
app.use(router);

app.mount('#app');
