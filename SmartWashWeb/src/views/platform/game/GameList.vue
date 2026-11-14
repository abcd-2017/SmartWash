<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="listQuery.keyword"
            placeholder="标题 / gameKey 模糊搜索"
            clearable
            style="width: 220px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="listQuery.category"
            placeholder="全部分类"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in GAME_CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select
            v-model="listQuery.sourceType"
            placeholder="全部来源"
            clearable
            style="width: 110px"
            @change="handleSearch"
          >
            <el-option label="上传" :value="1" />
            <el-option label="外链" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="下发">
          <el-select
            v-model="listQuery.enabled"
            placeholder="全部"
            clearable
            style="width: 100px"
            @change="handleSearch"
          >
            <el-option label="已下发" :value="1" />
            <el-option label="未下发" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCreate">上传游戏</el-button>
          <el-button
            :disabled="!selectedRows.length"
            :loading="batchToggling"
            @click="handleBatchEnabled(1)"
          >
            批量下发
          </el-button>
          <el-button
            :disabled="!selectedRows.length"
            :loading="batchToggling"
            @click="handleBatchEnabled(0)"
          >
            批量取消下发
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="gameList"
        fit
        highlight-current-row
        :empty-text="
          hasGameFilter ? '暂无数据，试试调整筛选条件' : '还没有游戏，点击右上角上传或批量导入开始'
        "
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column label="游戏" min-width="220">
          <template #default="{ row }">
            <div class="title-cell">
              <div class="title-row">
                <!-- 封面缩略图(有则展示,无则用色块占位) -->
                <img
                  v-if="row.coverUrl"
                  class="cover-thumb"
                  :src="row.coverUrl"
                  :alt="row.title || '封面'"
                  loading="lazy"
                />
                <!-- 端上按 icon/color key 白名单映射，色块直观展示色组、tag 展示 icon key 便于两端对齐 -->
                <span
                  v-else
                  class="icon-block"
                  :style="{ backgroundColor: gameColorHex(row.color) }"
                />
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
        <el-table-column label="下发" min-width="170">
          <template #default="{ row }">
            <div class="enabled-cell">
              <!-- 行内切换即「选择哪些下发」：成功前乐观更新，失败回滚并提示 -->
              <el-switch
                v-model="row.enabled"
                :active-value="1"
                :inactive-value="0"
                :loading="togglingId === row.id"
                @change="handleToggleEnabled(row)"
              />
              <!-- 后端下发会过滤空 url 的游戏包：已勾选下发但缺可玩地址时如实提示 -->
              <el-tooltip
                v-if="row.enabled === 1 && !row.url"
                content="已下发但不可玩：缺少游戏包地址，端上下发时会被过滤，请重新上传游戏包"
                placement="top"
              >
                <el-tag type="warning" size="small" effect="plain">已下发但不可玩</el-tag>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <!-- 更新时间（兜底 createdAt 防后端裁剪字段，对齐 WorkbenchEntryList 先例） -->
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              link
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
                <!-- icon 必须传组件（el-button 内部用 resolveDynamicComponent 解析，
                     本仓库按需引入未全局注册图标，传字符串会渲染成空图标） -->
                <el-button
                  size="small"
                  circle
                  type="danger"
                  plain
                  :icon="Close"
                  aria-label="移除文件"
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

            <!-- 自动填表入口必须放在 el-upload 之外：
                 el-upload 根 div 自带 onClick → 点开它自己的 file input（accept=.html,.htm,.zip）。
                 放在它的插槽里时，点击会冒泡到那一层，弹出来的是它的文件选择器（实测：点「选择游戏目录」
                 弹的确实是 el-upload__input），目录入口形同失效。移出后两条路径互不干扰。 -->
            <div class="upload-alt">
              <span class="upload-alt-divider">或</span>
              <el-button size="small" @click="handlePickDirectory"
                >选择游戏目录（自动填表）</el-button
              >
              <el-button size="small" @click="handlePickReadme">选 README.md 自动填表</el-button>
              <el-button size="small" @click="handleOpenBatchUpload"
                >批量导入（选父目录）</el-button
              >
              <input
                ref="dirInputRef"
                class="dir-input"
                type="file"
                webkitdirectory
                multiple
                @change="handleDirImport"
              />
              <input
                ref="readmeInputRef"
                class="dir-input"
                type="file"
                accept="README.md,.md"
                @change="handleReadmePick"
              />
            </div>

            <!-- 目录携带 levels.json：勾选随上传自动导入；解析失败仅提示，游戏本体照常上传 -->
            <el-alert
              v-if="levelsDraft && !levelsDraft.error"
              class="levels-draft-alert"
              type="info"
              :closable="false"
              show-icon
            >
              <template #title>
                识别到关卡文件 levels.json（{{ levelsDraft.plan?.rows.length || 0 }} 关）
              </template>
              <el-checkbox v-model="levelsDraft.checked" class="levels-draft-check">
                上传成功后自动导入{{ levelsDispositionText }}
              </el-checkbox>
            </el-alert>
            <el-alert
              v-else-if="levelsDraft?.error"
              class="levels-draft-alert"
              type="warning"
              :closable="false"
              show-icon
              :title="`识别到 levels.json 但未随上传导入：${levelsDraft.error}`"
            />
          </el-form-item>

          <el-form-item label="封面图">
            <el-upload
              ref="coverUploadRef"
              class="cover-uploader"
              :auto-upload="false"
              :limit="1"
              :show-file-list="false"
              accept="image/png,image/jpeg,image/webp"
              :on-change="handleCoverChange"
              :on-exceed="handleCoverExceed"
            >
              <div v-if="coverPreview" class="cover-preview">
                <img :src="coverPreview" alt="封面预览" />
                <el-button
                  size="small"
                  circle
                  type="danger"
                  plain
                  :icon="Close"
                  aria-label="移除封面"
                  class="cover-remove"
                  @click.stop="handleCoverRemove"
                />
              </div>
              <div v-else class="upload-empty cover-empty">
                <el-icon class="upload-empty-icon"><Picture /></el-icon>
                <div class="upload-empty-text">上传封面图（选填）</div>
                <div class="upload-empty-tip">
                  png / jpeg / webp，≤5MB；留空则端上用本地兜底封面
                </div>
              </div>
            </el-upload>
            <div class="form-tip">
              目录自动填表时若含封面图（cover.png / 游戏名.png）会自动识别为封面
            </div>
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
            <el-alert
              v-if="keyExists"
              class="key-conflict-alert"
              type="warning"
              :closable="false"
              show-icon
              title="该 gameKey 已存在：本次提交为覆盖更新，包文件与元数据将被替换，上架状态与排序保留"
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
        <!-- 关卡跟随导入结果：失败不回滚游戏上传，可到关卡管理重试 -->
        <div
          v-if="levelsDraft?.checked && !levelsDraft.error"
          class="upload-success-levels"
          :class="{ 'is-loading': levelsImporting }"
        >
          <template v-if="levelsImporting">正在导入关卡…</template>
          <template v-else-if="levelsResult?.ok">
            关卡已随本次上传导入：{{ levelsResult.created + levelsResult.updated }} 关（新增
            {{ levelsResult.created }} · 覆盖 {{ levelsResult.updated }}）
          </template>
          <template v-else-if="levelsResult">
            关卡导入失败：{{ levelsResult.message }}。可到「关卡管理」用同一 JSON 重新导入
          </template>
        </div>
      </div>

      <template #footer>
        <template v-if="!uploadResult">
          <el-button :disabled="uploading" @click="uploadDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitUpload">
            {{ keyExists ? '覆盖更新' : '确认上传' }}
          </el-button>
        </template>
        <template v-else>
          <el-button @click="handleReupload">再传一个</el-button>
          <el-button type="primary" @click="handleFinishUpload">完成</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗：选父目录 → 预检 → 串行上传（规范校验/README 解析与单目录导入同源） -->
    <BatchUploadDialog v-model="batchDialogVisible" @success="fetchData" />

    <!-- 编辑弹窗（元数据 + 可选更换游戏包；gameKey/来源不可改，方案 §五） -->
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

        <el-form-item label="游戏文件">
          <!-- 可选「更换游戏包」：不选文件 = 仅改元数据；选文件 = 以同 gameKey 覆盖上传。
               拖拽区 hide 内部文件列表，选中后自绘文件卡片（与上传弹窗同形态） -->
          <el-upload
            ref="editUploadRef"
            class="game-uploader"
            drag
            :auto-upload="false"
            :limit="1"
            :show-file-list="false"
            accept=".html,.htm,.zip"
            :on-change="handleEditFileChange"
            :on-exceed="handleEditFileExceed"
          >
            <div v-if="editFile" class="picked-file">
              <el-icon class="picked-file-icon"><Document /></el-icon>
              <div class="picked-file-meta">
                <div class="picked-file-name">{{ editFile.name }}</div>
                <div class="picked-file-size">{{ formatFileSize(editFile.size) }}</div>
              </div>
              <el-button
                size="small"
                circle
                type="danger"
                plain
                :icon="Close"
                aria-label="移除文件"
                class="picked-file-remove"
                @click.stop="handleEditFileRemove"
              />
            </div>
            <div v-else class="upload-empty">
              <div class="upload-empty-text">可选：拖拽新游戏包到此处覆盖上传</div>
              <div class="upload-empty-tip">
                不选文件则仅修改元数据；选文件会以当前 gameKey 覆盖上传（.html / .htm /
                .zip，≤10MB）
              </div>
            </div>
          </el-upload>
        </el-form-item>

        <el-form-item label="封面图">
          <el-upload
            ref="editCoverUploadRef"
            class="cover-uploader"
            :auto-upload="false"
            :limit="1"
            :show-file-list="false"
            accept="image/png,image/jpeg,image/webp"
            :on-change="handleCoverChange"
            :on-exceed="handleCoverExceed"
          >
            <div v-if="coverPreview" class="cover-preview">
              <img :src="coverPreview" alt="封面预览" />
              <el-button
                size="small"
                circle
                type="danger"
                plain
                :icon="Close"
                aria-label="移除封面"
                class="cover-remove"
                @click.stop="handleCoverRemove"
              />
            </div>
            <div v-else class="upload-empty cover-empty">
              <el-icon class="upload-empty-icon"><Picture /></el-icon>
              <div class="upload-empty-text">上传封面图（选填）</div>
              <div class="upload-empty-tip">png / jpeg / webp，≤5MB；留空则端上用本地兜底封面</div>
            </div>
          </el-upload>
          <div class="form-tip">
            目录自动填表时若含封面图（cover.png / 游戏名.png）会自动识别为封面
          </div>
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
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Document, UploadFilled, CircleCheckFilled, Close, Picture } from '@element-plus/icons-vue';
import {
  getGameList,
  uploadGame,
  updateGame,
  updateGameEnabled,
  batchUpdateGameEnabled,
  deleteGame,
  uploadGameCover,
} from '@/api/game';
import { batchImportGameLevels, getGameLevels } from '@/api/gameData';
import { formatTime, formatFileSize } from '@/utils/format';
import logger from '@/utils/logger';
import { useTableList } from '@/composables/useTableList';
import { useConfirm } from '@/composables/useConfirm';
import BatchUploadDialog from './components/BatchUploadDialog.vue';
import { relSegs, inspectGameDir, parseGameReadme, GAME_KEY_PATTERN } from './gameDirectory';
import {
  MAX_IMPORT_FILE_SIZE,
  parseLevelImportText,
  planLevelImport,
  buildChunkedPayloads,
} from './levelImportParser';
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
    sourceType: null,
    enabled: null,
  },
  buildParams: (q) => ({
    ...q,
    // 契约 §2.2：keyword 单参数模糊匹配 title 或 gameKey；
    // sourceType/enabled 用 ?? 清洗（enabled=0 未下发是合法筛选值，|| 会误清）
    keyword: q.keyword || undefined,
    category: q.category || undefined,
    sourceType: q.sourceType ?? undefined,
    enabled: q.enabled ?? undefined,
  }),
  errorMsg: '获取游戏列表失败',
});

