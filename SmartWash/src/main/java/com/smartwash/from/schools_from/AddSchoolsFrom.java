package com.smartwash.from.schools_from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddSchoolsFrom {
    @NotBlank(message = "学校名字不能为空")
    private String schoolName;

    @NotBlank(message = "学校位置不能为空")
    private String location;

    private Integer lockerCount;
}
