<template>
  <div class="page-container">
    <!-- 说明卡片 -->
    <el-alert title="演示工具" type="info" :closable="false" show-icon>
      <template #default>
        查询指定手机号当前有效的短信验证码。验证码有效期 10 分钟，一次性使用（校验成功后立即销毁）。
      </template>
    </el-alert>

    <!-- 查询区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="form" @submit.prevent="handleQuery">
        <el-form-item label="手机号" :error="phoneError">
          <el-input
            v-model="form.phoneNumber"
            placeholder="请输入11位手机号，支持 +86 前缀"
            clearable
            maxlength="16"
            style="width: 240px"
            @input="phoneError = ''"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery"> 查询验证码 </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 查询失败态：与「无结果」明确区分，避免把失败误读成没有验证码 -->
    <el-alert
      v-if="queryState === 'error'"
      title="查询失败"
      type="error"
      :closable="false"
      show-icon
    >
      <template #default>
        未能获取查询结果，请稍后重试；若持续失败请联系开发排查服务状态。
      </template>
    </el-alert>

    <!-- 结果区域 -->
    <div v-if="queryState === 'done' && result" class="table-card">
      <el-descriptions title="查询结果" :column="1" border>
        <el-descriptions-item label="手机号">{{ result.phone }}</el-descriptions-item>
      </el-descriptions>

      <el-table
        v-if="result.captchas && result.captchas.length"
        :data="result.captchas"
        fit
        highlight-current-row
      >
        <el-table-column prop="purposeDesc" label="用途" min-width="120">
          <template #default="{ row }">
            <el-tag :type="captchaPurposeTagType(row.purpose)">
              {{ row.purposeDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="验证码" min-width="140">
          <template #default="{ row }">
            <span class="captcha-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余有效时间" min-width="160">
          <template #default="{ row }">
            <span v-if="liveRemainSeconds(row) > 0">
              {{ formatRemainTime(liveRemainSeconds(row)) }}
            </span>
            <span v-else style="color: var(--color-danger)">{{ expiredText() }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="该手机号暂无有效验证码" :image-size="80" />
      <!-- 口径说明（Micro 档）：多历史验证码时以最新一次为准 -->
      <p class="result-hint">以最新一次查询为准，验证码被使用后立即失效</p>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { queryCaptcha } from '@/api/captcha';
import { captchaPurposeTagType } from '@/constants/dict';
import logger from '@/utils/logger';

const PHONE_REGEX = /^(\+86)?1[3-9]\d{9}$/;

const form = reactive({
  phoneNumber: '',
});

const phoneError = ref('');
const loading = ref(false);
const result = ref(null);
// 三态：idle 未查询 / done 完成（含无结果）/ error 失败——失败不再被误读为「无验证码」
const queryState = ref('idle');

// 倒计时：查询瞬间的 remainSeconds 是快照，按 elapsed 每秒递减才与真实有效期一致
let queriedAt = 0;
let ticker = null;
const tick = ref(0);

const startTicker = () => {
  stopTicker();
  queriedAt = Date.now();
  ticker = setInterval(() => {
    tick.value += 1;
    // 全部过期后停止计时，避免空转
    const list = result.value?.captchas ?? [];
    if (list.length && list.every((row) => liveRemainSeconds(row) <= 0)) stopTicker();
  }, 1000);
};

const stopTicker = () => {
  if (ticker) {
    clearInterval(ticker);
    ticker = null;
  }
};

onBeforeUnmount(stopTicker);

const liveRemainSeconds = (row) => {
  // 引用 tick 建立响应依赖：每秒重算
  void tick.value;
  const elapsed = Math.floor((Date.now() - queriedAt) / 1000);
  return (row.remainSeconds ?? 0) - elapsed;
};

async function handleQuery() {
  phoneError.value = '';
  queryState.value = 'idle';
  result.value = null;
  stopTicker();

  const phone = form.phoneNumber.trim();
  if (!phone) {
    phoneError.value = '请输入手机号';
    return;
  }
  if (!PHONE_REGEX.test(phone)) {
    phoneError.value = '手机号格式错误';
    return;
  }

  loading.value = true;
  try {
    const data = await queryCaptcha(phone);
    result.value = data;
    queryState.value = 'done';
    startTicker();
    // 读操作仅 debug 留痕：记条数便于对账；手机号与验证码值均不入日志（敏感红线）
    logger.debug('验证码查询成功', `count=${data?.captchas?.length ?? 0}`);
  } catch (error) {
    // HTTP 层错误已由 http.js 拦截器统一 ElMessage 提示，这里归因到页面动作；
    // 业务失败（code !== 200）拦截器仅 reject 不提示，此前只清空结果——失败被误读为
    // 「该手机号无验证码」，找回账号等场景直接误导运营判断（评审 P1）
    // error 仅保留 message/stack（sanitize 丢弃 config），手机号与验证码值不入日志
    logger.error('验证码查询失败:', error);
    queryState.value = 'error';
    ElMessage.error(error.message || '查询失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

// 过期文案统一出口：倒计时列与剩余时间格式化共用，避免改文案漏改一处
const expiredText = () => '已过期';

function formatRemainTime(seconds) {
  if (!seconds || seconds <= 0) return expiredText();
  const min = Math.floor(seconds / 60);
  const sec = seconds % 60;
  if (min > 0) {
    return `${min} 分 ${sec} 秒`;
  }
  return `${sec} 秒`;
}
</script>

<style scoped>
.captcha-code {
  font-family: var(--font-mono);
  font-size: 20px; /* 对齐 headline 档（验证码强调展示，原 18px off-ramp） */
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--color-primary);
}

/* 结果区口径说明（Micro 档 12px 弱化） */
.result-hint {
  margin: 0;
  padding: 10px 20px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