// 空态区分：无任何筛选条件视为「库空」（引导上传/批量导入），有筛选时提示调整条件
const hasGameFilter = computed(
  () =>
    Boolean((listQuery.keyword || '').trim()) ||
    (listQuery.category ?? '') !== '' ||
    (listQuery.sourceType ?? '') !== '' ||
    (listQuery.enabled ?? '') !== ''
);

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

// gameKey 规则与 README 解析已收敛到 ./gameDirectory（与批量导入同源），
// GAME_KEY_PATTERN/GAME_KEY_SOURCE/parseGameReadme 从该模块导入

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
// 封面图:本地选中文件 + 预览 URL + 已上传直链(编辑态回填)
const coverFile = ref(null);
const coverPreview = ref('');
const coverUrl = ref('');

const COVER_ACCEPT = ['image/png', 'image/jpeg', 'image/webp'];
const COVER_MAX_BYTES = 5 * 1024 * 1024;

// 封面选中:校验类型/大小后保留 raw File 并生成预览
const handleCoverChange = (uploadFile) => {
  const raw = uploadFile && uploadFile.raw;
  if (!raw) return;
  const type = (raw.type || '').toLowerCase();
  if (!COVER_ACCEPT.includes(type)) {
    ElMessage.error('封面仅支持 png / jpeg / webp 图片');
    return;
  }
  if (raw.size > COVER_MAX_BYTES) {
    ElMessage.error('封面大小不能超过 5MB');
    return;
  }
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value);
  coverFile.value = raw;
  coverPreview.value = URL.createObjectURL(raw);
};

