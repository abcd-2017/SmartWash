import {
  fileURLToPath,
  URL
} from 'node:url'

import {
  defineConfig
} from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
// Element Plus 按需引入（评审 #9）：模板组件自动注册 + API/指令自动补齐导入
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // 模板中的 el-* 组件与 v-loading 等指令按需引入并注入对应样式
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false, // 纯 JS 项目，不生成 .d.ts
    }),
    // 脚本中未显式 import 的 ElMessage/ElMessageBox 等 API 自动按需引入（含样式）
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: false,
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src',
        import.meta.url))
    },
  },
  server: {
    port: 5000,
    host: "0.0.0.0",
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  test: {
    environment: 'happy-dom'
  }
})
