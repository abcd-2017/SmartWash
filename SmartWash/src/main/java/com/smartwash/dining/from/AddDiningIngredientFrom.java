package com.smartwash.dining.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增配料请求 DTO（管理端 POST /admin/dining/ingredient）。
 * name 全局唯一（uk_name 兜底并发，重名 409 业务码）；category 缺省 OTHER。
 */
@Data
public class AddDiningIngredientFrom {

    /** 配料名（唯一；供应商信息不入库） */
    @NotBlank(message = "配料名称不能为空")
    @Size(max = 32, message = "配料名称过长（上限32字符）")
    private String name;

    /** 配料分类：MAIN=主料/AUX=辅料/SEASONING=调料/OTHER=其他，缺省 OTHER */
    @Pattern(regexp = "MAIN|AUX|SEASONING|OTHER", message = "配料分类仅支持 MAIN/AUX/SEASONING/OTHER")
    private String category;
}
