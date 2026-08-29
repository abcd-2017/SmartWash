// ESLint flat config（评审 #18）：错误预防 + Prettier 格式化双轨
// - @eslint/js recommended：no-undef / no-unused-vars 等基础质量规则
// - eslint-plugin-vue flat/essential：Vue3 错误预防类规则（不含风格类，格式交给 Prettier）
// - @vue/eslint-config-prettier：关闭与 Prettier 冲突的格式规则，并经 prettier/prettier 规则执行格式检查
//   （该包将 prettier/prettier 定为 warn：格式问题非功能性缺陷，由 lint:fix 统一消化）
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vuePrettierConfig from '@vue/eslint-config-prettier'
import globals from 'globals'

export default [
  {
    // 构建产物 / 依赖 / 生成文件不参与 lint
    ignores: ['**/dist/**', '**/node_modules/**', '**/coverage/**'],
  },

  // JS 基础质量规则
  js.configs.recommended,

  // Vue3 essential 规则集
  ...pluginVue.configs['flat/essential'],

  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.es2021,
      },
    },
    rules: {
      // 评审 #21：禁用遗留 console.log（warn），catch 分支的 error/warn 错误日志保留
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      // 未使用变量视为 error，下划线前缀变量/参数豁免（如占位回调参数）
      'no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'all' },
      ],
      // 禁止 == / != 宽松比较
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      // 布局与路由级视图组件沿用既有单词文件名（重命名会牵动路由引入与构建产物 chunk 命名），豁免；
      // 新增可复用组件仍须遵守多词命名
      'vue/multi-word-component-names': [
        'error',
        { ignores: ['Layout', 'Navbar', 'Sidebar', 'Home'] },
      ],
    },
  },

  // Prettier 兼容层必须最后加载：覆盖前面所有格式类规则
  vuePrettierConfig,
]
