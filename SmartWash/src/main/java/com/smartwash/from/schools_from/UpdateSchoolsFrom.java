package com.smartwash.from.schools_from;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSchoolsFrom {
    @NotNull(message = "请选择学校")
    private Long schoolId;

    private String schoolName;

    private String location;

    private Integer lockerCount;
}
