package com.smartwash.from.schools;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddSchoolsFrom {
    @NotBlank(message = "学校名字不能为空")
    private String schoolName;

    @NotBlank(message = "学校编码不能为空")
    private String schoolCode;

    @NotBlank(message = "学校位置不能为空")
    private String location;

    private String province;

    private String city;

    private String district;

    private java.math.BigDecimal longitude;

    private java.math.BigDecimal latitude;

    private String logoUrl;

    private String contactName;

    private String contactPhone;

    @NotNull(message = "存储柜数量不能为空")
    private Integer lockerCount;
}
