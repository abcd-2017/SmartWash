<template>
  <!-- 柜码公开落地页（/l/:lockerId）：扫码 → /web/t/{code} 302 → 本页 -->
  <div class="landing-page">
    <div class="landing-card">
      <div class="landing-brand">
        <img src="@/assets/logo.svg" alt="SmartWash" class="landing-logo" />
        <span class="landing-name">SmartWash 智能储物柜</span>
      </div>

      <div v-if="loading" class="landing-loading" v-loading="true" element-loading-text="正在读取柜子信息" />

      <!-- 未找到：柜码失效 / 柜子被删除 / 链接不完整 -->
      <template v-else-if="!info">
        <el-icon class="gone-icon" :size="44"><CircleClose /></el-icon>
        <h1 class="landing-title">柜码不存在或已失效</h1>
        <p class="landing-sub">{{ errorMessage || '二维码可能已被更换或撕毁，请联系宿管或运营人员' }}</p>
      </template>

      <!-- 信息卡：只含公开事实（学校/柜号/状态/位置），柜内物品信息不在此展示 -->
      <template v-else>
        <h1 class="landing-title">{{ info.schoolName || '智能储物柜' }}</h1>
        <div class="locker-line">
          <span class="locker-label">{{ info.lockerNumber }} 号柜</span>
          <el-tag :type="statusTagType" size="large">{{ statusText }}</el-tag>
        </div>
        <p v-if="info.schoolLocation" class="landing-sub">
          <el-icon class="sub-icon"><Location /></el-icon>{{ info.schoolLocation }}
        </p>
        <el-divider />
        <p class="landing-tip">
          存洗衣物请按洗衣项目预约后投入柜内；凭取件码取件。柜体故障请勿强开，联系运营处理。
        </p>
      </template>
    </div>
  </div>
</template>

<script setup>
// 柜码公开落地页（路由 /l/:lockerId，免登录匿名访问）。
// 扫码量统计发生在 /web/t/{code} 302 链路，本页为纯数据读，不重复计数。
// 码约定：路由携带 lockerId，公开解析 API 的别名即 L{lockerId}（后端 LockerTagCodes 契约）。
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { Location, CircleClose } from '@element-plus/icons-vue';
import { getLockerTagInfo } from '@/api/lockerTag';
import { lockerStatusTagType, lockerStatusText } from '@/constants/dict';
import logger from '@/utils/logger';

const route = useRoute();
const loading = ref(true);
const info = ref(null);
const errorMessage = ref('');

const statusText = computed(() =>
  info.value ? lockerStatusText(info.value.status) : ''
);
const statusTagType = computed(() =>
  info.value ? lockerStatusTagType(info.value.status) : 'info'
);

onMounted(async () => {
  const lockerId = String(route.params.lockerId || '').trim();
  if (!/^\d{1,15}$/.test(lockerId)) {
    loading.value = false;
    errorMessage.value = '链接不完整';
    return;
  }
  try {
    info.value = await getLockerTagInfo(`L${lockerId}`);
  } catch (error) {
    logger.warn('柜码解析被拒：', error?.message);
    errorMessage.value = error?.message || '';
    info.value = null;
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.landing-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
}

.landing-card {
  width: 420px;
  max-width: 100%;
  padding: 40px 36px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow:
    0 4px 32px rgba(99, 102, 241, 0.08),
    0 1px 2px rgba(0, 0, 0, 0.04);
  text-align: center;
}

.landing-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 28px;
}

.landing-logo {
  width: 32px;
  height: 32px;
}

.landing-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

.landing-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.landing-sub {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.sub-icon {
  vertical-align: -2px;
  margin-right: 4px;
}

.locker-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
}

/* 柜号：机器标识符按 DESIGN.md mono 规则等宽 + tabular nums */
.locker-label {
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-variant-numeric: tabular-nums;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.landing-loading {
  height: 160px;
}

.gone-icon {
  color: var(--color-danger);
  margin-bottom: 12px;
}

.landing-tip {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.8;
  text-align: left;
}
</style>
