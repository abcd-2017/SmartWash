<template>
  <div class="page-container">
    <div class="table-card settings-card">
      <div class="settings-header">
        <h2>平台设置</h2>
        <p class="settings-sub">占卜模块全局配置：默认模型、BYOK 开关、限额与主密钥轮换</p>
      </div>

      <el-form
        ref="formRef"
        :model="settings"
        label-width="160px"
        :rules="rules"
        class="settings-form"
        v-loading="loading"
      >
        <!-- 模型配置 -->
        <el-divider content-position="left">模型配置</el-divider>

        <el-form-item label="默认模型" prop="defaultModelId">
          <el-select
            v-model="settings.defaultModelId"
            placeholder="选择默认模型"
            filterable
            style="width: 300px"
          >
            <el-option
              v-for="model in modelOptions"
              :key="model.id"
              :label="model.name"
              :value="model.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="备用模型" prop="fallbackModelId">
          <el-select
            v-model="settings.fallbackModelId"
            placeholder="选择备用模型（可选）"
            filterable
            clearable
            style="width: 300px"
          >
            <el-option
              v-for="model in modelOptions"
              :key="model.id"
              :label="model.name"
              :value="model.id"
            />
          </el-select>
        </el-form-item>

        <!-- BYOK 配置 -->
        <el-divider content-position="left">BYOK（用户自带密钥）</el-divider>

        <el-form-item label="BYOK 开关" prop="byokEnabled">
          <el-switch
            v-model="settings.byokEnabled"
            :active-value="1"
            :inactive-value="0"
            active-text="允许用户自带 API Key"
            inactive-text="关闭"
          />
        </el-form-item>

        <el-form-item label="BYOK 每日限额" prop="byokDailyLimit">
          <el-input-number
            v-model="settings.byokDailyLimit"
            :min="1"
            :max="999"
            controls-position="right"
          />
          <span class="form-tip">次/用户/天（BYOK 用户费用自担，限额可更高）</span>
        </el-form-item>

        <el-form-item label="平台每日限额" prop="platformDailyLimit">
          <el-input-number
            v-model="settings.platformDailyLimit"
            :min="1"
            :max="999"
            controls-position="right"
          />
          <span class="form-tip">次/用户/天（使用平台默认模型的限额）</span>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <el-button type="primary" @click="submitForm" :loading="submitting">保存设置</el-button>
          <el-button @click="fetchSettings">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 主密钥轮换 -->
      <el-divider content-position="left">主密钥轮换</el-divider>
      <div class="rotate-key-section">
        <p class="form-tip">
          轮换主密钥后，后台将分批对所有已加密的 API Key 进行「解密→重新加密→写回」操作。
          轮换期间解读服务不受影响（解密按记录的 key_version 路由）。
        </p>
        <el-button type="danger" @click="handleRotateKey" :loading="rotating">
          <el-icon><Refresh /></el-icon>
          执行主密钥轮换
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import {
  getPlatformSettings,
  updatePlatformSettings,
  rotateMasterKey,
  getModelList,
} from '@/api/divination';
import { useConfirm } from '@/composables/useConfirm';

const formRef = ref(null);
const loading = ref(false);
const submitting = ref(false);
const rotating = ref(false);
const modelOptions = ref([]);

const settings = reactive({
  defaultModelId: null,
  fallbackModelId: null,
  byokEnabled: 0,
  byokDailyLimit: 50,
  platformDailyLimit: 20,
});

const rules = reactive({
  defaultModelId: [{ required: true, message: '请选择默认模型', trigger: 'change' }],
  byokDailyLimit: [{ required: true, message: '请输入 BYOK 每日限额', trigger: 'blur' }],
  platformDailyLimit: [{ required: true, message: '请输入平台每日限额', trigger: 'blur' }],
});

onMounted(async () => {
  await fetchModelOptions();
  await fetchSettings();
});

const fetchModelOptions = async () => {
  try {
    const res = await getModelList({ page: 1, size: 100 });
    modelOptions.value = res.records || [];
  } catch (error) {
    ElMessage.error(error.message || '获取模型列表失败');
  }
};

const fetchSettings = async () => {
  loading.value = true;
  try {
    const res = await getPlatformSettings();
    Object.assign(settings, {
      defaultModelId: res.defaultModelId ?? null,
      fallbackModelId: res.fallbackModelId ?? null,
      byokEnabled: res.byokEnabled ?? 0,
      byokDailyLimit: res.byokDailyLimit ?? 50,
      platformDailyLimit: res.platformDailyLimit ?? 20,
    });
  } catch (error) {
    ElMessage.error(error.message || '获取平台设置失败');
  } finally {
    loading.value = false;
  }
};

const submitForm = async () => {
  try {
    await formRef.value.validate();
    submitting.value = true;
    await updatePlatformSettings({ ...settings });
    ElMessage.success('保存成功');
  } catch (error) {
    ElMessage.error(error.message || '保存失败');
  } finally {
    submitting.value = false;
  }
};

const handleRotateKey = async () => {
  const confirmed = await useConfirm(
    '确认执行主密钥轮换？此操作将触发后台分批重加密任务，耗时取决于数据量。',
    '危险操作'
  );
  if (!confirmed) return;
  rotating.value = true;
  try {
    await rotateMasterKey();
    ElMessage.success('主密钥轮换已启动，后台正在分批重加密');
  } catch (error) {
    ElMessage.error(error.message || '主密钥轮换失败');
  } finally {
    rotating.value = false;
  }
};
</script>

<style scoped>
@import '@/assets/pages.css';

.settings-card {
  padding: 24px;
}

.settings-header {
  margin-bottom: 8px;
}

.settings-header h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.settings-sub {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

.settings-form {
  margin-top: 16px;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.rotate-key-section {
  padding: 0 16px 16px;
}
</style>