const handleCoverRemove = () => {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value);
  coverFile.value = null;
  coverPreview.value = '';
  coverUrl.value = '';
};

const handleCoverExceed = () => {
  ElMessage.error('最多只能选择 1 张封面图');
};

// 封面上传(可选):成功返回直链;失败抛错由调用方中断提交
const uploadCoverIfNeeded = async () => {
  if (!coverFile.value) return coverUrl.value || '';
  const url = await uploadGameCover(coverFile.value);
  coverUrl.value = url;
  return url;
};
const uploading = ref(false);
const uploadPercent = ref(0); // 上传进度 0-100
const uploadResult = ref(null); // 上传成功返回的 AdminGameVo（含 url 可玩地址）
const keyEdited = ref(false); // 用户手动改过 gameKey 后停止自动生成
const uploadKeySeed = ref(''); // 本次弹窗自动 gameKey 的时间戳尾巴，弹窗期间保持稳定
const keyExists = ref(false); // gameKey 冲突实时检查结果：true = 提交将覆盖更新已存在的游戏
let keyCheckTimer = null; // 冲突检查防抖定时器

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
    logger.debug('游戏文件预检未通过，已清空选择:', error);
    ElMessage.error(error);
    uploadRef.value?.clearFiles();
    selectedFile.value = null;
    return;
  }
  selectedFile.value = raw;
  // 手动选文件脱离了目录上下文：清除选目录时带来的关卡草稿，避免误导入
  levelsDraft.value = null;
  levelsResult.value = null;
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

