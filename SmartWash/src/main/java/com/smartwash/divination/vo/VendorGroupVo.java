package com.smartwash.divination.vo;

import lombok.Data;

import java.util.List;

/**
 * 厂商分组视图（GET /web/auth/div/models 返回元素）——一次请求返回整棵目录树：
 * 厂商为一级（含接入点），模型为二级（挂在厂商下）。
 *
 * <p>客户端设置页按此分步选择：先选厂商（一级，数据量小），再选该厂商的模型
 * （二级，从树内本地过滤，无二次请求）。目录只提供厂商/接入点/模型标识，
 * 密钥始终由用户自带（BYOK），目录不存任何密钥。</p>
 */
@Data
public class VendorGroupVo {

    /** 厂商名（一级分组标识） */
    private String vendor;

    /** 接入协议（当前仅 openai_compat） */
    private String provider;

    /** OpenAI 兼容接入点（厂商级，模型继承；目录模式由服务端取用，客户端仅展示） */
    private String baseUrl;

    /** 厂商排序权重（升序） */
    private Integer priority;

    /** 该厂商下的启用模型（按 priority 升序） */
    private List<ModelVo> models;
}
