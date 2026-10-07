<template>
  <div class="page-container">
    <!-- 说明卡片 -->
    <el-alert
      title="演示工具"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    >
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
            placeholder="请输入手机号，如 13800138000"
            clearable
            maxlength="13"
            style="width: 240px"
            @input="phoneError = ''"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">
            查询验证码
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 结果区域 -->
    <div v-if="result" class="table-card">
      <el-descriptions title="查询结果" :column="1" border>
        <el-descriptions-item label="手机号">{{ result.phone }}</el-descriptions-item>
      </el-descriptions>

      <el-table
        v-if="result.captchas && result.captchas.length"
        :data="result.captchas"
        style="margin-top: 16px"
        fit
        highlight-current-row
      >
        <el-table-column prop="purposeDesc" label="用途" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.purpose === 'register' ? 'success' : 'warning'">
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
            <span v-if="row.remainSeconds > 0">
              {{ formatRemainTime(row.remainSeconds) }}
            </span>
            <span v-else style="color: #f56c6c">已过期</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { queryCaptcha } from '@/api/captcha';

const PHONE_REGEX = /^(\+86)?1[3-9]\d{9}$/;

const form = reactive({
  phoneNumber: '',
});

const phoneError = ref('');
const loading = ref(false);
const result = ref(null);

async function handleQuery() {
  phoneError.value = '';
  result.value = null;

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
  } catch {
    // 查询失败静默处理：HTTP 层错误已由 http.js 拦截器统一 ElMessage 提示；
    // 业务失败（code !== 200）拦截器仅 reject 不提示，这里仅清空结果保持 UI 一致
  } finally {
    loading.value = false;
  }
}

function formatRemainTime(seconds) {
  if (!seconds || seconds <= 0) return '已过期';
  const min = Math.floor(seconds / 60);
  const sec = seconds % 60;
  if (min > 0) {
    return `${min} 分 ${sec} 秒`;
  }
  return `${sec} 秒`;
}
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.filter-container {
  background: #fff;
  padding: 20px 20px 4px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.table-card {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.captcha-code {
  font-family: 'Courier New', monospace;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #67c23a;
}
</style>
