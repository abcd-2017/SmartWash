package com.smartwash.vo.users;

import com.smartwash.vo.schools.SchoolsVo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserInfoVo {
    private String phoneNumber;

    private String studentId;

    private String campusCard;

    private BigDecimal balance;

    private String avatar;

    private SchoolsVo schoolVo;
}
