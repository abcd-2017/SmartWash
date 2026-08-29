// src/composables/useTableList.js
// 列表页通用逻辑（评审 #12/#25）：统一封装 分页状态 + 搜索/重置/翻页/改每页条数 + 数据拉取，
// 消除 11 个列表页复制粘贴的 handleSearch/resetSearch/handlePageChange/fetchData。
// 页面通过 buildParams 保留各自的空参清洗规则，行为与迁移前完全等价。
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';

// 分页组件可选的每页条数（评审 #25）
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

/**
 * @param {Object} options
 * @param {Function} options.fetchApi     列表请求函数：入参为查询参数对象，返回 { records, total }
 * @param {Object}   [options.baseQuery]  业务查询参数初值（不含分页），重置搜索时恢复为该值
 * @param {Function} [options.buildParams] 发请求前的参数加工（如空值转 undefined），默认浅拷贝
 * @param {String}   [options.errorMsg='获取数据失败'] 拉取失败的兜底提示文案
 */
export function useTableList({ fetchApi, baseQuery = {}, buildParams, errorMsg = '获取数据失败' }) {
  const list = ref([]); // 当页数据
  const total = ref(0); // 总条数
  const listLoading = ref(false); // 加载状态
  // 分页组件可选项（透传给 el-pagination 的 :page-sizes）
  const pageSizes = PAGE_SIZE_OPTIONS;

  // 查询参数：分页 + 业务字段
  const listQuery = reactive({
    page: 1,
    size: 10,
    ...baseQuery,
  });

  // 获取数据
  const fetchData = async () => {
    listLoading.value = true;
    try {
      const params = buildParams ? buildParams(listQuery) : { ...listQuery };
      const res = await fetchApi(params);
      list.value = res.records;
      total.value = res.total;
    } catch (error) {
      ElMessage.error(error.message || errorMsg);
    } finally {
      listLoading.value = false;
    }
  };

  // 搜索：回到第一页后拉取
  const handleSearch = () => {
    listQuery.page = 1;
    fetchData();
  };

  // 重置搜索：仅恢复业务查询字段初值，页码由 handleSearch 统一回到第一页
  const resetSearch = () => {
    Object.assign(listQuery, baseQuery);
    handleSearch();
  };

  // 页码变化
  const handlePageChange = (val) => {
    listQuery.page = val;
    fetchData();
  };

  // 每页条数变化：先回第一页再改 size，避免触发 el-pagination 的页码钳制导致二次请求
  const handleSizeChange = (val) => {
    listQuery.page = 1;
    listQuery.size = val;
    fetchData();
  };

  return {
    list,
    total,
    listLoading,
    listQuery,
    pageSizes,
    fetchData,
    handleSearch,
    resetSearch,
    handlePageChange,
    handleSizeChange,
  };
}
