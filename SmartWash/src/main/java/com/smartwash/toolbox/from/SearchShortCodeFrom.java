package com.smartwash.toolbox.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短码分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 * 用户端列表只按 owner 过滤；code/contentType/ownerPhone 筛选仅管理端接口生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchShortCodeFrom extends BaseSearchFrom {

    /** 短码精确筛选（管理端） */
    private String code;

    /** 内容类型筛选：1短链 2活码 3交付箱 4图床 5时间胶囊（管理端） */
    private Integer contentType;

    /** owner 手机号筛选（管理端，精确匹配） */
    private String ownerPhone;
}
