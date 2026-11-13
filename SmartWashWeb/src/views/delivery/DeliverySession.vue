<template>
  <div class="session-page">
    <!-- 顶部：会话信息卡 -->
    <header class="session-card">
      <div class="session-head">
        <div class="session-title">
          <img src="@/assets/logo.svg" alt="" class="session-logo" />
          <div>
            <h1 class="session-name">设备投递</h1>
            <p class="session-sub">
              投递码 <span class="session-code">{{ code }}</span>
            </p>
          </div>
        </div>
        <div class="session-meta">
          <span class="meta-item">
            剩余时间
            <strong :class="{ 'meta-urgent': remainingMs <= 60_000 }">{{ remainingText }}</strong>
          </span>
          <el-divider direction="vertical" />
          <span class="meta-item"
            >待取走 <strong>{{ pendingCount }}</strong> / 共 {{ items.length }} 条</span
          >
        </div>
      </div>
      <p class="session-notice">
        条目经服务器中转（服务器可见明文）；取走即焚，会话到期自动回收，请及时保存所需内容。
      </p>
    </header>

    <!-- 中部：条目流 -->
    <section ref="listRef" class="item-list" v-loading="firstLoading">
      <TransitionGroup v-if="items.length" name="item">
        <article
          v-for="item in items"
          :key="item.id"
          class="item-row"
          :class="{ 'item-consumed': isConsumed(item) }"
        >
          <div class="item-icon" :class="kindClass(item.kind)">
            <el-icon :size="18"><component :is="kindIcon(item.kind)" /></el-icon>
          </div>
          <div class="item-body">
            <div class="item-title-row">
              <span class="item-title">{{ item.title || '未命名' }}</span>
              <span
                class="item-direction"
                :class="item.direction === 1 ? 'dir-to-peer' : 'dir-to-owner'"
              >
                {{ directionText(item.direction) }}
              </span>
            </div>
            <div class="item-meta">
              <span>{{ formatTime(item.createdAt) }}</span>
              <span v-if="item.objectSize != null">{{ formatSize(item.objectSize) }}</span>
            </div>
          </div>
          <div class="item-action">
            <template v-if="isConsumed(item)">
              <el-tag size="small" type="info" effect="plain">已取走</el-tag>
            </template>
            <template v-else-if="item.direction === 1">
              <!-- 手机→电脑：本端可取走 -->
              <el-button
                v-if="item.kind === 1"
                type="primary"
                size="small"
                :loading="actingId === item.id"
                @click="handleCopyTake(item)"
              >
                复制内容
              </el-button>
              <el-button
                v-else
                type="primary"
                size="small"
                :loading="actingId === item.id"
                @click="handleDownloadTake(item)"
              >
                下载
              </el-button>
            </template>
            <span v-else class="item-waiting">等待手机端取走</span>
          </div>
        </article>
      </TransitionGroup>
      <el-empty v-else-if="!firstLoading" description="暂无条目，等待手机端投递" :image-size="80" />
    </section>

    <!-- 底部：发送区 -->
    <footer class="composer-card">
      <el-input
        v-model="draftText"
        type="textarea"
        :rows="3"
        resize="none"
        maxlength="100000"
        placeholder="输入要投递到手机的文本，Ctrl/Cmd+V 直接粘贴"
        @keydown.enter.exact.prevent="handleSendText"
      />
      <div class="composer-bar">
        <span class="composer-hint">文本与文件均取走即焚</span>
        <div class="composer-actions">
          <input
            ref="fileInputRef"
            type="file"
            multiple
            class="file-input"
            @change="handleFilesSelected"
          />
          <el-button :disabled="uploading" @click="fileInputRef?.click()">选择文件</el-button>
          <el-button
            type="primary"
            :loading="sendingText || uploading"
            :disabled="!draftText.trim()"
            @click="handleSendText"
          >
            发送文本
          </el-button>
        </div>
      </div>
      <p v-if="uploading" class="composer-uploading">文件上传中，请稍候…</p>
    </footer>
  </div>
</template>

