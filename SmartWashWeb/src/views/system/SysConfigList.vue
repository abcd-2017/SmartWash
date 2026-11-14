<template>
  <div class="page-container">
    <el-card class="config-card">
      <div class="card-header">
        <h2>系统参数设置</h2>
        <p class="header-sub">
          业务参数统一在此配置，修改后落库并立即生效（无需重启）。数值型参数必须为正整数。
        </p>
      </div>

      <!-- 加载失败态：与空列表区分，给出重试入口，不再让表格静默空白 -->
      <div v-if="loadError" class="load-error">
        <span>系统参数加载失败，请检查网络后重试</span>
        <el-button size="small" @click="fetchList">重试</el-button>
      </div>
      <el-table v-else :data="list" v-loading="loading" border stripe>
        <el-table-column prop="configKey" label="参数键" min-width="200">
          <template #default="{ row }">
            <span class="config-key">{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="参数值" min-width="120" />
        <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="最近修改" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      title="修改参数"
      width="min(480px, 94vw)"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="参数键">
          <span class="config-key">{{ form.configKey }}</span>
        </el-form-item>
        <el-form-item label="参数值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入参数值" maxlength="255" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            placeholder="参数用途说明"
            maxlength="255"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getSysConfigList, updateSysConfig } from '@/api/config';
import { formatTime } from '@/utils/format';
import { isValidationError } from '@/utils/errors';
import logger from '@/utils/logger';

const loading = ref(false);
const loadError = ref(false); // 列表加载失败态：与空列表区分，供表格区展示重试入口
const submitting = ref(false);
const dialogVisible = ref(false);
const list = ref([]);
const formRef = ref(null);

const form = reactive({
  id: null,
  configKey: '',
  configValue: '',
  remark: '',
});

const rules = reactive({
  configValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }],
});

onMounted(() => {
  fetchList();
});

const fetchList = async () => {
  loading.value = true;
  loadError.value = false;
  try {
    list.value = await getSysConfigList();
  } catch (error) {
    logger.error('获取系统参数失败:', error);
    loadError.value = true;
    ElMessage.error(error.message || '获取系统参数失败');
  } finally {
    loading.value = false;
  }
};

// 弹窗关闭 — 清除校验红字，避免下次打开残留上次未通过的校验态
const handleDialogClosed = () => {
  formRef.value?.clearValidate();
};

const handleEdit = (row) => {
  Object.assign(form, {
    id: row.id,
    configKey: row.configKey,
    configValue: row.configValue,
    remark: row.remark || '',
  });
  dialogVisible.value = true;
};

const submitForm = async () => {
  // 先进入提交态再校验：validate 是异步的，若在其后才置 loading，连点会打开双提交窗口
  submitting.value = true;
  try {
    await formRef.value.validate();
    await updateSysConfig({
      id: form.id,
      configValue: form.configValue,
      remark: form.remark,
    });
    logger.info('系统参数更新成功', `id=${form.id} key=${form.configKey}`);
    ElMessage.success('修改成功，已即时生效');
    dialogVisible.value = false;
    fetchList();
  } catch (error) {
    // 校验失败的特征是错误对象带 fields；字段红字已示于表单，不再叠加误导性 toast
    if (isValidationError(error)) {
      logger.debug('系统参数表单校验未通过，已拦截提交');
      return;
    }
    logger.error('系统参数更新失败:', error);
    ElMessage.error(error.message || '修改失败');
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.config-card {
  padding: 4px 8px;
}

.card-header {
  margin-bottom: 16px;
}

.card-header h2 {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.header-sub {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* 列表加载失败占位：与空列表区分，居中提示 + 重试入口 */
.load-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 32px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.config-key {
  font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 13px;
  color: var(--text-primary);
}
</style>
