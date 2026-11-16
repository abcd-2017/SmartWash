package com.smartwash.toolbox.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LockerTagBatchItemVo {

    private Long lockerId;

    /** 柜位编号（打印排版用） */
    private Integer lockerNumber;

    /** 柜码别名（L{lockerId}，uk_code 幂等键） */
    private String code;

    /** 完整跳转 URL（{toolbox.public-base-url}/web/t/{code}，打印排版用） */
    private String shortUrl;

    /** true = 本次新建；false = 已存在复用（幂等重生成不重复建行） */
    private boolean created;
}
