package com.smartwash.game.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.game.from.SearchGameFrom;
import com.smartwash.game.from.UpdateGameFrom;
import com.smartwash.game.from.UploadGameFrom;
import com.smartwash.game.service.GameConfigService;
import com.smartwash.game.vo.AdminGameVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 游戏库管理端控制器。
 * 路径前缀 /admin/game，需 ROLE_ADMIN（SecurityConfig 统一拦截）。
 * 写操作统一失效用户端下发缓存（gameList），保证端上拉到的列表即时更新。
 * 上传错误码语义：400 参数/包内容不合法、403 包内路径穿越、404 不存在、409 gameKey 并发冲突、413 体积超限、500 存储故障。
 * 四端联动说明：本组为新增接口，Web 管理后台为消费方（游戏库页），Android/鸿蒙不消费。
 */
@Tag(name = "游戏中心-管理端", description = "H5 游戏库上传/编辑/上下架/删除（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/game")
@RequiredArgsConstructor
public class GameAdminController {

    private final GameConfigService gameConfigService;

    @Operation(summary = "上传游戏", description = "multipart 文件（.html/.htm/.zip，≤10MB）+ 表单元数据；同 gameKey 重复上传 = 覆盖更新；url 落 MinIO 对象直链（指向入口文件）；上传后默认不下发，需调开关接口上架；zip 防炸弹（≤200 文件、解压 ≤20MB）与 ZipSlip 防护")
    @PostMapping("/upload")
    public Result<AdminGameVo> upload(@RequestPart("file") MultipartFile file,
                                      @Valid @ModelAttribute UploadGameFrom from) {
        return Result.ok(gameConfigService.upload(from, file));
    }

    @Operation(summary = "分页查询游戏列表", description = "管理视角含未启用项；keyword 模糊匹配 title/gameKey + category/enabled 精确筛选，按 sort/id 升序")
    @GetMapping("/list")
    public Result<Page<AdminGameVo>> list(SearchGameFrom from) {
        return Result.ok(gameConfigService.adminPage(from));
    }

    @Operation(summary = "编辑游戏元数据", description = "gameKey 不可修改；enabled 走专用开关接口；description/badge/remark 传空字符串表示清空")
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable("id") @Parameter(description = "游戏ID", required = true) Long id,
                                 @RequestBody @Valid UpdateGameFrom from) {
        gameConfigService.updateGame(id, from);
        return Result.ok("修改成功");
    }

    @Operation(summary = "上下架开关", description = "CAS 条件更新判影响行数，语义 = 选择哪些下发；不存在 404，已是目标状态幂等成功；写后用户端列表即时生效")
    @PostMapping("/{id}/enabled/{enabled}")
    public Result<String> changeEnabled(@PathVariable("id") @Parameter(description = "游戏ID", required = true) Long id,
                                        @PathVariable("enabled") @Parameter(description = "1下发 0不下发", required = true) Integer enabled) {
        gameConfigService.changeEnabled(id, enabled);
        return Result.ok("操作成功");
    }

    @Operation(summary = "删除游戏", description = "物理删除记录并清理 MinIO game/{gameKey}/ 前缀全部对象；存储清理失败将阻断删除（可重试）")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") @Parameter(description = "游戏ID", required = true) Long id) {
        gameConfigService.deleteGame(id);
        return Result.ok(true);
    }
}
