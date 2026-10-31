<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery">
        <el-form-item label="关键词">
          <el-input
            v-model="listQuery.keyword"
            placeholder="标题 / gameKey 模糊搜索"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="listQuery.category"
            placeholder="全部分类"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="opt in GAME_CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="primary" @click="handleCreate">上传游戏</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table v-loading="listLoading" :data="gameList" fit highlight-current-row>
        <el-table-column label="游戏" min-width="220">
          <template #default="{ row }">
            <div class="title-cell">
              <div class="title-row">
                <!-- 端上按 icon/color key 白名单映射，色块直观展示色组、tag 展示 icon key 便于两端对齐 -->
                <span class="icon-block" :style="{ backgroundColor: gameColorHex(row.color) }" />
                <span class="title-main">{{ row.title || '-' }}</span>
                <el-tag v-if="row.badge" size="small" type="warning" effect="plain">
                  {{ row.badge }}
                </el-tag>
                <el-tag size="small" type="info">{{ row.icon || 'default' }}</el-tag>
              </div>
              <span v-if="row.description" class="title-sub">{{ row.description }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="gameKey" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="key-text">{{ row.gameKey || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="90">
          <template #default="{ row }">
            <el-tag :type="gameCategoryTagType(row.category)" effect="plain">
              {{ gameCategoryText(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="80">
          <template #default="{ row }">
            <el-tag :type="gameSourceTypeTagType(row.sourceType)">
              {{ gameSourceTypeText(row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" min-width="100">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" min-width="70" align="center">
          <template #default="{ row }">{{ row.sort ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="下发" min-width="90">
          <template #default="{ row }">
            <!-- 行内切换即「选择哪些下发」：成功前乐观更新，失败回滚并提示 -->
            <el-switch
              v-model="row.enabled"
              :active-value="1"
              :inactive-value="0"
              :loading="togglingId === row.id"
              @change="handleToggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <!-- 更新时间（兜底 createdAt 防后端裁剪字段，对齐 WorkbenchEntryList 先例） -->
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              size="small"
              type="danger"
              :loading="deletingId === row.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          background
          :current-page="listQuery.page"
          :page-size="listQuery.size"
          :page-sizes="pageSizes"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog
      title="上传游戏"
      v-model="uploadDialogVisible"
      width="760px"
      :close-on-click-modal="false"
      :show-close="!uploading"
      @closed="handleUploadDialogClosed"
    >
      <!-- 成功后展示可玩地址；再传一个重新回到表单态 -->
      <template v-if="!uploadResult">
        <el-form ref="uploadFormRef" :model="tempGame" label-width="90px" :rules="rules">
          <el-divider content-position="left">游戏文件</el-divider>

          <el-form-item label="文件" prop="file">
            <!-- 拖拽区：hide 内部文件列表，选中后自绘文件卡片（校验失败可整条移除重选） -->
            <el-upload
              ref="uploadRef"
              class="game-uploader"
              drag
              :auto-upload="false"
              :limit="1"
              :show-file-list="false"
              accept=".html,.htm,.zip"
              :on-change="handleFileChange"
              :on-exceed="handleExceed"
            >
              <div v-if="selectedFile" class="picked-file">
                <el-icon class="picked-file-icon"><Document /></el-icon>
                <div class="picked-file-meta">
                  <div class="picked-file-name">{{ selectedFile.name }}</div>
                  <div class="picked-file-size">{{ formatFileSize(selectedFile.size) }}</div>
                </div>
                <el-button
                  size="small"
                  circle
                  type="danger"
                  plain
                  icon="Close"
                  class="picked-file-remove"
                  @click.stop="handleRemoveFile"
                />
              </div>
              <div v-else class="upload-empty">
                <el-icon class="upload-empty-icon"><UploadFilled /></el-icon>
                <div class="upload-empty-text">拖拽文件到此处，或 <em>点击选择</em></div>
                <div class="upload-empty-tip">支持 .html / .htm 单文件或 .zip 包，不超过 10MB</div>
              </div>
            </el-upload>
          </el-form-item>

          <el-divider content-position="left">基本信息</el-divider>

          <el-form-item label="标题" prop="title">
            <el-input
              v-model="tempGame.title"
              placeholder="游戏显示标题"
              maxlength="32"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="gameKey" prop="gameKey">
            <el-input
              v-model="tempGame.gameKey"
              placeholder="如 game-2048"
              maxlength="64"
              @input="keyEdited = true"
            />
            <div class="form-tip">
              游戏唯一标识，小写字母开头、3-64 位小写字母/数字/_/-；输入标题后自动生成，可修改。 同
              gameKey 重复上传视为覆盖更新
            </div>
          </el-form-item>

          <el-form-item label="分类" prop="category">
            <el-select v-model="tempGame.category" placeholder="请选择分类" style="width: 100%">
              <el-option
                v-for="opt in GAME_CATEGORY_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="描述">
            <el-input
              v-model="tempGame.description"
              type="textarea"
              :rows="2"
              placeholder="选填，一句话玩法描述"
              maxlength="128"
              show-word-limit
            />
          </el-form-item>

          <el-divider content-position="left">样式与排序</el-divider>

          <el-form-item label="图标" prop="icon">
            <el-select v-model="tempGame.icon" placeholder="请选择图标" style="width: 100%">
              <el-option
                v-for="opt in GAME_ICON_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <div class="form-tip">端上为白名单映射，未知值显示默认图标</div>
          </el-form-item>

          <el-form-item label="色组" prop="color">
            <el-select v-model="tempGame.color" placeholder="请选择色组" style="width: 100%">
              <el-option
                v-for="opt in GAME_COLOR_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              >
                <span class="color-dot" :style="{ backgroundColor: opt.hex }" />
                <span>{{ opt.label }}</span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="角标">
            <el-input
              v-model="tempGame.badge"
              placeholder="选填，如 新 / HOT，空不显示"
              maxlength="16"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="排序" prop="sort">
            <el-input-number
              v-model="tempGame.sort"
              :min="0"
              :max="9999"
              controls-position="right"
            />
            <span class="form-tip-inline">数字越小越靠前</span>
          </el-form-item>

          <el-form-item label="备注">
            <el-input
              v-model="tempGame.remark"
              type="textarea"
              :rows="2"
              placeholder="选填，管理备注"
              maxlength="255"
            />
          </el-form-item>
        </el-form>

        <!-- 上传进度（axios onUploadProgress 实时回传） -->
        <div v-if="uploading" class="progress-panel">
          <el-progress :percentage="uploadPercent" :stroke-width="8" />
          <div class="progress-text">正在上传游戏包…</div>
        </div>
      </template>

      <!-- 成功态：展示可玩地址，新窗口预览 -->
      <div v-else class="upload-success">
        <el-icon class="upload-success-icon"><CircleCheckFilled /></el-icon>
        <div class="upload-success-title">上传成功，游戏已入库</div>
        <div class="upload-success-url">
          <span class="upload-success-label">可玩地址</span>
          <el-link
            v-if="uploadResult.url"
            type="primary"
            :href="uploadResult.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ uploadResult.url }}
          </el-link>
          <span v-else class="upload-success-missing">待后端生成，可在列表编辑中查看</span>
        </div>
        <div class="upload-success-tip">
          可在新窗口预览游戏；确认无误后在列表中打开「下发」开关，App 游戏中心即会展示。
        </div>
      </div>

      <template #footer>
        <template v-if="!uploadResult">
          <el-button :disabled="uploading" @click="uploadDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitUpload">确认上传</el-button>
        </template>
        <template v-else>
          <el-button @click="handleReupload">再传一个</el-button>
          <el-button type="primary" @click="handleFinishUpload">完成</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 编辑弹窗（不含文件；gameKey/来源/文件不在编辑范围，方案 §五） -->
    <el-dialog
      title="编辑游戏"
      v-model="editDialogVisible"
      width="640px"
      @closed="handleEditDialogClosed"
    >
      <el-form ref="editFormRef" :model="tempGame" label-width="90px" :rules="rules">
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="gameKey">
          <el-input :model-value="tempGame.gameKey" disabled />
          <div class="form-tip">游戏唯一标识不可修改；如需更换请删除后重新上传</div>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="tempGame.title"
            placeholder="游戏显示标题"
            maxlength="32"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="分类" prop="category">
          <el-select v-model="tempGame.category" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="opt in GAME_CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="tempGame.description"
            type="textarea"
            :rows="2"
            placeholder="选填，一句话玩法描述"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>

        <el-divider content-position="left">样式与排序</el-divider>

        <el-form-item label="图标" prop="icon">
          <el-select v-model="tempGame.icon" placeholder="请选择图标" style="width: 100%">
            <el-option
              v-for="opt in GAME_ICON_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <div class="form-tip">端上为白名单映射，未知值显示默认图标</div>
        </el-form-item>

        <el-form-item label="色组" prop="color">
          <el-select v-model="tempGame.color" placeholder="请选择色组" style="width: 100%">
            <el-option
              v-for="opt in GAME_COLOR_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            >
              <span class="color-dot" :style="{ backgroundColor: opt.hex }" />
              <span>{{ opt.label }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="角标">
          <el-input
            v-model="tempGame.badge"
            placeholder="选填，如 新 / HOT，空不显示"
            maxlength="16"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="tempGame.sort" :min="0" :max="9999" controls-position="right" />
          <span class="form-tip-inline">数字越小越靠前</span>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="tempGame.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，管理备注"
            maxlength="255"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Document, UploadFilled, CircleCheckFilled } from '@element-plus/icons-vue';
import { getGameList, uploadGame, updateGame, updateGameEnabled, deleteGame } from '@/api/game';
import { formatTime, formatFileSize } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import {
  GAME_CATEGORY_OPTIONS,
  GAME_ICON_OPTIONS,
  GAME_COLOR_OPTIONS,
  gameCategoryText,
  gameCategoryTagType,
  gameSourceTypeText,
  gameSourceTypeTagType,
  gameColorHex,
} from '@/constants/dict';

// 行内防连点状态
const togglingId = ref(null);
const deletingId = ref(null);

// 列表查询与分页：统一由 useTableList 承载（含每页条数切换）
const {
  list: gameList,
  total,
  listLoading,
  listQuery,
  pageSizes,
  fetchData,
  handleSearch,
  resetSearch,
  handlePageChange,
  handleSizeChange,
} = useTableList({
  fetchApi: getGameList,
  baseQuery: {
    keyword: '',
    category: null,
  },
  buildParams: (q) => ({
    ...q,
    // 契约 §2.2：keyword 单参数模糊匹配 title 或 gameKey
    keyword: q.keyword || undefined,
    category: q.category || undefined,
  }),
  errorMsg: '获取游戏列表失败',
});

// ---------- 表单数据（字段名按方案 05 §三 DDL/§五契约，驼峰对齐 AdminGameVo） ----------

const tempGame = reactive({
  id: null,
  gameKey: '',
  title: '',
  category: 'puzzle',
  description: '',
  icon: 'gamepad',
  color: 'green',
  badge: '',
  sort: 100,
  remark: '',
});

// gameKey 规则：小写字母开头、3-64 位小写字母/数字/_/-（与工作台 entryKey 同款正则语言）
const GAME_KEY_PATTERN = /^[a-z][a-z0-9_-]{2,63}$/;

// 验证规则（上传/编辑两弹窗共用；file 项仅上传弹窗消费）。
// file 校验走自定义 validator 读 selectedFile 闭包——form model 中无 file 字段，
// 若用 required 校验 model.file 会因 undefined 恒不通过
const rules = reactive({
  file: [
    {
      validator: (rule, value, callback) => {
        if (!selectedFile.value) {
          callback(new Error('请选择游戏文件'));
        } else {
          callback();
        }
      },
      trigger: 'change',
    },
  ],
  gameKey: [
    { required: true, message: '请输入 gameKey', trigger: 'blur' },
    {
      pattern: GAME_KEY_PATTERN,
      message: '须为小写字母开头、3-64 位小写字母/数字/_/-',
      trigger: 'blur',
    },
  ],
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 32, message: '标题不能超过 32 个字符', trigger: 'blur' },
  ],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
});

// ---------- 上传弹窗 ----------

const uploadDialogVisible = ref(false);
const uploadFormRef = ref(null);
const uploadRef = ref(null);
const selectedFile = ref(null); // 已选文件（el-upload 的 raw File）
const uploading = ref(false);
const uploadPercent = ref(0); // 上传进度 0-100
const uploadResult = ref(null); // 上传成功返回的 AdminGameVo（含 url 可玩地址）
const keyEdited = ref(false); // 用户手动改过 gameKey 后停止自动生成
const uploadKeySeed = ref(''); // 本次弹窗自动 gameKey 的时间戳尾巴，弹窗期间保持稳定

// 前端预检：扩展名白名单 + 10MB 上限（方案 §四；后端仍有 413 兜底）
const MAX_FILE_SIZE = 10 * 1024 * 1024;
// 契约 §2.1：扩展名白名单 .html/.htm/.zip
const ALLOWED_EXTENSIONS = ['.html', '.htm', '.zip'];

const validateFile = (raw) => {
  const dotIndex = raw.name.lastIndexOf('.');
  const ext = dotIndex >= 0 ? raw.name.slice(dotIndex).toLowerCase() : '';
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return '仅支持 .html / .htm / .zip 文件';
  }
  if (raw.size > MAX_FILE_SIZE) {
    return '文件大小不能超过 10MB';
  }
  return null;
};

// 选中文件（含超限替换）：校验失败清空并提示
const handleFileChange = (uploadFile) => {
  const raw = uploadFile?.raw;
  if (!raw) return;
  const error = validateFile(raw);
  if (error) {
    ElMessage.error(error);
    uploadRef.value?.clearFiles();
    selectedFile.value = null;
    return;
  }
  selectedFile.value = raw;
  // 手动触发 file 项校验，清除上次的「请选择游戏文件」错误
  uploadFormRef.value?.validateField('file').catch(() => {});
};

// limit=1 下再次选择：替换为最新文件（Element Plus 官方推荐写法，handleStart 会触发 on-change）
const handleExceed = (files) => {
  uploadRef.value?.clearFiles();
  const file = files[0];
  if (file) {
    uploadRef.value?.handleStart(file);
  }
};

// 移除已选文件：清空后触发 file 项校验，让「请选择游戏文件」提示即时出现
const handleRemoveFile = () => {
  uploadRef.value?.clearFiles();
  selectedFile.value = null;
  uploadFormRef.value?.validateField('file').catch(() => {});
};

// 标题 → gameKey 自动生成：提取英文数字段小写拼接；非法/过短回退 game-{seed}
const slugFromTitle = (title) => {
  const segs = (title || '').toLowerCase().match(/[a-z0-9]+/g) || [];
  const slug = segs.join('-');
  return /^[a-z]/.test(slug) && slug.length >= 3
    ? slug.slice(0, 63)
    : `game-${uploadKeySeed.value}`;
};

// 输入标题自动生成 gameKey（用户手动改过后不再覆盖）
watch(
  () => tempGame.title,
  (title) => {
    if (uploadDialogVisible.value && !keyEdited.value) {
      tempGame.gameKey = slugFromTitle(title);
    }
  }
);

// 打开上传弹窗
const handleCreate = () => {
  uploadDialogVisible.value = true;
  uploadResult.value = null;
  selectedFile.value = null;
  keyEdited.value = false;
  uploadKeySeed.value = Date.now().toString(36);
  Object.assign(tempGame, {
    id: null,
    gameKey: '',
    title: '',
    category: 'puzzle',
    description: '',
    icon: 'gamepad',
    color: 'green',
    badge: '',
    sort: 100,
    remark: '',
  });
};

// 弹窗关闭 — 清除验证状态与文件
const handleUploadDialogClosed = () => {
  uploadFormRef.value?.resetFields();
  uploadFormRef.value?.clearValidate();
  selectedFile.value = null;
  uploadResult.value = null;
  uploadPercent.value = 0;
};

// 提交上传：表单校验 → 组装 multipart（file + 表单字段）→ 进度回传
const submitUpload = async () => {
  try {
    await uploadFormRef.value.validate();
  } catch {
    return; // 校验失败：el-form 已展示字段错误
  }
  if (!selectedFile.value) {
    ElMessage.error('请选择游戏文件');
    return;
  }

  const formData = new FormData();
  formData.append('file', selectedFile.value);
  formData.append('title', tempGame.title);
  formData.append('gameKey', tempGame.gameKey);
  formData.append('category', tempGame.category);
  formData.append('description', tempGame.description);
  formData.append('icon', tempGame.icon);
  formData.append('color', tempGame.color);
  formData.append('badge', tempGame.badge);
  formData.append('sort', String(tempGame.sort ?? 100));
  formData.append('remark', tempGame.remark);

  uploading.value = true;
  uploadPercent.value = 0;
  try {
    const res = await uploadGame(formData, (e) => {
      if (e.total) {
        uploadPercent.value = Math.round((e.loaded / e.total) * 100);
      }
    });
    uploadResult.value = res;
    ElMessage.success('上传成功');
    fetchData(); // 刷新列表（同 gameKey 覆盖场景也能看到最新元数据）
  } catch (error) {
    // 409 gameKey 冲突 / 413 超限 / 403 鉴权等由后端 message 透出
    ElMessage.error(error.message || '上传失败');
  } finally {
    uploading.value = false;
  }
};

// 成功态 → 再传一个：回到表单态保留已填字段；
// title 未变时 watch 不会触发，gameKey 需手动重生成
const handleReupload = () => {
  uploadResult.value = null;
  selectedFile.value = null;
  uploadPercent.value = 0;
  keyEdited.value = false;
  uploadKeySeed.value = Date.now().toString(36);
  tempGame.gameKey = slugFromTitle(tempGame.title);
};

// 成功态 → 完成：关闭弹窗
const handleFinishUpload = () => {
  uploadDialogVisible.value = false;
};

// ---------- 行内下发开关 ----------

const handleToggleEnabled = async (row) => {
  // el-switch 触发 change 时 v-model 已同步更新为目标值（Element Plus 源码 UPDATE 先于 CHANGE emit），
  // row.enabled 即用户意图值；失败时手动翻转回滚
  const next = row.enabled;
  togglingId.value = row.id;
  try {
    await updateGameEnabled(row.id, next);
    ElMessage.success(next === 1 ? '已下发至用户端' : '已停止下发');
  } catch (error) {
    row.enabled = next === 1 ? 0 : 1; // 失败回滚开关状态
    ElMessage.error(error.message || '状态切换失败');
  } finally {
    togglingId.value = null;
  }
};

// ---------- 编辑弹窗 ----------

const editDialogVisible = ref(false);
const editFormRef = ref(null);
const submitting = ref(false);

// 打开编辑弹窗（仅可编辑字段进表单；gameKey 只读展示）
const handleEdit = (row) => {
  editDialogVisible.value = true;
  Object.assign(tempGame, {
    id: row.id,
    gameKey: row.gameKey,
    title: row.title,
    category: row.category || 'puzzle',
    description: row.description || '',
    icon: row.icon || 'gamepad',
    color: row.color || 'green',
    badge: row.badge || '',
    sort: row.sort ?? 100,
    remark: row.remark || '',
  });
};

// 弹窗关闭 — 清除验证状态
const handleEditDialogClosed = () => {
  editFormRef.value?.resetFields();
  editFormRef.value?.clearValidate();
};

// 提交编辑：按方案 §五契约，PUT 只携带可编辑字段（不含 gameKey/sourceType/文件）
const submitEdit = async () => {
  submitting.value = true;
  try {
    await editFormRef.value.validate();
    await updateGame({
      id: tempGame.id,
      title: tempGame.title,
      description: tempGame.description,
      icon: tempGame.icon,
      color: tempGame.color,
      category: tempGame.category,
      badge: tempGame.badge,
      sort: tempGame.sort,
      remark: tempGame.remark,
    });
    ElMessage.success('修改成功');
    editDialogVisible.value = false;
    fetchData();
  } catch (error) {
    // 表单校验失败（字段错误已展示）与接口报错分开提示
    if (error?.message) {
      ElMessage.error(error.message);
    }
  } finally {
    submitting.value = false;
  }
};

// ---------- 删除 ----------

const handleDelete = async (row) => {
  // 确认弹窗：明确说明会同时清除服务器上的游戏文件（confirmation-dialogs）
  const confirmed = await useConfirm(
    `确认删除游戏「${row.title}」吗？将同时清除服务器上的游戏文件，删除后端上不再展示且不可恢复。`
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteGame(row.id);
    ElMessage.success('删除成功');
    fetchData(); // 刷新当前页
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  } finally {
    deletingId.value = null;
  }
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* 游戏 + 描述两行单元格 */
.title-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-main {
  color: #0f172a;
  font-weight: 500;
}

.title-sub {
  color: #94a3b8;
  font-size: 12px;
}

/* 图标色块（端上按色组 key 渲染 16dp 圆角块，此处用色点示意） */
.icon-block {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 4px;
  flex-shrink: 0;
}

/* gameKey 等宽字体（对齐跳转路由页 host 单元格） */
.key-text {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  color: #334155;
}

/* 色块圆点（表格与下拉选项共用） */
.color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  vertical-align: -1px;
  margin-right: 6px;
}

/* 表单项下的辅助说明 */
.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

/* 开关同行的行内说明 */
.form-tip-inline {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}

/* 拖拽上传区：占满表单行，hover/dragover 高亮走全局 motion token */
.game-uploader {
  width: 100%;
}

.game-uploader :deep(.el-upload-dragger) {
  width: 100%;
  padding: 24px 16px;
  transition:
    border-color var(--motion-fast) var(--ease-standard),
    background-color var(--motion-fast) var(--ease-standard);
}

.game-uploader :deep(.el-upload-dragger.is-dragover) {
  background-color: #eef2ff;
}

.upload-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
}

.upload-empty-icon {
  font-size: 36px;
  color: #a5b4fc;
}

.upload-empty-text {
  font-size: 14px;
  color: #334155;
}

.upload-empty-text em {
  font-style: normal;
  color: #6366f1;
}

.upload-empty-tip {
  font-size: 12px;
  color: #94a3b8;
}

/* 已选文件卡片 */
.picked-file {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 4px;
}

.picked-file-icon {
  font-size: 28px;
  color: #6366f1;
  flex-shrink: 0;
}

.picked-file-meta {
  flex: 1;
  min-width: 0;
  text-align: left;
}

.picked-file-name {
  font-size: 14px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picked-file-size {
  font-size: 12px;
  color: #94a3b8;
}

.picked-file-remove {
  flex-shrink: 0;
}

/* 上传进度面板 */
.progress-panel {
  margin-top: 4px;
  padding: 12px 16px;
  background-color: #f8fafc;
  border-radius: 8px;
}

.progress-text {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  text-align: center;
}

/* 上传成功面板 */
.upload-success {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 16px;
}

.upload-success-icon {
  font-size: 48px;
  color: #10b981;
}

.upload-success-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.upload-success-url {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}

.upload-success-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #64748b;
}

.upload-success-tip {
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  line-height: 1.6;
}

.upload-success-missing {
  font-size: 13px;
  color: #94a3b8;
}
</style>
