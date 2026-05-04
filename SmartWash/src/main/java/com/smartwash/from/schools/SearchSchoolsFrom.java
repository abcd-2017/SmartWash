package com.smartwash.from.schools;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchSchoolsFrom extends BaseSearchFrom {
    private Long schoolId;

    private String schoolName;

    private String schoolCode;

    private String province;

    private String city;

    private Integer lockerCount;
}
