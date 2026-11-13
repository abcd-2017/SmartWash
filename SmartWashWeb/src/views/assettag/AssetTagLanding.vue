<template>
  <!-- 物品标签公开寻物页（/tag/:code）：捡到物品扫码直达 -->
  <div class="landing-page">
    <div class="landing-card">
      <div class="landing-brand">
        <img src="@/assets/logo.svg" alt="SmartWash" class="landing-logo" />
        <span class="landing-name">SmartWash 物品标签</span>
      </div>

      <div v-if="loading" class="landing-loading" v-loading="true" element-loading-text="正在读取标签信息" />

      <!-- 失效态：标签不存在/已过期/被删除 -->
      <template v-else-if="!info">
        <el-icon class="gone-icon" :size="44"><CircleClose /></el-icon>
        <h1 class="landing-title">标签不存在或已失效</h1>
        <p class="landing-sub">{{ errorMessage || '二维码可能已被撕毁或标签已被失主删除' }}</p>
      </template>

      <template v-else>
        <el-tag :type="assetTagStatusTagType(info.status)" size="large" class="status-tag">
          {{ assetTagStatusText(info.status) }}
        </el-tag>
        <h1 class="landing-title">{{ info.itemName }}</h1>
        <p v-if="info.itemDesc" class="landing-sub">{{ info.itemDesc }}</p>

        <!-- 寻物中：联系方式由后端按「寻物中 + 已留」矩阵解密下发 -->
        <el-alert
          v-if="info.hasContact"
          class="contact-alert"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>失主正在寻找这件物品</template>
          <div class="contact-line">
            请联系失主：
            <span class="contact-value">{{ info.contact }}</span>
          </div>
        </el-alert>

        <el-divider />

        <p v-if="info.status === 2" class="landing-tip">
          该物品已被寻回，感谢每一位愿意扫码联系失主的同学。
        </p>
        <p v-else class="landing-tip">
          这是失主贴在物品上的寻物标签。扫码不会暴露失主的其他信息，请放心。
        </p>
      </template>
    </div>
  </div>
</template>

<script setup>
// 物品标签公开寻物页（路由 /tag/:code，免登录匿名访问）。
// 隐私：联系方式由后端按「寻物中 + 已留」矩阵解密下发，本页只做展示；
// 每次解析为失主留下扫码线索（IP 仅摘要，仅失主可见）。
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { CircleClose } from '@element-plus/icons-vue';
import { getAssetTagInfo } from '@/api/assetTag';
import { assetTagStatusText, assetTagStatusTagType } from '@/constants/dict';
import logger from '@/utils/logger';

const route = useRoute();
const loading = ref(true);
const info = ref(null);
const errorMessage = ref('');

onMounted(async () => {
  const code = String(route.params.code || '').trim();
  if (!code) {
    loading.value = false;
    errorMessage.value = '链接不完整';
    return;
  }
  try {
    info.value = await getAssetTagInfo(code);
  } catch (error) {
    logger.warn('物品标签解析被拒：', error?.message);
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
  margin-bottom: 24px;
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

.status-tag {
  margin-bottom: 12px;
}

.landing-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  word-break: break-all;
}

.landing-sub {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.contact-alert {
  margin-top: 20px;
  text-align: left;
}

.contact-line {
  font-size: 13px;
}

/* 联系方式：失主主动公开的信息，等宽大字号便于抄写 */
.contact-value {
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-variant-numeric: tabular-nums;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
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

.landing-loading {
  height: 160px;
}
</style>
