package com.smartwash.dining.support;

import com.smartwash.exception.CustomExceptions;
import com.smartwash.utils.ImageUploadValidator;
import org.springframework.web.multipart.MultipartFile;

/**
 * 菜谱封面上传校验（纯函数式静态工具，便于单测）。
 * 校验实现已下沉至通用 {@link ImageUploadValidator}（菜谱封面 / 头像 / 工作台配图 / 游戏封面
 * 统一口径：5MB 上限 + 扩展名白名单 + 魔数 + 扩展名一致性），本类保留菜谱语境的入口文案
 * 并维持既有公共 API（管理端/用户端封面上传与既有单测依赖），全部委托通用校验器执行。
 * 校验不通过抛 {@link CustomExceptions}（管理端信封语义：413 走超限码，其余走默认失败码）。
 */
public final class RecipeImageValidator {

    /** 封面大小上限 5MB（与头像/游戏封面/工作台配图口径一致） */
    public static final long MAX_IMAGE_BYTES = ImageUploadValidator.MAX_IMAGE_BYTES;

    private RecipeImageValidator() {
    }

    /**
     * 全量校验：非空 → 大小上限 → 扩展名白名单 → 魔数识别 → 扩展名与魔数一致性。
     *
     * @throws CustomExceptions 校验不通过时抛出，消息为面向运营的中文提示
     */
    public static void validate(MultipartFile file) {
        ImageUploadValidator.validate(file, "请选择要上传的封面图片");
    }

    /** 提取小写扩展名（无扩展名返回空串） */
    public static String extractExtension(String originalFilename) {
        return ImageUploadValidator.extractExtension(originalFilename);
    }

    /** JPEG 魔数：FF D8 FF */
    public static boolean isJpeg(byte[] b) {
        return ImageUploadValidator.isJpeg(b);
    }

    /** PNG 魔数：89 50 4E 47 */
    public static boolean isPng(byte[] b) {
        return ImageUploadValidator.isPng(b);
    }

    /** WebP 魔数：第 0-3 字节为 RIFF，第 8-11 字节为 WEBP */
    public static boolean isWebp(byte[] b) {
        return ImageUploadValidator.isWebp(b);
    }
}
