package com.smartwash.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.PaymentStatus;
import com.smartwash.common.RechargeType;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.service.ExportService;
import com.smartwash.service.IOrdersService;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 数据导出实现：
 * 1. 订单 CSV 导出（评审报告后端 #32 加固）：分批（每批 {@link #BATCH_SIZE} 条）循环查询 + 流式写出，
 *    替代原"单次 1 万条进内存"的实现，且不再有 1 万条截断——满批即继续翻页，直到末批，全量导出；
 *    单元格统一转义（逗号/双引号/换行）并做公式注入防护（见 {@link #escapeCsvCell}）。
 * 2. 充值记录 xlsx 导出：同样分批循环查询，SXSSF 流式写出（内存仅保留滑动窗口，其余落临时文件）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    /** 分批拉取批大小：每批 1000 条，写完即刷出释放，内存中最多滞留一批数据 */
    static final int BATCH_SIZE = 1000;

    private static final String CSV_HEADER = "订单ID,订单号,总价,实付金额,状态,下单时间";
    /** UTF-8 BOM：保证 Excel 直接打开时中文表头不乱码（沿用原实现） */
    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    /** CSV 标准行分隔符（RFC 4180） */
    private static final String LINE_SEPARATOR = "\r\n";
    /** 时间输出格式（原实现为 LocalDateTime 原生 toString，统一为常见可读格式） */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 充值记录 xlsx 表头（列顺序与 {@link #writeRechargeRow} 写出顺序一致） */
    private static final String[] RECHARGE_EXCEL_HEADER = {"ID", "用户", "金额", "渠道", "状态", "时间"};
    /** SXSSF 内存窗口行数：窗口外行自动落盘临时文件，导出不随数据量膨胀内存 */
    private static final int SXSSF_WINDOW_SIZE = 100;

    private final IOrdersService ordersService;
    private final IRechargeRecordsService rechargeRecordsService;

    @Override
    public void exportOrdersCsv(SearchOrderFrom searchFrom, OutputStream out) throws IOException {
        out.write(UTF_8_BOM);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.write(CSV_HEADER);
            writer.write(LINE_SEPARATOR);

            long exportedCount = 0;
            int page = 1;
            List<OrdersVo> batch;
            do {
                // 逐批改写分页参数拉取：满批说明可能还有下一页，不满批即为末页（分批全量导出，无固定条数上限）
                searchFrom.setPage(page);
                searchFrom.setSize(BATCH_SIZE);
                batch = ordersService.getAllOrders(searchFrom).getRecords();
                for (OrdersVo order : batch) {
                    writeOrderRow(writer, order);
                }
                exportedCount += batch.size();
                writer.flush(); // 每批写完立即刷出，避免响应缓冲堆积
                page++;
            } while (batch.size() == BATCH_SIZE);

            log.info("导出订单 CSV 完成, 共 {} 条记录, 批次数 {}", exportedCount, page - 1);
        }
    }

    @Override
    public void exportRechargeRecordsExcel(LocalDateTime dateFrom, LocalDateTime dateTo, OutputStream out) throws IOException {
        SearchRechargeRecordsFrom searchFrom = new SearchRechargeRecordsFrom();
        searchFrom.setStartTime(dateFrom);
        searchFrom.setEndTime(dateTo);

        // SXSSF：仅滑动窗口驻留内存，close 时随 workbook 一并清理临时文件
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_WINDOW_SIZE)) {
            Sheet sheet = workbook.createSheet("充值记录");
            Row header = sheet.createRow(0);
            for (int i = 0; i < RECHARGE_EXCEL_HEADER.length; i++) {
                header.createCell(i).setCellValue(RECHARGE_EXCEL_HEADER[i]);
            }

            long exportedCount = 0;
            int page = 1;
            int rowIdx = 1;
            List<RechargeRecordsVo> batch;
            do {
                // 与订单导出同模式：满批继续翻页直到末批，全量导出无条数截断
                searchFrom.setPage(page);
                searchFrom.setSize(BATCH_SIZE);
                batch = rechargeRecordsService.getAllRecords(searchFrom).getRecords();
                for (RechargeRecordsVo record : batch) {
                    writeRechargeRow(sheet.createRow(rowIdx++), record);
                }
                exportedCount += batch.size();
                page++;
            } while (batch.size() == BATCH_SIZE);

            workbook.write(out);
            log.info("导出充值记录 xlsx 完成, 共 {} 条记录, 批次数 {}", exportedCount, page - 1);
        }
    }

    /** 写出一行充值记录（列顺序与 RECHARGE_EXCEL_HEADER 一致） */
    private void writeRechargeRow(Row row, RechargeRecordsVo record) {
        if (record.getRecordId() != null) {
            row.createCell(0).setCellValue(record.getRecordId());
        }
        row.createCell(1).setCellValue(record.getUsers() != null && record.getUsers().getPhoneNumber() != null
                ? record.getUsers().getPhoneNumber() : "");
        // 金额写数值单元格，Excel 内可直接求和筛选
        row.createCell(2).setCellValue(record.getAmount() != null ? record.getAmount().doubleValue() : 0d);
        row.createCell(3).setCellValue(rechargeTypeText(record.getRechargeType()));
        row.createCell(4).setCellValue(paymentStatusText(record.getStatus()));
        row.createCell(5).setCellValue(record.getRechargeTime() == null ? "" : EXPORT_TIME_FORMATTER.format(record.getRechargeTime()));
    }

    /** 充值渠道文本：1-微信支付 2-支付宝支付（未知码原样输出，不丢信息） */
    static String rechargeTypeText(String type) {
        if (type == null) {
            return "";
        }
        for (RechargeType rechargeType : RechargeType.values()) {
            if (rechargeType.getType().equals(type)) {
                return rechargeType.getMessage();
            }
        }
        return type;
    }

    /** 充值状态文本：复用 PaymentStatus 语义（0-待支付 1-充值成功 2-充值失败 3-处理中），未知码原样输出 */
    static String paymentStatusText(String status) {
        if (status == null) {
            return "";
        }
        for (PaymentStatus paymentStatus : PaymentStatus.values()) {
            if (paymentStatus.getStatus().equals(status)) {
                return paymentStatus.getDescription();
            }
        }
        return status;
    }

    /** 写出一行订单数据（列顺序与表头一致） */
    private void writeOrderRow(BufferedWriter writer, OrdersVo order) throws IOException {
        writer.write(String.join(",",
                Objects.toString(order.getOrderId(), ""),
                escapeCsvCell(order.getOrderNo()),
                formatAmount(order.getTotalPrice()),
                formatAmount(order.getPayPrice()),
                escapeCsvCell(order.getStatus()),
                escapeCsvCell(order.getCreatedAt() == null ? null : EXPORT_TIME_FORMATTER.format(order.getCreatedAt()))));
        writer.write(LINE_SEPARATOR);
    }

    /**
     * CSV 单元格转义：
     * 1. 公式注入防护（CSV Injection）：以 =、+、-、@、Tab、CR 开头的文本值前置单引号，
     *    防止 Excel/WPS 打开文件时把单元格内容当公式执行（如 "=HYPERLINK(...)"、"@SUM(A1)"）；
     * 2. 含逗号、双引号、换行（LF/CR）的字段用双引号包裹，内部双引号翻倍转义（RFC 4180）。
     * 仅文本单元格走本方法；金额/ID 等数值列由服务端 BigDecimal/主键生成，不含公式字符。
     */
    static String escapeCsvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String cell = value;
        char first = cell.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            cell = "'" + cell;
        }
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
            cell = '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }

    /** 金额格式化：Locale.ROOT 固定小数点为"."，避免宿主区域使用逗号小数点时破坏 CSV 列结构 */
    private static String formatAmount(BigDecimal amount) {
        return String.format(Locale.ROOT, "%.2f", amount != null ? amount : BigDecimal.ZERO);
    }
}
