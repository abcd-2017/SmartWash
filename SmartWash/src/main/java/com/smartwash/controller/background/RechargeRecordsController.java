package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.from.recharge_records.RechargeDateRangeFrom;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.service.ExportService;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import com.smartwash.vo.recharge_records.RechargeSummaryVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 充值记录管理控制器
 *
 * <p>说明：本控制器承载两套管理端路径——既有列表 {@code /admin/rechargeRecords/all}
 * （Web 充值列表页在用，保持不变）与新增的汇总/导出 {@code /admin/recharge/summary|export}，
 * 因此各方法使用完整路径标注，类上不再统一声明 @RequestMapping 前缀。</p>
 */
@Tag(name = "充值记录管理", description = "后台充值记录查询、汇总统计与导出")
@Slf4j
@RestController
public class RechargeRecordsController {
    @Autowired
    private IRechargeRecordsService rechargeRecordsService;

    @Autowired
    private ExportService exportService;

    @Operation(summary = "分页查询充值记录列表", description = "根据条件分页查询用户充值记录列表")
    @GetMapping("/admin/rechargeRecords/all")
    public Result<Page<RechargeRecordsVo>> getAll(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        String phoneNumber = rechargeRecordsFrom.getPhoneNumber();
        if (StringUtils.hasText(phoneNumber)) {
            if (phoneNumber.length() > 11) return Result.failMsg("手机号长度错误");
            if (!phoneNumber.matches("^(\\+86)?1[3-9]\\d{9}$")) return Result.failMsg("手机号格式错误");
        }
        return Result.ok(rechargeRecordsService.getAllRecords(rechargeRecordsFrom));
    }

    @Operation(summary = "充值汇总统计", description = "按日期范围汇总充值总笔数、成功笔数、总金额、成功金额，日期可空 = 不限")
    @GetMapping("/admin/recharge/summary")
    public Result<RechargeSummaryVo> getSummary(RechargeDateRangeFrom rangeFrom) {
        return Result.ok(rechargeRecordsService.getRechargeSummary(rangeFrom.getDateFrom(), rangeFrom.getDateTo()));
    }

    @Operation(summary = "导出充值记录 Excel", description = "按日期范围导出充值记录为 xlsx 文件（分批流式写出，全量导出无条数截断），日期可空 = 不限")
    @GetMapping("/admin/recharge/export")
    public void exportRechargeRecords(RechargeDateRangeFrom rangeFrom, HttpServletResponse response) throws IOException {
        // 响应头语义与订单导出一致：附件下载，固定文件名（xlsx MIME）
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=recharge_records.xlsx");
        // 分批流式写出，不整表进内存（与订单导出同模式）
        exportService.exportRechargeRecordsExcel(rangeFrom.getDateFrom(), rangeFrom.getDateTo(), response.getOutputStream());
    }
}
