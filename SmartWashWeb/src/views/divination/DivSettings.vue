<template>
  <div class="page-container">
    <div class="table-card settings-card">
      <div class="settings-header">
        <p class="settings-sub">
          观象台全局配置：AI 解读开关、用户每日限额与主密钥轮换（平台零兜底，模型由用户自行配置）
        </p>
      </div>

      <el-form
        ref="formRef"
        :model="settings"
        label-width="min(160px, 94vw)"
        :rules="rules"
        class="settings-form"
        v-loading="loading"
      >
        <!-- 功能开关 -->
        <el-divider content-position="left">AI 解读</el-divider>

        <el-form-item label="AI 解读开关" prop="divUseEnabled">
          <el-switch
            v-model="settings.divUseEnabled"
            :active-value="1"
            :inactive-value="0"
            active-text="开放（用户配置自己的模型后可解读）"
            inactive-text="关闭（客户端隐藏解读入口并拦截）"
          />
        </el-form-item>

        <el-form-item label="每日解读限额" prop="byokDailyLimit">
          <el-input-number
            v-model="settings.byokDailyLimit"
            :min="1"
            :max="999"
            controls-position="right"
          />
          <span class="form-tip">次/用户/天（解读费用由用户自带的 API Key 承担）</span>
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
import { getPlatformSettings, updatePlatformSettings, rotateMasterKey } from '@/api/divination';
import { useConfirm } from '@/composables/useConfirm';
import logger from '@/utils/logger';
import { isValidationError } from '@/utils/errors';

const formRef = ref(null);
const loading = ref(false);
const submitting = ref(false);
const rotating = ref(false);

const settings = reactive({
  divUseEnabled: 1,
  byokDailyLimit: 50,
});

const rules = reactive({
  byokDailyLimit: [{ required: true, message: '请输入每日解读限额', trigger: 'blur' }],
});

onMounted(() => {
  fetchSettings();
});

const fetchSettings = async () => {
  loading.value = true;
  try {
    const res = await getPlatformSettings();
    Object.assign(settings, {
      divUseEnabled: res.divUseEnabled ?? 1,
      byokDailyLimit: res.byokDailyLimit ?? 50,
    });
  } catch (error) {
    logger.error('获取平台设置失败:', error);
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
    // settings 不含密钥字段，业务键可安全入日志
    logger.info(
      '平台设置保存成功',
      `divUseEnabled=${settings.divUseEnabled}`,
      `byokDailyLimit=${settings.byokDailyLimit}`
    );
    ElMessage.success('保存成功');
  } catch (error) {
    // validate() 校验失败的特征是错误对象带 fields；与接口失败区分级别（校验失败不属系统错误）
    if (isValidationError(error)) {
      logger.debug('平台设置表单校验未通过，已拦截提交（错误示于字段红字，不弹全局 toast）');
    } else {
      logger.error('平台设置保存失败:', error);
      ElMessage.error(error.message || '保存失败');
    }
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
    // 高危关键动作必留痕；密钥材料一律不入日志
    logger.info('主密钥轮换已启动');
    ElMessage.success('主密钥轮换已启动，后台正在分批重加密');
  } catch (error) {
    logger.error('主密钥轮换失败:', error);
    ElMessage.error(error.message || '主密钥轮换失败');
  } finally {
    rotating.value = false;
  }
};
</script>

<style scoped>
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
  color: var(--text-primary);
}

.settings-sub {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.settings-form {
  margin-top: 16px;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.rotate-key-section {
  padding: 0 16px 16px;
}

/* 设置卡(live 打磨 v1):表单内容宽度收敛至 720px,避免控件在满宽卡片内被拉散 */
.settings-card > .settings-header,
.settings-card > .settings-form,
.settings-card > .el-divider,
.settings-card > .rotate-key-section {
  max-width: 720px;
}
</style>
