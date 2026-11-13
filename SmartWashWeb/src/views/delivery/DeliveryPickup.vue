<template>
  <!-- 会话视图：纵向流式布局（会话页自带居中与满高），进入/失效视图：视口居中卡片 -->
  <div class="pickup-page" :class="{ 'pickup-page--flow': view === 'session' }">
    <!-- 视图一：进入页（输码 / 密码） -->
    <div v-if="view === 'entering'" class="enter-card enter-card--branded">
      <div class="enter-brand-band">
        <img src="@/assets/logo.svg" alt="SmartWash" class="enter-logo" />
        <span class="enter-name">SmartWash 设备投递</span>
      </div>
      <div class="enter-body">
        <h1 class="enter-title">{{ routeCode ? '进入投递会话' : '输入投递码' }}</h1>
        <p class="enter-sub">
          {{
            routeCode
              ? '该会话来自你的手机，进入后可与手机双向互传文本与文件'
              : '请输入手机端设备投递页展示的 7 位投递码'
          }}
        </p>

        <!-- 无码访问 /d 时先输码 -->
        <el-form v-if="!routeCode" class="enter-form" @submit.prevent>
          <el-form-item>
            <el-input
              v-model="inputCode"
              class="code-input"
              placeholder="如 Dlv4xkQ"
              maxlength="7"
              size="large"
              autofocus
              @input="inputCode = inputCode.toUpperCase()"
              @keyup.enter="handleEnter"
            >
              <template #prefix
                ><el-icon><Postcard /></el-icon
              ></template>
            </el-input>
          </el-form-item>
        </el-form>

        <!-- 有密码会话：密码输入（仅当后端返回 403 后展示） -->
        <el-form v-if="needPassword" class="enter-form" @submit.prevent>
          <el-form-item>
            <el-input
              v-model="inputPassword"
              type="password"
              placeholder="请输入会话访问密码"
              size="large"
              show-password
              @keyup.enter="handleEnter"
            >
              <template #prefix
                ><el-icon><Lock /></el-icon
              ></template>
            </el-input>
          </el-form-item>
        </el-form>

        <!-- 内联错误：409/404 等会话语义错误常驻展示，避免 toast 一闪而过 -->
        <el-alert
          v-if="enterError"
          class="enter-alert"
          :title="enterError"
          :type="enterErrorType"
          :closable="false"
          show-icon
        />

        <el-button
          type="primary"
          size="large"
          class="enter-btn"
          :loading="entering"
          :disabled="!canSubmit"
          @click="handleEnter"
        >
          {{ needPassword ? '验证密码并进入' : '进入会话' }}
        </el-button>

        <p class="enter-tip">文本与文件经服务器中转且取走即焚，会话到期自动回收</p>
      </div>
    </div>

    <!-- 视图二：会话页 -->
    <DeliverySession
      v-else-if="view === 'session'"
      :code="activeCode"
      :session="sessionInfo"
      @expired="handleExpired"
    />

    <!-- 视图三：失效态 -->
    <div v-else class="gone-card">
      <el-icon class="gone-icon" :size="44"><CircleClose /></el-icon>
      <h1 class="gone-title">会话不存在或已失效</h1>
      <p class="gone-sub">投递会话已过期、被关闭或从未存在。条目随会话回收，无法恢复。</p>
      <el-button type="primary" @click="handleBackToEnter">重新输入投递码</el-button>
    </div>
  </div>
</template>

<script setup>
// 设备投递公开取件页容器（路由 /d/:code，免登录匿名访问）。
// 三态：entering（输码/密码进入）→ session（双向会话）→ gone（会话失效/过期）。
// 码即凭证：enter 成功即持有会话操作权（单对端互斥，第二个对端进入返回 409）。
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Postcard, Lock, CircleClose } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import { DELIVERY_CODE, enterDelivery } from '@/api/delivery';
import logger from '@/utils/logger';
import DeliverySession from './DeliverySession.vue';

const route = useRoute();
const router = useRouter();

const view = ref('entering');
const routeCode = computed(() => String(route.params.code || '').trim());
const activeCode = ref('');
const sessionInfo = ref(null);

const inputCode = ref('');
const inputPassword = ref('');
const needPassword = ref(false);
const entering = ref(false);
const enterError = ref('');
const enterErrorType = ref('error');

const canSubmit = computed(() => (routeCode.value ? true : inputCode.value.trim().length > 0));

// 自动进入：从 /d/{code} 直接打开时跳过输码，无密码会话直接进；
// 有密码会话由 403 触发展示密码框（契约：无密码会话传空也能成功，有密码不传即 403）
onMounted(() => {
  if (routeCode.value) {
    doEnter(routeCode.value, '');
  }
});

function handleEnter() {
  if (entering.value || !canSubmit.value) {
    // 进入中或手动输码为空的请求拦截（按钮已禁用，回车键仍可能触发），仅 dev 记录
    logger.debug('进入请求被拦截：正在进入或投递码为空');
    return;
  }
  doEnter(routeCode.value || inputCode.value.trim(), needPassword.value ? inputPassword.value : '');
}

