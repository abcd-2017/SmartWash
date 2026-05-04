import './assets/main.css'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

import {
    createApp
} from 'vue'
import {
    createPinia
} from 'pinia'
import App from './App.vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import router from './router'
import 'element-plus/dist/index.css'

dayjs.locale('zh-cn')

const app = createApp(App)
app.use(ElementPlus, { locale: zhCn })
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.config.globalProperties.$dayjs = dayjs
app.use(createPinia())
app.use(router);

app.mount('#app')