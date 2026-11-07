package com.smartwash.game.from;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量上下架请求 DTO（POST /admin/game/enabled/batch）。
 * 逐条 CAS 尽力而为：单条失败不阻断其余，结果以明细返回。
 */
@Data
public class BatchEnabledFrom {

    /** 目标游戏 ID 集合（去重由后端处理，空集合拒绝） */
    @NotEmpty(message = "游戏 ID 集合不能为空")
    private List<Long> ids;

    /** 目标状态：1下发 0取消下发 */
    @NotNull(message = "目标状态不能为空")
    private Integer enabled;
}
