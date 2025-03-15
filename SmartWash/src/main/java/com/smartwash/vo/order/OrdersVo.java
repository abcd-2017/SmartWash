package com.smartwash.vo.order;

import com.smartwash.vo.laudry.LaundryPackageVo;
import com.smartwash.vo.locker.LockersVo;
import com.smartwash.vo.schools.SchoolsVo;
import com.smartwash.vo.users.UserVo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrdersVo {
    private Long orderId;

    private UserVo userVo;

    private SchoolsVo schoolsVo;

    private LockersVo lockersVo;

    private String orderNo;

    private LaundryPackageVo laundryPackageVo;

    private BigDecimal totalPrice;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String status;

    private String pickupCode;
}