// ---------- 自动填表：选目录（部分浏览器）/ 选 README.md（全浏览器） ----------
// 两者都服务 games-library 标准结构（index.html + 可选 README.md），最终汇到 applyReadmeFields。

const dirInputRef = ref(null);
const readmeInputRef = ref(null);

// ---------- 关卡文件跟随导入（选目录时识别 levels.json，随上传自动导入） ----------
// draft 为 null 表示本次上传不带关卡；error 非空时仅提示、不随上传导入（游戏本体不受影响）
const levelsDraft = ref(null); // { fileName, items, plan, checked, error }
const levelsImporting = ref(false);
const levelsResult = ref(null); // { ok, created, updated, message }：成功态面板展示

// 「覆盖 X · 新增 Y」分布文案（计划未算出时为空）
const levelsDispositionText = computed(() => {
  const plan = levelsDraft.value?.plan;
  if (!plan) return '';
  if (plan.created && plan.updated) return `（新增 ${plan.created} 关 · 覆盖 ${plan.updated} 关）`;
  if (plan.created) return `（新增 ${plan.created} 关）`;
  if (plan.updated) return `（覆盖 ${plan.updated} 关）`;
  return '';
});

/**
 * 按当前 gameKey 计算关卡处置分布：keyExists 时拉现有关卡按序号对齐（撞号覆盖、其余新增），
 * 全新游戏按空表顺延分配序号。拉取失败保持空表顺延的分布（裸数据从 1 起，信封显式序号不受影响）。
 */
const refreshLevelsPlan = async () => {
  const draft = levelsDraft.value;
  if (!draft || draft.error) return;
  let plan = planLevelImport(draft.items, []);
  if (keyExists.value) {
    const keyAtFetch = tempGame.gameKey;
    try {
      const existingLevels = await getGameLevels(tempGame.gameKey);
      // 防抖窗口内 gameKey 又被改过：丢弃迟到响应，等 keyExists 重查后再对齐
      if (tempGame.gameKey === keyAtFetch) {
        plan = planLevelImport(draft.items, existingLevels);
      }
    } catch (err) {
      logger.error('现有关卡获取失败，按空表顺延展示:', tempGame.gameKey, err);
    }
  }
  levelsDraft.value = { ...draft, plan };
};

// keyExists（防抖重查完成）或所选目录变化后重算分布；draft.items 引用不变，不会自触发
watch([keyExists, () => levelsDraft.value?.items], () => {
  refreshLevelsPlan();
});

const handlePickDirectory = () => {
  dirInputRef.value?.click();
};

const handlePickReadme = () => {
  readmeInputRef.value?.click();
};

/**
 * README 解析结果落表单：gameKey 不再被标题 watch 覆盖；
 * 目录名缺失（只选了 README）时按标题生成，避免 gameKey 留空卡在必填校验。
 */
const applyReadmeFields = (parsed) => {
  Object.assign(tempGame, parsed);
  keyEdited.value = true;
  if (!tempGame.gameKey) {
    tempGame.gameKey = slugFromTitle(tempGame.title);
  }
  uploadFormRef.value?.clearValidate(['title', 'gameKey', 'category']);
};

// 只选 README.md（全浏览器降级路径）：不碰上传文件，只填表
const handleReadmePick = (event) => {
  const file = event.target.files?.[0];
  event.target.value = ''; // 允许重复选择同一文件
  if (!file) return;
  if (!/^readme\.md$/i.test(file.name)) {
    logger.debug('README 自动填表：所选文件非 README.md:', file.name);
    ElMessage.error('请选择 README.md');
    return;
  }
  // 只选 README 路径没有目录上下文：清除可能残留的关卡草稿
  levelsDraft.value = null;
  levelsResult.value = null;
  file
    .text()
    .then((text) => {
      applyReadmeFields(parseGameReadme(text, ''));
      ElMessage.success('已按 README 填表，请核对后提交');
    })
    .catch((readError) => {
      logger.error('README 文件读取失败:', readError);
      ElMessage.error('README 读取失败，请手动填写表单');
    });
};

