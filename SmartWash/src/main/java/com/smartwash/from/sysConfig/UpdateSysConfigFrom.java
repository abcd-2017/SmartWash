package com.smartwash.from.sysConfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统参数更新表单
 */
@Data
public class UpdateSysConfigFrom {

    @NotNull(message = "参数 ID 不能为空")
    private Long id;

    @NotBlank(message = "参数值不能为空")
    @Size(max = 255, message = "参数值长度不能超过 255")
    private String configValue;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
