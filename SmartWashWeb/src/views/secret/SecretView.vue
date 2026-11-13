<template>
  <!-- 阅后即焚公开查看页：视口居中单卡片，四态流转 -->
  <div class="secret-page">
    <!-- 视图一：无码访问 /s 时先输码（码大小写敏感，不做大写转换） -->
    <div v-if="view === 'entering'" class="secret-card">
      <div class="secret-brand">
        <img src="@/assets/logo.svg" alt="SmartWash" class="secret-logo" />
        <span class="secret-name">SmartWash 阅后即焚</span>
      </div>
      <h1 class="secret-title">输入私密码</h1>
      <p class="secret-sub">请输入发送方给你的私密码（区分大小写）</p>
      <el-form class="secret-form" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="inputCode"
            class="code-input"
            placeholder="如 sK4x9Qm"
            maxlength="16"
            size="large"
            @keyup.enter="handleLookup"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="enterError"
        class="secret-alert"
        :title="enterError"
        :type="enterErrorType"
        :closable="false"
        show-icon
      />
      <el-button
        type="primary"
        size="large"
        class="secret-btn"
        :loading="loading"
        :disabled="!inputCode.trim()"
        @click="handleLookup"
      >
        查看消息
      </el-button>
      <p class="secret-tip">此类消息仅可查看一次，查看后即焚毁</p>
    </div>

    <!-- 视图二：待揭示（meta 成功后；含可选口令） -->
    <div v-else-if="view === 'ready'" class="secret-card">
      <div class="secret-brand">
        <img src="@/assets/logo.svg" alt="SmartWash" class="secret-logo" />
        <span class="secret-name">SmartWash 阅后即焚</span>
      </div>
      <h1 class="secret-title">有人给你发了一条阅后即焚消息</h1>
      <p class="secret-sub">内容仅可查看一次，点击「揭示」后立即焚毁、无法找回</p>

      <el-form v-if="requiresPassword" class="secret-form" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="inputPassword"
            type="password"
            placeholder="请输入访问口令"
            size="large"
            show-password
            @keyup.enter="handleReveal"
          >
            <template #prefix
              ><el-icon><Lock /></el-icon
            ></template>
          </el-input>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="revealError"
        class="secret-alert"
        :title="revealError"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-button
        type="primary"
        size="large"
        class="secret-btn"
        :loading="revealing"
        :disabled="requiresPassword && inputPassword.length < 4"
        @click="handleReveal"
      >
        揭示内容
      </el-button>
      <p class="secret-tip">到期未领取也会自动焚毁</p>
    </div>

    <!-- 视图三：已揭示（明文一次性展示 + 复制） -->
    <div v-else-if="view === 'revealed'" class="secret-card">
      <div class="secret-brand">
        <img src="@/assets/logo.svg" alt="SmartWash" class="secret-logo" />
        <span class="secret-name">SmartWash 阅后即焚</span>
      </div>
      <el-alert
        class="secret-alert"
        title="内容已焚毁：离开或刷新本页后无法再次查看，请立即保存"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="content-box">{{ revealedContent }}</div>
      <el-button type="primary" size="large" class="secret-btn" @click="handleCopy">
        复制内容
      </el-button>
      <p class="secret-tip">本链接已失效，关闭页面即可</p>
    </div>

    <!-- 视图四：终态（不存在/已焚毁/已过期/口令锁定） -->
    <div v-else class="secret-card">
      <el-icon class="gone-icon" :size="44"><CircleClose /></el-icon>
      <h1 class="secret-title">{{ goneTitle }}</h1>
      <p class="secret-sub">{{ goneSub }}</p>
      <el-button v-if="allowRetry" type="primary" @click="handleBackToEnter">
        重新输入私密码
      </el-button>
      <p v-else class="secret-tip">私密文本经服务器加密存储，焚毁后服务端也不再持有明文</p>
    </div>
  </div>
</template>

<script setup>
// 阅后即焚公开查看页（路由 /s/:code，免登录匿名访问）。
// 四态：entering（输码）→ ready（待揭示，含可选口令）→ revealed（明文一次性展示）→ gone（终态）。
// 安全基线：
// 1. 揭示必须用户点击（POST view），meta 只读不焚毁——防链接预览爬虫提前烧毁；
// 2. 口令错误 403 不焚毁，可重试；410 为终态（已焚毁/已过期）；
// 3. 明文纯文本渲染（禁 v-html），页面隐藏/卸载即清内存。
import { onMounted, onUnmounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Lock, CircleClose } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import { SECRET_CODE, getSecretMeta, viewSecret } from '@/api/secret';
import logger from '@/utils/logger';

const route = useRoute();
const router = useRouter();

const view = ref('entering');
const routeCode = ref('');
const inputCode = ref('');
const inputPassword = ref('');
const requiresPassword = ref(false);
const loading = ref(false);
const revealing = ref(false);
const enterError = ref('');
const enterErrorType = ref('error');
const revealError = ref('');
const revealedContent = ref('');
const goneTitle = ref('');
const goneSub = ref('');
/** 终态是否允许回到输码（链接直进的失效态不允许，避免误导重试） */
const allowRetry = ref(false);

onMounted(() => {
  const code = String(route.params.code || '').trim();
  if (code) {
    routeCode.value = code;
    doLookup(code);
  }
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  clearPlaintext();
});

