package com.smartwash.from.schools;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSchoolsFrom {
    @NotNull(message = "请选择学校")
    private Long schoolId;

    private String schoolName;

    private String schoolCode;

    private String location;

    private String province;

    private String city;

    private String district;

    private java.math.BigDecimal longitude;

    private java.math.BigDecimal latitude;

    private String logoUrl;

    private String contactName;

    private String contactPhone;

    private Integer lockerCount;
}
