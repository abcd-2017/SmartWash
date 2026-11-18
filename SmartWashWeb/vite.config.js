import {
  fileURLToPath,
  URL
} from 'node:url'

import {
  defineConfig
} from 'vite'
import vue from '@vitejs/plugin-vue'
// Element Plus 按需引入（评审 #9）：模板组件自动注册 + API/指令自动补齐导入
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 统一后端地址（与 Android 端对齐）：通过环境变量注入，无兜底——未传则构建报错，禁止明文 IP 入库。
// 本地开发：npm run dev 走 Vite 代理，不依赖此值。
// 生产构建：SMART_WASH_BASE_URL=https://your-domain.com/ npm run build
const smartwashBaseUrl = process.env.SMART_WASH_BASE_URL || ''

// https://vite.dev/config/
// vite-plugin-vue-devtools 的依赖 @vue/devtools-kit 在模块顶层读取 localStorage，
// Node（vitest 加载本配置文件）下不存在该 API 会直接崩；改为 vitest 场景外动态引入
export default defineConfig(async () => {
  const plugins = [
    vue(),
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
  ]
  if (!process.env.VITEST) {
    // devtools 插件依赖 @vue/devtools-kit，其在模块顶层读取 localStorage；
    // Node 22+/25 未带 --localstorage-file 时全局 localStorage 是残缺实现
    // （getItem 等方法缺失），模块加载即崩。能力探测 + try/catch 双保险：
    // 环境可用才加载，且任何加载异常都不阻断 dev/build 配置解析。
    try {
      const lsOk =
        typeof localStorage !== 'undefined' &&
        typeof localStorage.getItem === 'function' &&
        typeof localStorage.setItem === 'function'
      if (lsOk) {
        const { default: vueDevTools } = await import('vite-plugin-vue-devtools')
        plugins.push(vueDevTools())
      }
    } catch {
      // devtools 仅为开发辅助，跳过即可
    }
  }

  return {
    define: {
      __SMART_WASH_BASE_URL__: JSON.stringify(smartwashBaseUrl),
    },
    plugins,
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
      environment: 'happy-dom',
      // element-plus 冷加载约 5s（按需引入 + 样式内联），避免 import('@/utils/http') 触发
      // 首次 element-plus 加载时撞上默认 5s 超时（既有性能特征，非断言失败）
      testTimeout: 20000,
      // Node 22 实验性全局 localStorage 会遮蔽 happy-dom 注入，测试统一兜底内存版 stub
      setupFiles: ['src/__tests__/setup.dom.js'],
      server: {
        deps: {
          // Element Plus 按需引入会注入 .css，内联后由 vitest 忽略样式，
          // 避免 SSR 加载器报 "Unknown file extension .css"
          inline: ['element-plus'],
        },
      },
    }
  }
})
