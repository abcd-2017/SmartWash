package com.smartwash.from.users_from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchUserFrom extends BaseSearchFrom {
    private Long userId;

    private Long schoolId;

    private String phoneNumber;

    private String studentId;

    private String campusCard;
}