// 选择目录：结构校验收敛到 gameDirectory.inspectGameDir（与批量导入同源规范）；
// index.html 作为上传文件，README 解析后预填表单（匹配不到的字段留空），封面图自动识别，
// levels.json 解析后进入关卡草稿（勾选随上传自动导入，解析失败仅提示不阻断）
const handleDirImport = (event) => {
  const files = Array.from(event.target.files || []);
  event.target.value = ''; // 允许重复选择同一目录
  if (!files.length) return;

  const dirName = relSegs(files[0])[0] || 'game';
  const inspect = inspectGameDir(dirName, files, 1);
  if (!inspect.ok) {
    logger.debug('游戏目录导入预检未通过：', inspect.reason);
    ElMessage.error(inspect.reason);
    return;
  }
  const fileError = validateFile(inspect.indexHtml);
  if (fileError) {
    logger.debug('游戏目录导入预检未通过：', fileError);
    ElMessage.error(fileError);
    return;
  }

  selectedFile.value = inspect.indexHtml;
  uploadFormRef.value?.validateField('file').catch(() => {});

  // 目录内封面自动识别:cover.png / <game-key>.png 等图片(取首个)
  if (inspect.coverFile) {
    handleCoverChange({ raw: inspect.coverFile });
    ElMessage.success('已识别目录中的封面图');
  }

  // levels.json（可选）：解析预览供勾选与处置展示；失败仅提示，游戏本体照常上传
  levelsResult.value = null;
  if (!inspect.levelsFile) {
    levelsDraft.value = null;
  } else if (inspect.levelsFile.size > MAX_IMPORT_FILE_SIZE) {
    levelsDraft.value = {
      checked: false,
      items: null,
      plan: null,
      error: `关卡文件超过 ${MAX_IMPORT_FILE_SIZE / 1024 / 1024}MB 上限`,
    };
  } else {
    inspect.levelsFile
      .text()
      .then((text) => {
        const parsed = parseLevelImportText(text);
        if (!parsed.ok) {
          levelsDraft.value = { checked: false, items: null, plan: null, error: parsed.reason };
          return;
        }
        levelsDraft.value = {
          checked: true,
          fileName: inspect.levelsFile.name,
          items: parsed.items,
          plan: null,
          error: '',
        };
        return refreshLevelsPlan();
      })
      .catch((readError) => {
        logger.error('levels.json 读取失败:', readError);
        levelsDraft.value = { checked: false, items: null, plan: null, error: '关卡文件读取失败' };
      });
  }

  // README 可选：无则字段留空（gameKey 仍取目录名），有则解析预填
  if (!inspect.readme) {
    applyReadmeFields({ title: '', gameKey: dirName, category: '', description: '' });
    return;
  }
  inspect.readme
    .text()
    .then((text) => {
      applyReadmeFields(parseGameReadme(text, dirName));
      ElMessage.success('已按 README 填表，请核对后提交');
    })
    .catch((readError) => {
      logger.error('README 文件读取失败:', readError);
      ElMessage.error('README 读取失败，请手动填写表单');
    });
};

// 移除已选文件：清空后触发 file 项校验，让「请选择游戏文件」提示即时出现
const handleRemoveFile = () => {
  uploadRef.value?.clearFiles();
  selectedFile.value = null;
  // 文件卡片移除后目录上下文不再完整，关卡草稿一并撤下
  levelsDraft.value = null;
  levelsResult.value = null;
  uploadFormRef.value?.validateField('file').catch(() => {});
};

