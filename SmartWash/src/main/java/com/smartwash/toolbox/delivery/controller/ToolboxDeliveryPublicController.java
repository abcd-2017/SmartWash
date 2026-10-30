package com.smartwash.toolbox.delivery.controller;

import com.smartwash.common.Result;
import com.smartwash.toolbox.delivery.from.EnterDeliverySessionFrom;
import com.smartwash.toolbox.delivery.from.PublishDeliveryTextFrom;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService.DeliveryContent;
import com.smartwash.toolbox.delivery.toolkit.DeliveryFileStreaming;
import com.smartwash.toolbox.delivery.vo.DeliveryItemTakenVo;
import com.smartwash.toolbox.delivery.vo.DeliveryItemVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionDetailVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 工作台设备投递对端控制器（电脑浏览器凭投递码匿名访问）。
 * 路径前缀 /web/delivery/**，SecurityConfig 公开白名单放行（对齐 /web/t/** 先例，码即凭证）。
 * 进入时写 Redis 占用键实现单对端互斥（被占 409），条目操作与 owner 侧同构（direction=2）。
 */
@Tag(name = "工作台-设备投递对端", description = "凭投递码匿名进入与双向条目接口（浏览器侧，公开段）")
@Slf4j
@RestController
@RequestMapping("/web/delivery")
@RequiredArgsConstructor
public class ToolboxDeliveryPublicController {

    private final ToolboxDeliveryService toolboxDeliveryService;
    private final DeliveryFileStreaming deliveryFileStreaming;

    @Operation(summary = "进入会话", description = "凭码匿名进入，可选密码（错误响应延迟约500ms防爆破）；单对端占用（被占 409 语义）；计入会话进入次数")
    @PostMapping("/{code}/enter")
    public Result<DeliverySessionVo> enter(@PathVariable("code") String code,
                                           @RequestBody(required = false) @Valid EnterDeliverySessionFrom from) {
        String password = from == null ? null : from.getPassword();
        return Result.ok(toolboxDeliveryService.enter(code, password));
    }

    @Operation(summary = "条目流", description = "对端视角全量条目（含已焚毁占位），过期/关闭一律 404 语义；2-3s 轮询消费")
    @GetMapping("/{code}/items")
    public Result<DeliverySessionDetailVo> items(@PathVariable("code") String code) {
        return Result.ok(toolboxDeliveryService.peerSessionDetail(code));
    }

    @Operation(summary = "发布文本条目", description = "direction 固定 2（电脑→手机）；条目数超限返回 413 语义")
    @PostMapping("/{code}/items")
    public Result<DeliveryItemVo> publishText(@PathVariable("code") String code,
                                              @RequestBody @Valid PublishDeliveryTextFrom from) {
        return Result.ok(toolboxDeliveryService.peerPublishText(code, from));
    }

    @Operation(summary = "发布文件/图片条目", description = "multipart 上传，kind=2图片/3文件；单文件超20MB或会话总量超200MB返回 413 语义")
    @PostMapping("/{code}/items/file")
    public Result<DeliveryItemVo> publishFile(@PathVariable("code") String code,
                                              @RequestParam("kind") Integer kind,
                                              @RequestParam("file") MultipartFile file) {
        return Result.ok(toolboxDeliveryService.peerPublishFile(code, kind, file));
    }

    @Operation(summary = "取走条目", description = "原子认领（UPDATE WHERE taken=0 判影响行数）：文本一步焚毁并返回内容；文件认领后经 content 接口流式下载")
    @PostMapping("/{code}/items/{itemId}/take")
    public Result<DeliveryItemTakenVo> take(@PathVariable("code") String code,
                                            @PathVariable("itemId") Long itemId) {
        return Result.ok(toolboxDeliveryService.takeItem(code, itemId));
    }

    @Operation(summary = "条目内容", description = "文本返回 JSON（焚毁后内容为空）；文件流式转发（须先 take 认领，下载完成即焚毁，中断可重取），不暴露对象 URL")
    @GetMapping("/{code}/items/{itemId}/content")
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