<script setup>
// 设备投递会话页（对端 = 电脑浏览器）：双向条目流 + 发送区 + 取走即焚操作。
// 数据同步采用 2-3s 轮询（契约 v1 无 SSE，基准取中 2.5s）：页面隐藏/卸载时暂停，
// 重新可见立即补拉；轮询遇 404 视为会话失效，上抛 expired 由容器切换失效态。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Document, Picture, Paperclip } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  DELIVERY_CODE,
  DELIVERY_MAX_FILE_SIZE,
  getDeliveryItems,
  takeDeliveryItem,
  downloadDeliveryFile,
  publishDeliveryText,
  publishDeliveryFile,
} from '@/api/delivery';
import logger from '@/utils/logger';

const props = defineProps({
  code: { type: String, required: true },
  // enter 成功返回的会话 Vo（expireAt 用于本地倒计时）
  session: { type: Object, required: true },
});

const emit = defineEmits(['expired']);

// ---------- 条目流与轮询 ----------
const POLL_INTERVAL = 2500; // 契约 2-3s 轮询，取中值
const items = ref([]);
const firstLoading = ref(true);
const refreshing = ref(false);
const listRef = ref(null);
let pollTimer = null;
let stopped = false;

const pendingCount = computed(() => items.value.filter((item) => !isConsumed(item)).length);

// 取走即焚判定：taken=1 为已焚毁占位；hasContent=false 兜底覆盖内容缺失的边缘态
function isConsumed(item) {
  return item.taken === 1 || !item.hasContent;
}

async function refreshItems() {
  if (refreshing.value || stopped) return;
  refreshing.value = true;
  try {
    const envelope = await getDeliveryItems(props.code);
    if (envelope.code === DELIVERY_CODE.OK) {
      items.value = envelope.data?.items || [];
    } else if (envelope.code === DELIVERY_CODE.GONE) {
      // 读路径即时失效：过期/关闭一律 404，立即退出会话页
      sessionGone();
    }
    // 其余业务码/网络异常：轮询天然自愈，静默等待下一轮
  } catch (error) {
    // 网络异常静默自愈，下一轮重试；轮询为高频路径仅记 debug（envelopeHttp 无拦截器留痕，生产不输出）
    logger.debug('投递条目轮询本轮未成功，等待下一轮自愈:', error);
  } finally {
    refreshing.value = false;
  }
}