// 标题 → gameKey 自动生成：提取英文数字段小写拼接，数字开头补 game- 前缀（如 2048 → game-2048），
// 同标题确定性地生成同一 gameKey，重复上传自动落到覆盖更新；纯中文等无可用字符时才回退 game-{seed}
const slugFromTitle = (title) => {
  const segs = (title || '').toLowerCase().match(/[a-z0-9]+/g) || [];
  const raw = segs.join('-');
  if (!raw) {
    return `game-${uploadKeySeed.value}`;
  }
  const slug = /^[a-z]/.test(raw) ? raw : `game-${raw}`;
  return slug.length >= 3 ? slug.slice(0, 63) : `game-${uploadKeySeed.value}`;
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

// gameKey 冲突实时检查：防抖查询列表（keyword 模糊后前端精确比对），命中即提示覆盖语义，
// 让「新增还是更新」在上传前可见；检查失败保持 false 静默，不阻断上传（后端唯一索引与覆盖语义兜底）
watch(
  () => tempGame.gameKey,
  (key) => {
    if (!uploadDialogVisible.value) return;
    keyExists.value = false;
    clearTimeout(keyCheckTimer);
    if (!key || key.length < 3) return;
    keyCheckTimer = setTimeout(async () => {
      try {
        const res = await getGameList({ page: 1, size: 100, keyword: key });
        keyExists.value = (res.records || []).some((r) => r.gameKey === key);
      } catch {
        // 宁可少提示也不误报
      }
    }, 400);
  }
);

// 打开上传弹窗
const handleCreate = () => {
  uploadDialogVisible.value = true;
  uploadResult.value = null;
  selectedFile.value = null;
  handleCoverRemove();
  keyEdited.value = false;
  levelsDraft.value = null;
  levelsResult.value = null;
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
  keyExists.value = false;
  levelsDraft.value = null;
  levelsResult.value = null;
  levelsImporting.value = false;
  clearTimeout(keyCheckTimer);
  // 与 handleEditDialogClosed 对齐：清掉封面选中态，避免残留带入下一次上传
  handleCoverRemove();
};

// 提交上传：表单校验 → 组装 multipart（file + 表单字段）→ 进度回传
const submitUpload = async () => {
  try {
    await uploadFormRef.value.validate();
  } catch {
    logger.debug('上传表单校验未通过，已拦截提交');
    return; // 校验失败：el-form 已展示字段错误
  }
  if (!selectedFile.value) {
    logger.debug('上传提交时未选择游戏文件');
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

  // 封面(可选):先上传取直链再回填;上传失败则中断提交
  try {
    formData.append('coverUrl', await uploadCoverIfNeeded());
  } catch (err) {
    logger.error('封面上传失败:', err);
    ElMessage.error('封面上传失败，请稍后重试');
    uploading.value = false;
    return;
  }

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
    // 低频关键写操作成功留痕，业务键仅 gameKey/id，不含文件内容与请求体（敏感红线）
    logger.info('游戏上传成功', `gameKey=${tempGame.gameKey}`, `id=${res?.id ?? '-'}`);

    // 关卡跟随导入（勾选草稿）：先游戏后关卡（后端 requireGame 要求游戏先入库），
    // 按 500 一批串行提交；失败不回滚游戏上传，成功态面板独立展示，可到关卡管理重试
    if (levelsDraft.value?.checked && !levelsDraft.value.error && levelsDraft.value.plan) {
      levelsImporting.value = true;
      try {
        let created = 0;
        let updated = 0;
        const rows = levelsDraft.value.plan.rows.filter((r) => r.fate !== 'error');
        for (const payload of buildChunkedPayloads(rows, {
          gameKey: tempGame.gameKey,
          replaceAll: false,
        })) {
          const importRes = await batchImportGameLevels(payload);
          created += importRes.created || 0;
          updated += importRes.updated || 0;
        }
        levelsResult.value = { ok: true, created, updated };
        logger.info(
          '关卡跟随导入成功',
          `gameKey=${tempGame.gameKey}`,
          `新增=${created}`,
          `覆盖=${updated}`
        );
      } catch (importError) {
        logger.error('关卡跟随导入失败:', `gameKey=${tempGame.gameKey}`, importError);
        levelsResult.value = { ok: false, message: importError.message || '关卡导入失败' };
      } finally {
        levelsImporting.value = false;
      }
    }
    fetchData(); // 刷新列表（同 gameKey 覆盖场景也能看到最新元数据）
  } catch (error) {
    logger.error('游戏上传失败:', `gameKey=${tempGame.gameKey}`, error);
    // 409 gameKey 冲突 / 413 超限 / 403 鉴权等由后端 message 透出
    ElMessage.error(error.message || '上传失败');
  } finally {
    uploading.value = false;
  }
};

// 成功态 → 再传一个：回到表单态保留已填字段；
// title 未变时 watch 不会触发，gameKey 需手动重生成；
// 封面与 handleCreate 同步清位，避免上一局的封面静默带入下一个游戏
const handleReupload = () => {
  uploadResult.value = null;
  selectedFile.value = null;
  handleCoverRemove();
  uploadPercent.value = 0;
  keyEdited.value = false;
  levelsDraft.value = null;
  levelsResult.value = null;
  uploadKeySeed.value = Date.now().toString(36);
  tempGame.gameKey = slugFromTitle(tempGame.title);
};

// 成功态 → 完成：关闭弹窗
const handleFinishUpload = () => {
  uploadDialogVisible.value = false;
};

// ---------- 批量导入（选父目录，多个游戏目录按规范逐个上传） ----------

const batchDialogVisible = ref(false);

// 从上传弹窗进入批量导入：关掉单上传弹窗避免两层弹窗叠置
const handleOpenBatchUpload = () => {
  if (uploading.value) return;
  uploadDialogVisible.value = false;
  batchDialogVisible.value = true;
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
    // 低频关键写操作成功留痕，业务键仅 id/gameKey 与目标状态
    logger.info(
      '游戏下发状态更新成功',
      `id=${row.id}`,
      `gameKey=${row.gameKey}`,
      `enabled=${next}`
    );
  } catch (error) {
    row.enabled = next === 1 ? 0 : 1; // 失败回滚开关状态
    logger.error(
      '游戏下发状态切换失败（已回滚开关）:',
      `id=${row.id}`,
      `gameKey=${row.gameKey}`,
      error
    );
    ElMessage.error(error.message || '状态切换失败');
  } finally {
    togglingId.value = null;
  }
};

// ---------- 批量下发（表格勾选） ----------

const selectedRows = ref([]);
const batchToggling = ref(false);

const handleSelectionChange = (rows) => {
  selectedRows.value = rows;
};

