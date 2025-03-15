package com.smartwash.from.schools;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddSchoolsFrom {
    @NotBlank(message = "学校名字不能为空")
    private String schoolName;

    @NotBlank(message = "学校位置不能为空")
    private String location;

    @NotNull(message = "存储柜数量不能为空")
    private Integer lockerCount;
}
