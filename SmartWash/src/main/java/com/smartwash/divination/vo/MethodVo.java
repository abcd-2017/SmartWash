package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 术数方法元信息（GET /methods 返回元素）。
 */
@Data
public class MethodVo {

    /** liuyao/meihua/qimen/liuren */
    private String code;

    private String name;

    private String description;

    /** 免责声明文案 */
    private String disclaimer;
}
