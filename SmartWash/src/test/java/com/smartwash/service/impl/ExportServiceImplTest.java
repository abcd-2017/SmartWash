package com.smartwash.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.service.IOrdersService;
import com.smartwash.vo.order.OrdersVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单 CSV 导出加固回归测试（评审报告后端 #32）。
 * 闸门语义：分批拉取流式写出（不再一次性 1 万条进内存、不再截断），
 * 单元格完整转义（逗号/引号/换行）+ 公式注入防护（=、+、-、@、Tab、CR 前缀加单引号）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExportServiceImpl CSV 导出加固测试")
class ExportServiceImplTest {

    @Mock
    private IOrdersService ordersService;

    private ExportServiceImpl exportService;

    @BeforeEach
    void setUp() {
        exportService = new ExportServiceImpl(ordersService);
    }

    // ==================== 单元格转义 ====================

    @Test
    @DisplayName("escapeCsvCell：含逗号/双引号/换行的字段用双引号包裹且内部引号翻倍")
    void escapeCsvCell_specialChars_wrappedAndDoubled() {
        assertEquals("\"a,b\"", ExportServiceImpl.escapeCsvCell("a,b"), "逗号必须包裹");
        assertEquals("\"say \"\"hi\"\"\"", ExportServiceImpl.escapeCsvCell("say \"hi\""), "内部双引号必须翻倍");
        assertEquals("\"line1\nline2\"", ExportServiceImpl.escapeCsvCell("line1\nline2"), "换行必须包裹");
        assertEquals("\"a,\"\"b\"\"\nc\"", ExportServiceImpl.escapeCsvCell("a,\"b\"\nc"), "多特殊字符叠加时先引号翻倍再包裹");
        assertEquals("normal", ExportServiceImpl.escapeCsvCell("normal"), "普通文本不应被改写");
        assertEquals("", ExportServiceImpl.escapeCsvCell(null), "null 输出空串");
        assertEquals("", ExportServiceImpl.escapeCsvCell(""), "空串输出空串");
    }

    @Test
    @DisplayName("escapeCsvCell：=、+、-、@、Tab、CR 开头的公式注入前缀一律加单引号")
    void escapeCsvCell_formulaPrefix_prefixedWithQuote() {
        // 值本身含双引号，加前缀后还会触发包裹转义：'=HYPERLINK(""http://evil"") 外层再包双引号
        assertEquals("\"'=HYPERLINK(\"\"http://evil\"\")\"",
                ExportServiceImpl.escapeCsvCell("=HYPERLINK(\"http://evil\")"));
        assertEquals("'@SUM(A1)", ExportServiceImpl.escapeCsvCell("@SUM(A1)"));
        assertEquals("'-1+cmd|' /C calc", ExportServiceImpl.escapeCsvCell("-1+cmd|' /C calc"));
        assertEquals("'+8613800000000", ExportServiceImpl.escapeCsvCell("+8613800000000"));
        assertEquals("'\tTAB", ExportServiceImpl.escapeCsvCell("\tTAB"));
        // CR 开头：加前缀后因包含 CR 还会触发包裹
        assertEquals("\"'\rCR\"", ExportServiceImpl.escapeCsvCell("\rCR"));
    }

    // ==================== 分批流式导出 ====================

