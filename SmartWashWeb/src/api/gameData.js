// src/api/gameData.js
// 游戏数据管理 API 层（关卡 / 存档 / 成绩，后端 GameDataAdminController，ROLE_ADMIN）。
// 与 api/game.js 分开：game.js 管游戏包与元数据（上传/上下架），本模块管运行期数据。
import request from '@/utils/http';

// ==================== 关卡 ====================

/** 关卡列表：按 gameKey 返回全部关卡（含未启用），按 sort / levelNo 排序 */
export function getGameLevels(gameKey) {
  return request({
    url: '/admin/game/levels',
    method: 'get',
    params: { gameKey },
  });
}

/** 保存关卡（新建/更新）：按 (gameKey, levelNo) upsert，保存后版本号递增 */
export function saveGameLevel(data) {
  return request({
    url: '/admin/game/levels',
    method: 'post',
    data,
  });
}

/** 批量导入关卡：整批事务（任一项校验失败全量回滚），replaceAll=true 先清空再导入；成功后版本号仅递增一次 */
export function batchImportGameLevels(data) {
  return request({
    url: '/admin/game/levels/batch',
    method: 'post',
    data,
  });
}

/** 删除关卡：按 (gameKey, levelNo) 物理删除，删除后版本号递增 */
export function deleteGameLevel(gameKey, levelNo) {
  return request({
    url: '/admin/game/levels',
    method: 'delete',
    params: { gameKey, levelNo },
  });
}

// ==================== 存档 / 成绩 ====================

/** 存档分页列表：gameKey / userId 筛选（可空），按更新时间倒序 */
export function getGameSaves(params) {
  return request({
    url: '/admin/game/saves',
    method: 'get',
    params,
  });
}

/** 成绩分页列表：gameKey 筛选（可空），按 metric_value 降序 */
export function getGameScores(params) {
  return request({
    url: '/admin/game/scores',
    method: 'get',
    params,
  });
}
