package com.smartwash.dining.support;

import com.smartwash.exception.CustomExceptions;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 菜谱封面上传校验（纯函数式静态工具，便于单测）。
 * 对齐既有惯例（WebUsersController 头像上传，评审报告后端 #23）：仅信文件真实内容，
 * 读文件头 12 字节判断魔数（JPEG: FF D8 FF / PNG: 89 50 4E 47 / WebP: RIFF....WEBP），
 * 并要求扩展名与魔数识别出的类型一致——contentType 可被伪造，不作为依据；
 * 大小上限 5MB 代码层兜底（与 spring.servlet.multipart 双保险）。
 * 校验不通过抛 {@link CustomExceptions}（管理端信封语义：413 走超限码，其余走默认失败码）。
 */
public final class RecipeImageValidator {

    /** 封面大小上限 5MB（与头像/游戏封面/工作台配图口径一致） */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    /** 扩展名白名单（统一小写比对） */
    private static final String EXT_JPG = "jpg";
    private static final String EXT_JPEG = "jpeg";
    private static final String EXT_PNG = "png";
    private static final String EXT_WEBP = "webp";

    private RecipeImageValidator() {
    }

    /**
     * 全量校验：非空 → 大小上限 → 扩展名白名单 → 魔数识别 → 扩展名与魔数一致性。
     *
     * @throws CustomExceptions 校验不通过时抛出，消息为面向运营的中文提示
     */
    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomExceptions("请选择要上传的封面图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new CustomExceptions(413, "图片大小不能超过5MB");
        }
        String extension = extractExtension(file.getOriginalFilename());
        boolean extJpeg = EXT_JPG.equals(extension) || EXT_JPEG.equals(extension);
        if (!extJpeg && !EXT_PNG.equals(extension) && !EXT_WEBP.equals(extension)) {
            throw new CustomExceptions("仅支持 JPG/PNG/WebP 格式图片");
        }

        byte[] header = readHeader(file);
        if (isJpeg(header)) {
            if (!extJpeg) {
                throw new CustomExceptions("图片内容与扩展名不符");
            }
            return;
        }
        if (isPng(header)) {
            if (!EXT_PNG.equals(extension)) {
                throw new CustomExceptions("图片内容与扩展名不符");
            }
            return;
        }
        if (isWebp(header)) {
            if (!EXT_WEBP.equals(extension)) {
                throw new CustomExceptions("图片内容与扩展名不符");
            }
            return;
        }
        throw new CustomExceptions("仅支持 JPG/PNG/WebP 格式图片");
    }

    /** 提取小写扩展名（无扩展名返回空串） */
    public static String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.lastIndexOf('.') < 0) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** 读文件头 12 字节；不足 12 字节按内容不合法处理（全 0 填充后魔数必然不命中） */
    private static byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            is.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new CustomExceptions("图片读取失败，请重试");
        }
        return header;
    }

    /** JPEG 魔数：FF D8 FF */
    public static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    /** PNG 魔数：89 50 4E 47 */
    public static boolean isPng(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    /** WebP 魔数：第 0-3 字节为 RIFF，第 8-11 字节为 WEBP */
    public static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