    @Test
    @DisplayName("导出满批自动翻页：1000+3 条跨两批全量写出，无 1 万条截断")
    void exportOrdersCsv_fullBatchPagination_exportsAll() throws Exception {
        SearchOrderFrom searchFrom = new SearchOrderFrom();
        Page<OrdersVo> page1 = new Page<>(1, ExportServiceImpl.BATCH_SIZE);
        page1.setRecords(batch(ExportServiceImpl.BATCH_SIZE, 0));
        Page<OrdersVo> page2 = new Page<>(2, ExportServiceImpl.BATCH_SIZE);
        page2.setRecords(batch(3, ExportServiceImpl.BATCH_SIZE));
        // 服务会原地改写同一 SearchOrderFrom 的分页参数，必须在调用时刻记录快照（captor 只存引用）
        List<Integer> pagesAtCall = new ArrayList<>();
        List<Integer> sizesAtCall = new ArrayList<>();
        when(ordersService.getAllOrders(any(SearchOrderFrom.class))).thenAnswer(inv -> {
            SearchOrderFrom from = inv.getArgument(0);
            pagesAtCall.add(from.getPage());
            sizesAtCall.add(from.getSize());
            return pagesAtCall.size() == 1 ? page1 : page2;
        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exportOrdersCsv(searchFrom, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        // 表头 + 1003 行数据；行分隔符为 RFC 4180 的 CRLF
        assertEquals(1 + ExportServiceImpl.BATCH_SIZE + 3, csv.split("\r\n").length);
        // 传参断言：逐批以 page=1,2、size=1000 查询
        assertEquals(List.of(1, 2), pagesAtCall, "第一批 page=1，满批后第二批 page=2");
        assertEquals(List.of(ExportServiceImpl.BATCH_SIZE, ExportServiceImpl.BATCH_SIZE), sizesAtCall);
        verify(ordersService, times(2)).getAllOrders(any(SearchOrderFrom.class));
    }

    @Test
    @DisplayName("导出内容：表头/金额格式正确，含逗号引号与公式前缀的单元格正确转义")
    void exportOrdersCsv_rowContent_escapedAndFormatted() throws Exception {
        OrdersVo order = new OrdersVo();
        order.setOrderId(1L);
        order.setOrderNo("ORD,1\"2\n3");
        order.setTotalPrice(new BigDecimal("12.3"));
        order.setPayPrice(new BigDecimal("-4.5"));
        order.setStatus("=1+1");
        order.setCreatedAt(LocalDateTime.of(2026, 8, 29, 10, 30, 0));
        Page<OrdersVo> page = new Page<>(1, ExportServiceImpl.BATCH_SIZE);
        page.setRecords(List.of(order));
        when(ordersService.getAllOrders(any(SearchOrderFrom.class))).thenReturn(page);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exportOrdersCsv(new SearchOrderFrom(), out);

        String raw = out.toString(StandardCharsets.UTF_8);
        String csv = raw.replace("\uFEFF", ""); // 去 BOM 后校验内容
        // 值内是 LF 不是 CRLF，split("\r\n") 不会切断被包裹的字段
        String[] lines = csv.split("\r\n");
        assertEquals("订单ID,订单号,总价,实付金额,状态,下单时间", lines[0]);
        assertEquals("1,\"ORD,1\"\"2\n3\",12.30,-4.50,'=1+1,2026-08-29 10:30:00", lines[1]);
        assertTrue(raw.startsWith("\uFEFF"), "必须保留 UTF-8 BOM（Excel 中文兼容）");
    }

    @Test
    @DisplayName("金额为 null 时按 0.00 输出，不抛 NPE")
    void exportOrdersCsv_nullAmount_zeroFormatted() throws Exception {
        OrdersVo order = new OrdersVo();
        order.setOrderId(2L);
        order.setOrderNo("ORD2");
        order.setStatus("0");
        Page<OrdersVo> page = new Page<>(1, ExportServiceImpl.BATCH_SIZE);
        page.setRecords(List.of(order));
        when(ordersService.getAllOrders(any(SearchOrderFrom.class))).thenReturn(page);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exportOrdersCsv(new SearchOrderFrom(), out);

        String csv = out.toString(StandardCharsets.UTF_8).replace("\uFEFF", "");
        assertEquals("2,ORD2,0.00,0.00,0,", csv.split("\r\n")[1]);
    }

    /** 构造一批最小化订单 VO */
    private List<OrdersVo> batch(int count, long idStart) {
        List<OrdersVo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OrdersVo vo = new OrdersVo();
            vo.setOrderId(idStart + i + 1);
            vo.setOrderNo("ORD" + (idStart + i + 1));
            vo.setTotalPrice(BigDecimal.ONE);
            vo.setPayPrice(BigDecimal.ONE);
            vo.setStatus("0");
            vo.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            list.add(vo);
        }
        return list;
    }
}
