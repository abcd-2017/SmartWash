// src/utils/optionCache.js
// 下拉选项全量数据缓存（评审 #15）：模块级 Promise 缓存，首次拉取后多页面复用同一份结果，
// 不再每进一个页面就 size:1000 拉一次全量。不新增后端接口，提供失效入口供管理页增删改后调用。
import { getSchoolList } from '@/api/school';
import { getLaundryList } from '@/api/laundry';
import { getRoleList } from '@/api/role';

/**
 * 创建一个带失效入口的缓存拉取器
 * @param {Function} fetchApi 分页接口函数
 * @param {Object} query 全量拉取参数
 */
function createCachedFetcher(fetchApi, query) {
  let pending = null; // 进行中/已完成的 Promise；失败时清空以便重试

  return {
    /**
     * 拉取选项列表（并发调用共享同一 Promise）
     * @param {Boolean} [force=false] 传 true 强制绕过缓存重新拉取
     * @returns {Promise<Array>} 选项 records 数组
     */
    load(force = false) {
      if (!pending || force) {
        pending = fetchApi(query)
          .then((res) => res.records || [])
          .catch((error) => {
            pending = null; // 失败后允许下次重试
            throw error;
          });
      }
      return pending;
    },
    /** 失效入口：清空缓存，下次 load 重新拉取 */
    invalidate() {
      pending = null;
    },
  };
}

// 学校选项缓存（用户/订单/寄存柜等页面的学校下拉共用）
export const schoolOptionsCache = createCachedFetcher(getSchoolList, { page: 1, size: 1000 });

// 洗护套餐选项缓存（订单页面套餐下拉共用）
export const laundryOptionsCache = createCachedFetcher(getLaundryList, { page: 1, size: 1000 });

// 角色选项缓存（管理员页面角色下拉共用）
export const roleOptionsCache = createCachedFetcher(getRoleList, { page: 1, size: 1000 });

/** 一键失效全部选项缓存（如新增学校/套餐/角色后调用） */
export function invalidateAllOptionCaches() {
  schoolOptionsCache.invalidate();
  laundryOptionsCache.invalidate();
  roleOptionsCache.invalidate();
}
