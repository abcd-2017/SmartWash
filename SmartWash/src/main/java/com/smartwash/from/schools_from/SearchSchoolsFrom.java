package com.smartwash.from.schools_from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchSchoolsFrom extends BaseSearchFrom {
    private Long schoolId;

    private String schoolName;

    private Integer lockerCount;
}