// 页面隐藏即清明文（一次性展示语义：离开即视为已读）
function handleVisibilityChange() {
  if (document.hidden && view.value === 'revealed') {
    clearPlaintext();
    view.value = 'gone';
    goneTitle.value = '内容已销毁';
    goneSub.value = '离开页面后明文已从内存清除，服务端亦已焚毁，无法再次查看';
    allowRetry.value = false;
  }
}

function clearPlaintext() {
  revealedContent.value = '';
  inputPassword.value = '';
}

// 查询元信息：404/410 直接进终态；200 进待揭示态
async function doLookup(code) {
  loading.value = true;
  enterError.value = '';
  try {
    const envelope = await getSecretMeta(code);
    if (envelope.code === SECRET_CODE.OK) {
      requiresPassword.value = Boolean(envelope.data?.requiresPassword);
      view.value = 'ready';
      return;
    }
    enterGone(envelope, code);
  } catch (error) {
    logger.error('私密文本元信息获取网络异常:', error);
    enterError.value = '网络异常，请稍后重试';
    enterErrorType.value = 'error';
  } finally {
    loading.value = false;
  }
}

function enterGone(envelope, code) {
  if (envelope.code === SECRET_CODE.NOT_FOUND) {
    goneTitle.value = '私密文本不存在';
    goneSub.value = '链接不完整、私密码有误，或该消息从未存在';
  } else if (envelope.code === SECRET_CODE.GONE) {
    goneTitle.value = '内容已不可查看';
    goneSub.value = envelope.message || '私密文本已焚毁或已过期，内容无法恢复';
  } else {
    goneTitle.value = '无法查看';
    goneSub.value = envelope.message || '服务暂不可用，请稍后重试';
  }
  allowRetry.value = true;
  view.value = 'gone';
  logger.warn('私密文本查看被拒：', `code=${envelope.code}`, `输入码=${code ? '已提供' : '空'}`);
}

// 手动输码：地址补码后走同一查询流程
function handleLookup() {
  const code = inputCode.value.trim();
  if (loading.value || !code) return;
  routeCode.value = code;
  router.replace(`/s/${encodeURIComponent(code)}`);
  doLookup(code);
}

// 揭示：POST 触发焚毁；403 可重试（未焚毁），410 进终态
async function handleReveal() {
  if (revealing.value) return;
  revealing.value = true;
  revealError.value = '';
  try {
    const envelope = await viewSecret(routeCode.value, requiresPassword.value ? inputPassword.value : '');
    if (envelope.code === SECRET_CODE.OK) {
      revealedContent.value = envelope.data?.content || '';
      view.value = 'revealed';
      // 关键动作成功日志：绝不记录明文与口令，只记状态
      logger.info('私密文本揭示成功，内容已焚毁');
      return;
    }
    if (envelope.code === SECRET_CODE.WRONG_PASSWORD) {
      logger.warn('私密文本揭示被拒：口令错误（403，未焚毁）');
      revealError.value = '口令错误，请重试（内容未被焚毁）';
      return;
    }
    enterGone(envelope, routeCode.value);
  } catch (error) {
    logger.error('私密文本揭示网络异常:', error);
    revealError.value = '网络异常，请稍后重试';
  } finally {
    revealing.value = false;
  }
}

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(revealedContent.value);
    ElMessage.success('已复制，请立即保存');
  } catch (error) {
    // 剪贴板 API 不可用（非安全上下文/权限拒绝）：降级为选区提示
    logger.warn('剪贴板复制失败:', error);
    ElMessage.warning('复制失败，请手动选择文本复制');
  }
}

function handleBackToEnter() {
  view.value = 'entering';
  routeCode.value = '';
  inputCode.value = '';
  requiresPassword.value = false;
  enterError.value = '';
  revealError.value = '';
  clearPlaintext();
  router.replace('/s');
}
</script>

<style scoped>
.secret-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
}

.secret-card {
  width: 460px;
  max-width: 100%;
  padding: 40px 36px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow:
    0 4px 32px rgba(99, 102, 241, 0.08),
    0 1px 2px rgba(0, 0, 0, 0.04);
  animation: secret-card-in var(--motion-slow) var(--ease-decelerate) both;
  text-align: center;
}

.secret-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 28px;
}

.secret-logo {
  width: 32px;
  height: 32px;
}

.secret-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

.secret-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.secret-sub {
  margin: 8px 0 24px;
  font-size: 13px;
  color: var(--text-secondary);
}

.secret-form {
  text-align: left;
}

/* 私密码输入：等宽大字号，区分大小写（不做大写转换，与短码内核 54 字符字母表一致） */
.code-input :deep(.el-input__inner) {
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 4px;
}

.secret-alert {
  margin-bottom: 16px;
  text-align: left;
}

.secret-btn {
  width: 100%;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
}

.secret-tip {
  margin: 20px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

/* 明文展示：纯文本等宽 + 换行保留，只读不可编辑 */
.content-box {
  margin-bottom: 16px;
  padding: 16px;
  min-height: 96px;
  max-height: 320px;
  overflow: auto;
  background: var(--bg-secondary, #f6f7fb);
  border-radius: 10px;
  border: 1px solid var(--border-light, #e5e7eb);
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  text-align: left;
  user-select: text;
}

.gone-icon {
  color: var(--color-danger);
  margin-bottom: 12px;
}

@keyframes secret-card-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
