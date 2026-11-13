package com.smartwash.game.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关卡批量导入结果（POST /admin/game/levels/batch 响应）。
 * 整批事务语义：全部成功才返回（任一项失败走统一错误信封全量回滚），故无逐条失败明细。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameLevelImportResultVo {

    /** 本次导入条目总数 */
    private int total;

    /** 新增关卡数 */
    private int created;

    /** 覆盖更新关卡数（replaceAll=true 时恒为 0） */
    private int updated;
}
