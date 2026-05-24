package com.smartwash.service;

import com.smartwash.from.order.SearchOrderFrom;

/**
 * 数据导出服务接口
 */
public interface ExportService {

    /**
     * 导出订单为 CSV
     *
     * @param searchFrom 搜索条件
     * @return CSV 内容字节数组
     */
    byte[] exportOrdersCsv(SearchOrderFrom searchFrom);
}
