import './assets/main.css'
import dayjs from 'dayjs'

import {
    createApp
} from 'vue'
import {
    createPinia
} from 'pinia'
import App from './App.vue'
import ElementPlus from 'element-plus';
import router from './router';
import 'element-plus/dist/index.css';



const app = createApp(App)
app.use(ElementPlus);
app.config.globalProperties.$dayjs = dayjs
app.use(createPinia())
app.use(router);

app.mount('#app')