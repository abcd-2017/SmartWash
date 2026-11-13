package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端菜谱视图对象，供 Web 后台列表与治理操作使用。
 * ingredients 以结构化数组返回（服务层反序列化 DB 中的 JSON 文本）；
 * coverUrl 为封面对外直链（MinioConfig.urlOf 拼装，Web 直接渲染），coverObjectKey 供编辑回传。
 */
@Data
@Accessors(chain = true)
public class DiningRecipeVo {

    private Long id;

    /** 所属分类 ID，null=未分类 */
    private Long categoryId;

    /** 所属分类名称（服务层联查回填，未分类为 null） */
    private String categoryName;

    /** 菜名 */
    private String name;

    /** 封面对象键（编辑回传用），null=未上传 */
    private String coverObjectKey;

    /** 封面对外直链（由对象键拼装），null=未上传 */
    private String coverUrl;

    /** 食材名数组（服务层由 DB JSON 文本反序列化） */
    private List<String> ingredients;

    /** 做法步骤（纯文本） */
    private String steps;

    /** 来源：ADMIN=运营后台录入，USER=用户端新增 */
    private String source;

    /** 上下架：1=上架 0=下架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
