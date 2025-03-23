package com.smartwash.from.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserInfo {
    @NotNull(message = "请选择学校")
    private Long schoolId;
    @NotBlank(message = "请输入学生信息")
    private String studentId;
}
