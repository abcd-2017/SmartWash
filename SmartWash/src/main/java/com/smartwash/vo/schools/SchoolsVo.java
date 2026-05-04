package com.smartwash.vo.schools;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchoolsVo {
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

    private LocalDateTime createdAt;

}
