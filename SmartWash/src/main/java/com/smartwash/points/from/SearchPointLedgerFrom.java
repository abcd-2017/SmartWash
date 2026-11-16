package com.smartwash.points.from;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分流水分页筛选 DTO（用户端流水页）。
 * 继承 BaseSearchFrom：page 默认 1、size 默认 10、page/size 最小 1。
 * 积分流水单页上限 50 由服务层收口（超出按 50 截断），与 BaseSearchFrom 的通用上限 100 解耦。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPointLedgerFrom extends BaseSearchFrom {
}