const handleBatchEnabled = async (enabled) => {
  const rows = selectedRows.value;
  const actionText = enabled === 1 ? '下发' : '取消下发';
  // 已处于目标状态的行提前剔除，避免整批幂等空转
  const targets = rows.filter((r) => r.enabled !== enabled);
  if (!targets.length) {
    ElMessage.info(`勾选的游戏均已是${actionText === '下发' ? '下发' : '未下发'}状态`);
    return;
  }
  const confirmed = await useConfirm(
    `确定对选中的 ${targets.length} 个游戏执行「${actionText}」？用户端列表将即时生效。`,
    `批量${actionText}`
  );
  if (!confirmed) return;

  batchToggling.value = true;
  try {
    const results = await batchUpdateGameEnabled(
      targets.map((r) => r.id),
      enabled
    );
    const failed = (results || []).filter((r) => !r.success);
    if (!failed.length) {
      ElMessage.success(`已批量${actionText} ${targets.length} 个游戏`);
      logger.info('游戏批量下发成功', `count=${targets.length}`, `enabled=${enabled}`);
    } else {
      // 部分失败：明细浮出，成功的保持生效（尽力而为语义）
      ElMessage.warning(
        `批量${actionText}完成：成功 ${targets.length - failed.length} 个，失败 ${failed.length} 个`
      );
      logger.error(
        '游戏批量下发部分失败:',
        failed.map((f) => `id=${f.id} ${f.message || ''}`).join('; ')
      );
    }
    selectedRows.value = [];
    fetchData();
  } catch (error) {
    logger.error(`游戏批量${actionText}失败:`, error);
    ElMessage.error(error.message || `批量${actionText}失败`);
  } finally {
    batchToggling.value = false;
  }
};

// ---------- 编辑弹窗（元数据 + 可选更换游戏包） ----------

const editDialogVisible = ref(false);
const editFormRef = ref(null);
const editUploadRef = ref(null);
const editFile = ref(null); // 已选的新游戏包（null = 仅改元数据）
const submitting = ref(false);

// 打开编辑弹窗（仅可编辑字段进表单；gameKey 只读展示）
const handleEdit = (row) => {
  editDialogVisible.value = true;
  editFile.value = null;
  // 已有封面回填预览(不可编辑态下也能看到当前封面);新选文件会覆盖
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value);
  coverFile.value = null;
  coverUrl.value = row.coverUrl || '';
  coverPreview.value = row.coverUrl || '';
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

// 弹窗关闭 — 清除验证状态与已选文件
const handleEditDialogClosed = () => {
  editFormRef.value?.resetFields();
  editFormRef.value?.clearValidate();
  editFile.value = null;
  handleCoverRemove();
};

// 选择新游戏包（含超限替换）：校验失败清空并提示，与上传弹窗同口径
const handleEditFileChange = (uploadFile) => {
  const raw = uploadFile?.raw;
  if (!raw) return;
  const error = validateFile(raw);
  if (error) {
    logger.debug('编辑换包文件预检未通过，已清空选择:', error);
    ElMessage.error(error);
    editUploadRef.value?.clearFiles();
    editFile.value = null;
    return;
  }
  editFile.value = raw;
};

// limit=1 下再次选择：替换为最新文件
const handleEditFileExceed = (files) => {
  editUploadRef.value?.clearFiles();
  const file = files[0];
  if (file) editUploadRef.value?.handleStart(file);
};

const handleEditFileRemove = () => {
  editUploadRef.value?.clearFiles();
  editFile.value = null;
};

// 提交编辑：
// - 未选文件 → PUT 元数据（gameKey/来源不可改，方案 §五契约）
// - 选了文件 → 以同 gameKey 走覆盖上传（后端保留 id/created_at，元数据取当前表单值），
//   使「修改游戏」同时覆盖游戏包，不必绕道上传弹窗重填一遍表单
const submitEdit = async () => {
  submitting.value = true;
  try {
    await editFormRef.value.validate();
    const payload = {
      title: tempGame.title,
      description: tempGame.description,
      icon: tempGame.icon,
      color: tempGame.color,
      category: tempGame.category,
      badge: tempGame.badge,
      sort: tempGame.sort,
      remark: tempGame.remark,
    };
    // 封面(可选):新选文件先上传取直链;移除封面时清空;未改动沿用原值
    if (coverFile.value) {
      try {
        payload.coverUrl = await uploadCoverIfNeeded();
      } catch (err) {
        logger.error('封面上传失败:', err);
        ElMessage.error('封面上传失败，请稍后重试');
        submitting.value = false;
        return;
      }
    } else if (!coverPreview.value) {
      payload.coverUrl = '';
    } else {
      payload.coverUrl = coverUrl.value;
    }
    if (editFile.value) {
      const formData = new FormData();
      formData.append('file', editFile.value);
      formData.append('gameKey', tempGame.gameKey);
      Object.entries(payload).forEach(([key, value]) =>
        formData.append(key, value == null ? '' : String(value))
      );
      await uploadGame(formData);
    } else {
      await updateGame({ id: tempGame.id, ...payload });
    }
    ElMessage.success('修改成功');
    // 低频关键写操作成功留痕，区分仅改元数据与覆盖上传游戏包；业务键仅 id/gameKey
    logger.info(
      editFile.value ? '游戏编辑成功（覆盖上传游戏包）' : '游戏编辑成功（仅元数据）',
      `id=${tempGame.id}`,
      `gameKey=${tempGame.gameKey}`
    );
    editDialogVisible.value = false;
    fetchData();
  } catch (error) {
    // 表单校验失败（字段错误已展示）与接口报错分开提示
    if (error?.message) {
      logger.error('游戏编辑失败:', `id=${tempGame.id}`, `gameKey=${tempGame.gameKey}`, error);
      ElMessage.error(error.message);
    }
  } finally {
    submitting.value = false;
  }
};

