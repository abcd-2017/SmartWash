<template>
  <div class="page-container">
    <!-- 说明卡片：与操作审计区分，明确本表是登录/注册留痕 -->
    <el-alert title="登录日志" type="info" :closable="false" show-icon>
      <template #default>
        登录 / 注册的平台与设备留痕（login_log），用于按端别、版本、网络排查登录问题。
      </template>
    </el-alert>

    <!-- 搜索区域 -->
    <div class="filter-container">
      <el-form :inline="true" :model="listQuery" @keyup.enter="handleSearch">
        <el-form-item label="平台">
          <el-select
            v-model="listQuery.platform"
            placeholder="全部平台"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in LOGIN_PLATFORM_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="项目渠道">
          <el-select
            v-model="listQuery.appChannel"
            placeholder="全部渠道"
            clearable
            style="width: 150px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in LOGIN_APP_CHANNEL_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="结果">
          <el-select
            v-model="listQuery.result"
            placeholder="全部结果"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in LOGIN_RESULT_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="登录时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disabledDate"
            :shortcuts="timeShortcuts"
            style="width: 380px"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item class="filter-end">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格（只读：登录日志仅查询，不提供新增/编辑/删除） -->
    <div class="table-card">
      <el-table
        v-loading="listLoading"
        :data="loginLogList"
        fit
        highlight-current-row
        empty-text="暂无数据，试试调整筛选条件"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column label="用户ID" min-width="100">
          <template #default="{ row }">{{ userIdOf(row) }}</template>
        </el-table-column>
        <el-table-column label="手机号" min-width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="平台" min-width="110">
          <template #default="{ row }">
            <el-tag :type="loginPlatformTagType(row.platform)">{{
              loginPlatformText(row.platform)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="项目渠道" min-width="120">
          <template #default="{ row }">{{ appChannelOf(row) }}</template>
        </el-table-column>
        <el-table-column label="App 版本" min-width="110">
          <template #default="{ row }">{{ appVersionOf(row) }}</template>
        </el-table-column>
        <el-table-column label="网络类型" min-width="110">
          <template #default="{ row }">{{
            loginNetworkTypeText(row.networkType ?? row.network_type)
          }}</template>
        </el-table-column>
        <el-table-column prop="ua" label="User-Agent" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ua || '-' }}</template>
        </el-table-column>
        <el-table-column label="IP" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" min-width="90">
          <template #default="{ row }">
            <el-tag :type="loginResultTagType(row.result)">{{
              loginResultText(row.result)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录时间" min-width="180">
          <template #default="{ row }">{{ loginTime(row) }}</template>
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
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import dayjs from 'dayjs';
import { getLoginLogs } from '@/api/loginLog';
import { formatTime } from '@/utils/format';
import { useTableList } from '@/composables/useTableList';
import { useTimeRange } from '@/composables/useTimeRange';
import {
  LOGIN_PLATFORM_OPTIONS,
  LOGIN_APP_CHANNEL_OPTIONS,
  LOGIN_RESULT_OPTIONS,
  loginPlatformTagType,
  loginPlatformText,
  loginAppChannelText,
  loginResultTagType,
  loginResultText,
  loginNetworkTypeText,
} from '@/constants/dict';

// 后端契约字段为下划线口径（app_version/created_at/user_id/network_type/app_channel），
// 而现有 Java 接口序列化多为驼峰（参考 AuditLogList 的 createdAt）。
// 此处两种命名都兼容，待后端联调确定口径后收敛。
const appVersionOf = (row) => row.appVersion ?? row.app_version ?? '-';
const appChannelOf = (row) => loginAppChannelText(row.appChannel ?? row.app_channel);
const userIdOf = (row) => row.userId ?? row.user_id ?? '-';
const loginTime = (row) => formatTime(row.createdAt ?? row.created_at);

// ===== 列表查询与分页：统一由 useTableList 承载 =====
const {
  list: loginLogList,
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
  fetchApi: getLoginLogs,
  baseQuery: {
    platform: null,
    appChannel: null,
    result: null,
    startTime: null,
    endTime: null,
  },
  buildParams: (q) => ({
    ...q,
    platform: q.platform || undefined,
    appChannel: q.appChannel || undefined,
    result: q.result || undefined,
  }),
  errorMsg: '获取登录日志失败',
});

// 时间范围：startTime/endTime 字段名与审计筛选口径一致，
// 格式 yyyy-MM-dd HH:mm:ss 与后端 @DateTimeFormat 口径一致
const timeRange = useTimeRange(listQuery);

// 时间筛选禁选未来时间（登录日志只增不改，未来区间必为空）
const disabledDate = (date) => date.getTime() > Date.now();

// 快捷区间：对齐运营排查常用窗口（今天 / 近 7 天 / 近 30 天）
const timeShortcuts = [
  {
    text: '今天',
    value: () => [dayjs().startOf('day').toDate(), dayjs().endOf('day').toDate()],
  },
  {
    text: '近 7 天',
    value: () => [
      dayjs().subtract(6, 'day').startOf('day').toDate(),
      dayjs().endOf('day').toDate(),
    ],
  },
  {
    text: '近 30 天',
    value: () => [
      dayjs().subtract(29, 'day').startOf('day').toDate(),
      dayjs().endOf('day').toDate(),
    ],
  },
];

onMounted(() => {
  fetchData();
});
</script>
