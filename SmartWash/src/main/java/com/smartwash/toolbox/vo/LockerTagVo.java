package com.smartwash.toolbox.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 柜子资产码公开解析结果（扫码落地页信息卡）。
 * 只含公开事实（柜号/状态/学校/位置），不含柜内订单等私有信息。
 */
@Data
@Accessors(chain = true)
public class LockerTagVo {

    private Long lockerId;

    /** 柜位编号 */
    private Integer lockerNumber;

    /** 柜子状态：0 空闲 / 1 使用中 / 2 故障（端上按既有 LockerStatusEnum 字典映射） */
    private String status;

    /** 学校名称 */
    private String schoolName;

    /** 学校详细地址（lockers 挂在学校维度） */
    private String schoolLocation;
}
