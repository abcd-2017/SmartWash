package com.smartwash.workbench.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.workbench.from.AddDeeplinkRouteFrom;
import com.smartwash.workbench.from.SearchDeeplinkRouteFrom;
import com.smartwash.workbench.from.UpdateDeeplinkRouteFrom;
import com.smartwash.workbench.service.DeeplinkRouteService;
import com.smartwash.workbench.vo.DeeplinkRouteVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * App deeplink 路由字典管理端控制器。
 * 路径前缀 /admin/deeplink/routes，需 ROLE_ADMIN（SecurityConfig 统一拦截）。
 * 字典数据源：管理端配置 workbench_entry.target 时的合法 host 参考；
 * 写操作统一失效公开下发缓存（deeplinkRoutes）。
 */
@Tag(name = "工作台-路由字典管理端", description = "App deeplink 路由字典维护（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/deeplink/routes")
@RequiredArgsConstructor
public class DeeplinkRouteAdminController {

    private final DeeplinkRouteService deeplinkRouteService;

    @Operation(summary = "分页查询路由字典", description = "page/size + host 模糊 + enabled 筛选，按 host 升序")
    @GetMapping("/all")
    public Result<Page<DeeplinkRouteVo>> getAll(SearchDeeplinkRouteFrom from) {
        return Result.ok(deeplinkRouteService.adminPage(from));
    }

    @Operation(summary = "新增路由", description = "host 全局唯一（重复拒绝且创建后不可修改）；platform：1 Android 2 HarmonyOS 3 双端")
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid AddDeeplinkRouteFrom from) {
        deeplinkRouteService.addRoute(from);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新路由", description = "host 不可修改（改 host 等于改路由，端上须发版支持）；description 传空字符串表示清空")
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid UpdateDeeplinkRouteFrom from) {
        deeplinkRouteService.updateRoute(from);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除路由", description = "根据ID批量物理删除，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> delete(@PathVariable("ids") @Parameter(description = "路由ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        deeplinkRouteService.deleteRoutes(ids);
        return Result.ok(true);
    }
}
