package com.smartwash.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.service.ExportService;
import com.smartwash.service.IOrdersService;
import com.smartwash.vo.order.OrdersVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final IOrdersService ordersService;

    @Override
    public byte[] exportOrdersCsv(SearchOrderFrom searchFrom) {
        // 设置较大分页以获取全部数据
        searchFrom.setPage(1);
        searchFrom.setSize(10000);

        Page<OrdersVo> orders = ordersService.getAllOrders(searchFrom);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        // BOM for Excel
        writer.write('﻿');
        // CSV Header
        writer.println("订单ID,订单号,总价,实付金额,状态,下单时间");

        for (OrdersVo order : orders.getRecords()) {
            writer.printf("%d,%s,%.2f,%.2f,%s,%s%n",
                    order.getOrderId(),
                    order.getOrderNo(),
                    order.getTotalPrice() != null ? order.getTotalPrice() : 0,
                    order.getPayPrice() != null ? order.getPayPrice() : 0,
                    order.getStatus(),
                    order.getCreatedAt()
            );
        }

        writer.flush();
        log.info("导出订单 CSV, 共 {} 条记录", orders.getRecords().size());
        return out.toByteArray();
    }
}
