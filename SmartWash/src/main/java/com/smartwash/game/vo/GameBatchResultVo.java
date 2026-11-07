package com.smartwash.game.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量上下架单条结果（POST /admin/game/enabled/batch 响应明细）。
 * 尽力而为语义：success=false 时 message 说明原因（不存在等），不影响其余条目。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameBatchResultVo {

    /** 游戏 ID */
    private Long id;

    /** 是否生效（含幂等命中：已是目标状态也计成功） */
    private boolean success;

    /** 失败原因（成功时为空） */
    private String message;
}
