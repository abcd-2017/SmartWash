package com.smartwash.game.from;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 关卡批量导入请求（管理端 POST /admin/game/levels/batch）。
 *
 * <p>「裸关卡 / 信封」双兼容格式的解析归一由前端完成（levelImportParser），
 * 本接口收到的是归一后的条目列表：dataJson 必填，元数据可选缺省兜底。
 * 服务端整批事务，任一项校验失败全量回滚，成功后版本号仅递增一次。</p>
 */
@Data
@Accessors(chain = true)
public class GameLevelBatchFrom {

    /** 游戏标识 */
    @NotBlank(message = "gameKey 不能为空")
    private String gameKey;

    /** 是否先清空该游戏现有关卡再导入（危险操作，前端二次确认） */
    private boolean replaceAll;

    /** 关卡条目（上限 500，防超大请求） */
    @Valid
    @NotEmpty(message = "导入关卡列表不能为空")
    @Size(max = 500, message = "单次最多导入 500 关")
    private List<LevelItem> levels;

    /** 单关条目：字段与 GameLevelSaveFrom 对齐，gameKey 由顶层携带避免逐条重复 */
    @Data
    @Accessors(chain = true)
    public static class LevelItem {

        /** 关卡序号 */
        @NotNull(message = "关卡序号不能为空")
        @Min(value = 1, message = "关卡序号必须为正整数")
        private Integer levelNo;

        /** 关卡名 */
        @Size(max = 32, message = "关卡名最长 32 字")
        private String name;

        /** 关卡定义 JSON 字符串（服务端校验 JSON 合法性，结构由游戏自定） */
        @NotBlank(message = "关卡数据不能为空")
        @Size(max = 65535, message = "单关数据过大（超过 64KB）")
        private String dataJson;

        /** 难度（展示用） */
        private Integer difficulty;

        /** 1 下发 0 不下发（可空按 1） */
        private Integer enabled;

        /** 排序，越小越靠前（可空按关卡序号×10） */
        private Integer sort;

        /** 备注 */
        @Size(max = 255, message = "备注最长 255 字")
        private String remark;
    }
}
