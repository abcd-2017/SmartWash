package com.smartwash.service;

import com.smartwash.from.order.SearchOrderFrom;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 数据导出服务接口
 */
public interface ExportService {

    /**
     * 导出订单为 CSV：分批（每批 1000 条）循环查询并流式写入输出流，
     * 不再整表载入内存，也不再按固定条数截断（评审报告后端 #32）。
     * 下载响应头（Content-Type / 文件名）由调用方设置。
     *
     * @param searchFrom 搜索条件（方法内部会逐批改写其分页参数）
     * @param out        目标输出流（通常为 HTTP 响应输出流）
     * @throws IOException 输出流写入失败时抛出
     */
    void exportOrdersCsv(SearchOrderFrom searchFrom, OutputStream out) throws IOException;
}