async function doEnter(code, password) {
  if (!code) return;
  entering.value = true;
  enterError.value = '';
  try {
    const envelope = await enterDelivery(code, password);
    if (envelope.code === DELIVERY_CODE.OK) {
      activeCode.value = code;
      sessionInfo.value = envelope.data || {};
      needPassword.value = false;
      view.value = 'session';
      // 低频关键动作成功（码即取件凭证，只记来源与是否带密码，凭证值不入日志）
      logger.info(
        '进入投递会话成功',
        `来源=${routeCode.value ? '链接直进' : '手动输码'}`,
        `带密码=${Boolean(password)}`
      );
      // 手动输码进入（URL 无码）时把码补进地址：刷新/分享链接后可直接恢复进入流程
      if (!routeCode.value) {
        router.replace(`/d/${encodeURIComponent(code)}`);
      }
      return;
    }
    if (envelope.code === DELIVERY_CODE.WRONG_PASSWORD) {
      // 首次 403 视为「会话有密码」；带密码仍 403 才提示密码错误
      const firstTry = !needPassword.value;
      if (firstTry) {
        // 正常流程分支：发现会话设有密码，非异常
        logger.debug('会话设有访问密码，已展示密码输入');
      } else {
        logger.warn('进入被拒：访问密码错误（403）');
      }
      needPassword.value = true;
      enterErrorType.value = 'warning';
      enterError.value = firstTry ? '该会话已设置访问密码，请输入后进入' : '进入密码错误，请重试';
      return;
    }
    if (envelope.code === DELIVERY_CODE.CONFLICT) {
      logger.warn('进入被拒：会话已被其他设备占用（409）');
      enterErrorType.value = 'warning';
      enterError.value = '另一设备已占用此会话，请稍后再试或让手机端重新发起';
      return;
    }
    if (envelope.code === DELIVERY_CODE.GONE) {
      logger.warn('进入被拒：会话不存在或已过期（404）');
      enterErrorType.value = 'error';
      enterError.value = '会话不存在或已过期';
      // 从链接直进（URL 带码）而码已失效时，回到无码形态展示输码表单，方便用户重输
      if (routeCode.value) {
        router.replace('/d');
      }
      return;
    }
    // 201 通用业务失败（如码格式非法）等
    logger.warn('进入被拒：', `code=${envelope.code}`, envelope.message);
    enterErrorType.value = 'error';
    enterError.value = envelope.message || '进入失败，请稍后重试';
  } catch (error) {
    // 页面自有 catch（envelopeHttp 无拦截器留痕），此处兜底记录
    logger.error('进入投递会话网络异常:', error);
    ElMessage.error('网络异常，请稍后重试');
  } finally {
    entering.value = false;
  }
}

// 会话页上报失效（轮询 404 / 本地倒计时归零）
function handleExpired() {
  view.value = 'gone';
}

function handleBackToEnter() {
  view.value = 'entering';
  activeCode.value = '';
  sessionInfo.value = null;
  inputCode.value = '';
  inputPassword.value = '';
  needPassword.value = false;
  enterError.value = '';
  // 地址同步回无码形态，避免刷新后又拿旧码自动进入
  router.replace('/d');
}
</script>

<style scoped>
.pickup-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
}

/* 会话视图：改为纵向 stretch，条目流内滚由会话页自行控制 */
.pickup-page--flow {
  align-items: stretch;
  padding: 0;
}

/* --- 进入页卡片（风格对齐 LoginPage 单栏形态） --- */
.enter-card,
.gone-card {
  width: 420px;
  max-width: 100%;
  padding: 40px 36px 28px;
  background: var(--bg-card);
  border-radius: 16px;
  box-shadow: var(--shadow-brand); /* 品牌光阴影：仅登录/404/投递面板允许（base.css token） */
  animation: delivery-card-in var(--motion-slow) var(--ease-decelerate) both;
  text-align: center;
}

/* 进入页品牌暗色头带(live 打磨 v2):对齐登录页品牌语言,公开页有产品归属 */
.enter-card--branded {
  padding: 0;
  overflow: hidden;
}

.enter-brand-band {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 24px;
  background: var(--sidebar-gradient);
}

.enter-brand-band .enter-name {
  color: var(--sidebar-text-strong);
}

.enter-body {
  padding: 32px 36px 28px;
}

.enter-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 28px;
}

.enter-logo {
  width: 32px;
  height: 32px;
}

.enter-name {
  font-size: 14px; /* 对齐 body 档（原 15px off-ramp） */
  font-weight: 700;
  color: var(--text-primary);
}

.enter-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.enter-sub {
  margin: 8px 0 24px;
  font-size: 13px;
  color: var(--text-secondary);
}

.enter-form {
  text-align: left;
}

.enter-form + .enter-form {
  margin-top: 4px;
}

/* 投递码输入：等宽大字号，便于对照手机端逐位核对 */
.code-input :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 6px;
  /* 值已在 @input 同步转大写,此处 uppercase 仅为冗余保险 */
  text-transform: uppercase;
  font-variant-numeric: tabular-nums;
}

.enter-alert {
  margin-bottom: 16px;
  text-align: left;
}

.enter-btn {
  width: 100%;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
}

.enter-tip {
  margin: 20px 0 0;
  font-size: 12px;
  /* 有意义提示文字用 secondary(muted 对白底 ≈2.9:1,低于 WCAG AA 4.5:1) */
  color: var(--text-secondary);
}

/* --- 失效态 --- */
.gone-icon {
  color: var(--color-danger);
  margin-bottom: 12px;
}

.gone-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.gone-sub {
  margin: 10px 0 24px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
}

@keyframes delivery-card-in {
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