// ---------- 删除 ----------

const handleDelete = async (row) => {
  // 确认弹窗：如实说明影响范围——仅删游戏包与展示条目，关卡/存档/成绩数据保留，
  // 同 gameKey 重新上传会继承历史数据（confirmation-dialogs）
  const confirmed = await useConfirm(
    `确认删除游戏「${row.title}」吗？将同时清除服务器上的游戏文件，删除后不再展示且不可恢复。该游戏的关卡、存档与成绩数据不会删除，同 gameKey 重新上传会继承历史数据。`
  );
  if (!confirmed) return;
  deletingId.value = row.id;
  try {
    await deleteGame(row.id);
    ElMessage.success('删除成功');
    // 低频关键写操作成功留痕，业务键仅 id/gameKey
    logger.info('游戏删除成功', `id=${row.id}`, `gameKey=${row.gameKey}`);
    fetchData(); // 刷新当前页
  } catch (error) {
    logger.error('游戏删除失败:', `id=${row.id}`, `gameKey=${row.gameKey}`, error);
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
  color: var(--text-primary);
  font-weight: 500;
}

.title-sub {
  color: var(--text-muted);
  font-size: 12px;
}

/* 图标色块（端上按色组 key 渲染 16dp 圆角块，此处用色点示意） */
.icon-block {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 6px; /* 对齐 rounded.sm（WEB-012：4px 低于阶梯最小档） */
  flex-shrink: 0;
}

/* 下发列：开关与「已下发但不可玩」提示上下排布 */
.enabled-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

/* gameKey 等宽字体（对齐跳转路由页 host 单元格） */
.key-text {
  font-family: var(--font-mono);
  color: var(--text-body);
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
  color: var(--text-secondary);
}

/* gameKey 冲突覆盖提示：贴在 gameKey 表单行内，与输入框同宽 */
.key-conflict-alert {
  width: 100%;
  margin-top: 4px;
}

/* 目录携带 levels.json 提示条：贴在文件选择区下方，与整个表单行同宽 */
.levels-draft-alert {
  width: 100%;
  margin-top: 10px;
}

.levels-draft-check {
  height: auto;
  margin-top: 2px;
}

/* 开关同行的行内说明 */
.form-tip-inline {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
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
  background-color: var(--primary-tint);
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
  color: var(--primary-soft);
}

.upload-empty-text {
  font-size: 14px;
  color: var(--text-body);
}

.upload-empty-text em {
  font-style: normal;
  color: var(--color-primary);
}

.upload-empty-tip {
  font-size: 12px;
  color: var(--text-muted);
}

/* 自动填表入口：置于 el-upload 之外，与拖拽区平级（见模板处注释——放在插槽内会被 el-upload 抢走点击） */
.upload-alt {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
  margin-top: 8px;
}

.upload-alt-divider {
  width: 100%;
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}

/* 自动填表用的两个隐藏 input */
.dir-input {
  display: none;
}

/* 已选文件卡片 */
.cover-thumb {
  width: 44px;
  height: 25px;
  object-fit: cover;
  border-radius: 6px; /* 对齐 rounded.sm，低阶梯最小档 */
  border: 1px solid var(--border-color);
  flex: none;
}

.cover-empty {
  min-height: 108px;
}

.cover-preview {
  position: relative;
  width: 160px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.cover-preview img {
  display: block;
  width: 100%;
  height: auto;
}

.cover-remove {
  position: absolute;
  top: 6px;
  right: 6px;
}

.picked-file {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 4px;
}

.picked-file-icon {
  font-size: 28px;
  color: var(--color-primary);
  flex-shrink: 0;
}

.picked-file-meta {
  flex: 1;
  min-width: 0;
  text-align: left;
}

.picked-file-name {
  font-size: 14px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picked-file-size {
  font-size: 12px;
  color: var(--text-muted);
}

.picked-file-remove {
  flex-shrink: 0;
}

/* 上传进度面板 */
.progress-panel {
  margin-top: 4px;
  padding: 12px 16px;
  background-color: var(--bg-subtle);
  border-radius: 8px;
}

.progress-text {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
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
  color: var(--color-success);
}

.upload-success-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
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
  color: var(--text-secondary);
}

.upload-success-tip {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  line-height: 1.6;
}

.upload-success-missing {
  font-size: 13px;
  color: var(--text-muted);
}

/* 成功态面板的关卡跟随导入结果：加载态弱化为次要文本色 */
.upload-success-levels {
  font-size: 13px;
  color: var(--text-body);
  line-height: 1.6;
  text-align: center;
}

.upload-success-levels.is-loading {
  color: var(--text-secondary);
}
</style>
