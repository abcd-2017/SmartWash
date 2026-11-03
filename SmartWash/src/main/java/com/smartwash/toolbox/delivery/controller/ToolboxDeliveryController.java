package com.smartwash.toolbox.delivery.controller;

import com.smartwash.common.Result;
import com.smartwash.toolbox.delivery.from.CreateDeliverySessionFrom;
import com.smartwash.toolbox.delivery.from.PublishDeliveryTextFrom;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService.DeliveryContent;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService.DeliveryFileDownload;
import com.smartwash.toolbox.delivery.toolkit.DeliveryFileStreaming;
import com.smartwash.toolbox.delivery.vo.DeliveryItemTakenVo;
import com.smartwash.toolbox.delivery.vo.DeliveryItemVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionDetailVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 工作台设备投递用户端控制器（owner = 会话主人，ROLE_USER）。
 * 路径前缀 /web/auth/toolbox/delivery（对齐工具箱短链控制器先例，落 /web/auth/** 认证段）。
 * 条目操作与对端同构（码即凭证）；本控制器额外提供会话管理面（创建/详情/关闭，归属校验）。
 */
@Tag(name = "工作台-设备投递", description = "投递会话创建/管理与双向条目接口（手机侧）")
@Slf4j
@RestController
@RequestMapping("/web/auth/toolbox/delivery")
@RequiredArgsConstructor
public class ToolboxDeliveryController {

    private final ToolboxDeliveryService toolboxDeliveryService;
    private final DeliveryFileStreaming deliveryFileStreaming;

    @Operation(summary = "创建投递会话", description = "可选密码与有效期（默认30分钟，上限120）；返回投递码、过期时刻与对端 H5 地址")
    @PostMapping("/sessions")
    public Result<DeliverySessionVo> create(@RequestBody @Valid CreateDeliverySessionFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxDeliveryService.createSession(from, user.getUserId()));
    }

    @Operation(summary = "会话详情与条目流", description = "owner 视角全量条目（含已焚毁占位），归属不符按 404 语义")
    @GetMapping("/sessions/{code}")
    public Result<DeliverySessionDetailVo> detail(@PathVariable("code") String code) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxDeliveryService.ownerSessionDetail(code, user.getUserId()));
    }

    @Operation(summary = "关闭会话", description = "expire_at 前置为当前时刻，读路径立即失效；物理数据由定时清理回收")
    @PostMapping("/sessions/{code}/close")
    public Result<Void> close(@PathVariable("code") String code) {
        LoginUser user = UserContextHolder.getUser();
        toolboxDeliveryService.closeSession(code, user.getUserId());
        return Result.ok(null);
    }

    @Operation(summary = "发布文本条目", description = "direction 固定 1（手机→电脑）；条目数超限返回 413 语义")
    @PostMapping("/sessions/{code}/items")
    public Result<DeliveryItemVo> publishText(@PathVariable("code") String code,
                                              @RequestBody @Valid PublishDeliveryTextFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxDeliveryService.publishText(code, from, user.getUserId()));
    }

    @Operation(summary = "发布文件/图片条目", description = "multipart 上传，kind=2图片/3文件；单文件超20MB或会话总量超200MB返回 413 语义")
    @PostMapping("/sessions/{code}/items/file")
    public Result<DeliveryItemVo> publishFile(@PathVariable("code") String code,
                                              @RequestParam("kind") Integer kind,
                                              @RequestParam("file") MultipartFile file) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxDeliveryService.publishFile(code, kind, file, user.getUserId()));
    }

    @Operation(summary = "取走条目", description = "原子认领（UPDATE WHERE taken=0 判影响行数）：文本一步焚毁并返回内容；文件认领后经 content 接口流式下载")
    @PostMapping("/sessions/{code}/items/{itemId}/take")
    public Result<DeliveryItemTakenVo> take(@PathVariable("code") String code,
                                            @PathVariable("itemId") Long itemId) {
        return Result.ok(toolboxDeliveryService.takeItem(code, itemId));
    }

    @Operation(summary = "条目内容", description = "文本返回 JSON（焚毁后内容为空）；文件流式转发（须先 take 认领，下载完成即焚毁，中断可重取），不暴露对象 URL")
    @GetMapping("/sessions/{code}/items/{itemId}/content")
    public ResponseEntity<?> content(@PathVariable("code") String code,
                                     @PathVariable("itemId") Long itemId) {
        DeliveryContent content = toolboxDeliveryService.content(code, itemId);
        if (content.getFileDownload() == null) {
            DeliveryItemTakenVo vo = new DeliveryItemTakenVo()
                    .setId(itemId)
                    .setKind(content.getKind())
                    .setTextContent(content.getTextContent());
            return ResponseEntity.ok().body(Result.ok(vo));
        }
        return deliveryFileStreaming.toResponse(toolboxDeliveryService, content.getFileDownload());
    }
}