function startPolling() {
  stopPolling();
  if (stopped) return;
  pollTimer = setInterval(() => {
    // 页面隐藏时暂停轮询（双保险：visibilitychange 已停，定时器回调再判一次）
    if (document.hidden) return;
    refreshItems();
  }, POLL_INTERVAL);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function handleVisibilityChange() {
  if (stopped) return;
  if (document.hidden) {
    stopPolling();
  } else {
    // 重新可见立即补拉一次，再恢复周期轮询
    refreshItems();
    startPolling();
  }
}

function sessionGone() {
  if (stopped) return;
  stopped = true;
  stopPolling();
  // 低频关键状态迁移（轮询404/本地倒计时归零/条目操作遇404多路汇聚，stopped 守卫保证只记一次）
  logger.info('投递会话已失效，退出会话页（过期/被关闭/读取404/倒计时归零）');
  emit('expired');
}

onMounted(async () => {
  document.addEventListener('visibilitychange', handleVisibilityChange);
  tickCountdown();
  countdownTimer = setInterval(tickCountdown, 1000);
  await refreshItems();
  firstLoading.value = false;
  startPolling();
});

onBeforeUnmount(() => {
  // 页面卸载：停轮询 + 停倒计时 + 移除监听，避免离开后仍在后台请求
  stopped = true;
  stopPolling();
  if (countdownTimer) clearInterval(countdownTimer);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});

// ---------- 倒计时 ----------
const remainingMs = ref(0);
const expireAt = computed(() =>
  props.session?.expireAt ? new Date(props.session.expireAt) : null
);
let countdownTimer = null;

const remainingText = computed(() => {
  const ms = Math.max(0, remainingMs.value);
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (n) => String(n).padStart(2, '0');
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
});

function tickCountdown() {
  if (!expireAt.value) return;
  remainingMs.value = expireAt.value.getTime() - Date.now();
  if (remainingMs.value <= 0) {
    // 本地到期即切失效态（后端读路径同样即时失效，双端一致）
    sessionGone();
  }
}

// 新条目到达时滚动到底部（仅当用户本来就停在底部附近，避免打断回看）
watch(
  () => items.value.length,
  async (next, prev) => {
    if (next <= prev) return;
    await nextTick();
    const el = listRef.value;
    if (!el) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (nearBottom) el.scrollTop = el.scrollHeight;
  }
);

// ---------- 取走（take + 焚毁） ----------
const actingId = ref(null);

// 文本条目「复制内容」= take 认领并焚毁，正文随响应返回后写入剪贴板
async function handleCopyTake(item) {
  if (actingId.value) return;
  actingId.value = item.id;
  try {
    const envelope = await takeDeliveryItem(props.code, item.id);
    if (envelope.code === DELIVERY_CODE.OK) {
      await copyToClipboard(envelope.data?.textContent || '');
      // 低频关键动作成功（取走即焚毁，不可逆）
      logger.info('投递文本取件成功', `itemId=${item.id}`);
      ElMessage.success('已复制到剪贴板，原文本已焚毁');
      refreshItems();
    } else if (envelope.code === DELIVERY_CODE.CONFLICT) {
      logger.warn('取件被拒：条目已被取走（409）', `itemId=${item.id}`);
      ElMessage.warning('条目已被取走');
      refreshItems();
    } else if (envelope.code === DELIVERY_CODE.GONE) {
      sessionGone();
    } else {
      logger.warn('取件被拒：', `itemId=${item.id}`, `code=${envelope.code}`, envelope.message);
      ElMessage.error(envelope.message || '取走失败，请稍后重试');
    }
  } catch (error) {
    // 页面自有 catch（envelopeHttp 无拦截器留痕），此处兜底记录
    logger.error('文本取件失败:', `itemId=${item.id}`, error);
    ElMessage.error('网络异常，请稍后重试');
  } finally {
    actingId.value = null;
  }
}

// 文件/图片条目「下载」= take 认领后走 content 流式下载（流正常完成服务端即焚毁）
async function handleDownloadTake(item) {
  if (actingId.value) return;
  actingId.value = item.id;
  try {
    const takeEnvelope = await takeDeliveryItem(props.code, item.id);
    if (takeEnvelope.code === DELIVERY_CODE.CONFLICT) {
      logger.warn('取件被拒：条目已被取走（409）', `itemId=${item.id}`);
      ElMessage.warning('条目已被取走');
      refreshItems();
      return;
    }
    if (takeEnvelope.code === DELIVERY_CODE.GONE) {
      sessionGone();
      return;
    }
    if (takeEnvelope.code !== DELIVERY_CODE.OK) {
      logger.warn(
        '取件被拒：',
        `itemId=${item.id}`,
        `code=${takeEnvelope.code}`,
        takeEnvelope.message
      );
      ElMessage.error(takeEnvelope.message || '取走失败，请稍后重试');
      return;
    }

    const contentUrl = takeEnvelope.data?.contentUrl;
    if (!contentUrl) {
      // 认领已成功但拿不到下载路径，属契约异常，降级告警
      logger.warn('下载路径缺失（条目已认领但无 contentUrl）', `itemId=${item.id}`);
      ElMessage.error('下载路径缺失，请稍后重试');
      return;
    }
    const { blob, filename } = await downloadDeliveryFile(props.code, contentUrl, item.title);
    triggerSave(blob, filename);
    // 低频关键动作成功（流式下载完成即焚毁，不可逆）
    logger.info(
      '投递文件下载成功',
      `itemId=${item.id}`,
      `fileName=${filename}`,
      `size=${blob.size}`
    );
    ElMessage.success('已下载，原文件已焚毁');
    refreshItems();
  } catch (error) {
    if (error?.businessCode === DELIVERY_CODE.GONE) {
      sessionGone();
    } else if (error?.businessCode === DELIVERY_CODE.CONFLICT) {
      logger.warn('下载被拒：条目状态已变化（409）', `itemId=${item.id}`);
      ElMessage.warning('条目尚未取走或状态已变化，请重试');
      refreshItems();
    } else {
      // 页面自有 catch（envelopeHttp 无拦截器留痕），此处兜底记录
      logger.error('文件下载失败:', `itemId=${item.id}`, error);
      ElMessage.error(error?.message || '网络异常，请稍后重试');
    }
  } finally {
    actingId.value = null;
  }
}

// 优先 Clipboard API（需 secure context），非 HTTPS 环境降级 execCommand
async function copyToClipboard(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
}

function triggerSave(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  // 释放对象 URL；下载请求由浏览器接管，若被用户中断后端会自动释放认领（可重取）
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

// ---------- 发送区 ----------
const draftText = ref('');
const sendingText = ref(false);
const uploading = ref(false);
const fileInputRef = ref(null);

async function handleSendText() {
  const text = draftText.value.trim();
  if (!text || sendingText.value || uploading.value) return;
  sendingText.value = true;
  try {
    const envelope = await publishDeliveryText(props.code, text);
    if (envelope.code === DELIVERY_CODE.OK) {
      // 低频写操作成功（正文内容不入日志，只记返回条目 id）
      logger.info('投递文本发送成功', `itemId=${envelope.data?.id ?? '-'}`);
      draftText.value = '';
      refreshItems();
    } else if (envelope.code === DELIVERY_CODE.GONE) {
      sessionGone();
    } else {
      logger.warn('文本发送被拒：', `code=${envelope.code}`, envelope.message);
      ElMessage.error(envelope.message || '发送失败，请稍后重试');
    }
  } catch (error) {
    // 页面自有 catch（envelopeHttp 无拦截器留痕），此处兜底记录
    logger.error('文本发送失败:', error);
    ElMessage.error('网络异常，请稍后重试');
  } finally {
    sendingText.value = false;
  }
}

async function handleFilesSelected(event) {
  const files = Array.from(event.target.files || []);
  event.target.value = ''; // 允许重复选择同一文件
  if (!files.length || uploading.value) return;
  uploading.value = true;
  try {
    for (const file of files) {
      if (file.size > DELIVERY_MAX_FILE_SIZE) {
        logger.warn('文件超过 20MB 已跳过', `fileName=${file.name}`, `size=${file.size}`);
        ElMessage.warning(`「${file.name}」超过 20MB，已跳过`);
        continue;
      }
      const kind = file.type?.startsWith('image/') ? 2 : 3;
      // 逐个串行上传，失败不中断后续文件（413 等业务错误逐个提示）
      const envelope = await publishDeliveryFile(props.code, kind, file);
      if (envelope.code === DELIVERY_CODE.OK) {
        // 低频写操作成功（文件内容不入日志，只记元数据）
        logger.info(
          '投递文件上传成功',
          `fileName=${file.name}`,
          `size=${file.size}`,
          `kind=${kind}`
        );
        continue;
      }
      if (envelope.code === DELIVERY_CODE.GONE) {
        sessionGone();
        return;
      }
      logger.warn(
        '文件上传被拒：',
        `fileName=${file.name}`,
        `code=${envelope.code}`,
        envelope.message
      );
      ElMessage.error(`「${file.name}」${envelope.message || '上传失败'}`);
    }
    refreshItems();
  } catch (error) {
    // 页面自有 catch（envelopeHttp 无拦截器留痕），此处兜底记录
    logger.error('文件上传失败:', error);
    ElMessage.error('网络异常，请稍后重试');
  } finally {
    uploading.value = false;
  }
}

// ---------- 展示映射 ----------
const KIND_ICONS = { 1: Document, 2: Picture, 3: Paperclip };
const KIND_CLASSES = { 1: 'kind-text', 2: 'kind-image', 3: 'kind-file' };

function kindIcon(kind) {
  return KIND_ICONS[kind] || Document;
}

function kindClass(kind) {
  return KIND_CLASSES[kind] || 'kind-text';
}

function directionText(direction) {
  // 1 手机→电脑（owner 发布）、2 电脑→手机（对端发布）
  return direction === 1 ? '手机 → 电脑' : '电脑 → 手机';
}

function formatTime(value) {
  return value ? dayjs(value).format('HH:mm') : '';
}

function formatSize(bytes) {
  if (bytes == null) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
</script>

<style scoped>
.session-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 720px;
  max-width: 100%;
  margin: 0 auto;
  padding: 24px 16px 32px;
  min-height: 100vh;
}

/* --- 顶部会话信息卡 --- */
.session-card {
  padding: 20px 24px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
  animation: delivery-card-in var(--motion-slow) var(--ease-decelerate) both;
}

.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.session-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-logo {
  width: 36px;
  height: 36px;
}

.session-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.session-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.session-code {
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 2px;
  font-variant-numeric: tabular-nums;
  /* 机器标识符走全站唯一等宽栈(mono 规则) */
  font-family: var(--font-mono);
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text-secondary);
}

.meta-item strong {
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.meta-urgent {
  color: var(--color-danger) !important;
}

.session-notice {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
  font-size: 12px;
  color: var(--text-muted);
}

/* --- 条目流 --- */
.item-list {
  position: relative; /* TransitionGroup 离场条目 absolute 定位的包含块 */
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
  max-height: calc(100vh - 320px);
  overflow-y: auto;
  padding: 4px 2px;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
  /* 条目进场：淡入上移，随轮询新增自然触发 */
  transition:
    opacity var(--motion-base) var(--ease-standard),
    transform var(--motion-base) var(--ease-standard);
}

.item-consumed {
  opacity: 0.55;
}

.item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
}

/* 条目类型淡底图标容器：走语义 tint token（淡彩底规则） */
.kind-text {
  background: var(--primary-tint);
  color: var(--primary-tint-text);
}

.kind-image {
  background: var(--warning-tint);
  color: var(--warning-tint-text);
}

.kind-file {
  background: var(--border-color);
  color: var(--text-body);
}

.item-body {
  flex: 1;
  min-width: 0;
}

.item-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-title {
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-direction {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px; /* 胶囊角标：有意保留的角标形态（阶梯外例外） */
  font-size: 12px; /* 对齐 micro 档（原 11px 低于 ramp 最小值） */
  line-height: 18px;
}

/* 方向角标：手机→电脑 = 本端可取（主色淡底）；电脑→手机 = 对端发布（灰底） */
.dir-to-peer {
  background: var(--primary-tint);
  color: var(--primary-tint-text);
}

.dir-to-owner {
  background: var(--border-subtle);
  color: var(--text-secondary);
}

.item-meta {
  display: flex;
  gap: 12px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.item-action {
  flex-shrink: 0;
  min-width: 72px;
  text-align: right;
}

.item-waiting {
  font-size: 12px;
  color: var(--text-muted);
}

/* TransitionGroup 进出场：新条目淡入上移，离场更快（全局 ease Token） */
.item-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.item-enter-active {
  transition:
    opacity var(--motion-base) var(--ease-decelerate),
    transform var(--motion-base) var(--ease-decelerate);
}

.item-leave-to {
  opacity: 0;
}

.item-leave-active {
  /* 离场更快（全局 ease Token）；absolute 脱流让后续条目平滑上移补位 */
  position: absolute;
  width: 100%;
  transition: opacity var(--motion-fast) var(--ease-accelerate);
}

.item-move {
  transition: transform var(--motion-base) var(--ease-standard);
}

/* --- 发送区 --- */
.composer-card {
  padding: 16px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-ground);
  animation: delivery-card-in var(--motion-slow) var(--ease-decelerate) both;
  /* 错峰 delay 60ms 为 stagger 节奏值(off-ramp,非状态过渡时长) */
  animation-delay: 60ms;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.composer-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.composer-uploading {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-primary);
}

/* 隐藏的原生文件选择器（按钮触发） */
.file-input {
  display: none;
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

@media (max-width: 600px) {
  .session-card {
    padding: 16px;
  }

  .item-list {
    max-height: none;
  }
}
</style>
