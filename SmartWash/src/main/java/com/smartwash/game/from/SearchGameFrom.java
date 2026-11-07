package com.smartwash.game.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 游戏分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 管理端列表筛选：keyword 模糊（title/gameKey）+ category/sourceType/enabled 精确；含未启用项（管理视角）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchGameFrom extends BaseSearchFrom {

    /** 关键词模糊筛选：同时匹配 title / gameKey */
    private String keyword;

    /** 分类筛选：puzzle/arcade/word/board/other，空=全部 */
    private String category;

    /** 来源筛选：1上传包 2外链，空=全部 */
    private Integer sourceType;

    /** 下发状态筛选：1下发 0不下发，空=全部 */
    private Integer enabled;
}
