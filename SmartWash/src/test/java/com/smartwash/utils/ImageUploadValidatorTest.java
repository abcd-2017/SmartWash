package com.smartwash.utils;

import com.smartwash.exception.CustomExceptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用图片上传校验器测试（纯静态工具，无需 Mockito 容器）。
 * 覆盖外部行为契约：非空校验（默认/定制文案）、5MB 超限 413、扩展名白名单、魔数识别、
 * 扩展名与魔数一致性（改后缀攻击）、validateContent 返回形态（null=通过，头像 failMsg 风格复用）。
 * 菜谱封面/头像/工作台配图/游戏封面四条链路共用本校验器，此处即为统一口径的回归防线。
 */
@DisplayName("ImageUploadValidator 通用图片上传校验")
class ImageUploadValidatorTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] WEBP_BYTES = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0,
            'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final byte[] HTML_BYTES = {'<', 'h', 't', 'm', 'l', '>', '<', '/', 'h', 't', 'm', 'l', '>'};

    private MockMultipartFile multipart(byte[] content, String filename) {
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }

    @Test
    @DisplayName("全量校验：空文件拒绝（默认文案与封面语境定制文案）")
    void validateRejectsEmptyFile() {
        CustomExceptions defaultTip = assertThrows(CustomExceptions.class,
                () -> ImageUploadValidator.validate(new MockMultipartFile("file", "a.png", "image/png", new byte[0])));
        assertEquals("请选择要上传的图片", defaultTip.getMessage());

        CustomExceptions customTip = assertThrows(CustomExceptions.class,
                () -> ImageUploadValidator.validate(new MockMultipartFile("file", "a.png", "image/png", new byte[0]),
                        "请选择要上传的封面图片"));
        assertEquals("请选择要上传的封面图片", customTip.getMessage());

        CustomExceptions nullFile = assertThrows(CustomExceptions.class,
                () -> ImageUploadValidator.validate(null));
        assertEquals("请选择要上传的图片", nullFile.getMessage());
    }

    @Test
    @DisplayName("全量校验：超过 5MB 拒绝（413 业务码）")
    void validateRejectsOversizedFile() {
        byte[] big = new byte[(int) (ImageUploadValidator.MAX_IMAGE_BYTES + 1)];
        System.arraycopy(PNG_BYTES, 0, big, 0, PNG_BYTES.length);
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> ImageUploadValidator.validate(new MockMultipartFile("file", "a.png", "image/png", big)));
        assertEquals(413, ex.getCode());
        assertEquals("图片大小不能超过5MB", ex.getMessage());
    }

    @Test
    @DisplayName("全量校验：恰好 5MB 的合法图片放行（闸门为开区间 ≤5MB）")
    void validateAcceptsFileAtLimit() {
        byte[] atLimit = new byte[(int) ImageUploadValidator.MAX_IMAGE_BYTES];
        System.arraycopy(PNG_BYTES, 0, atLimit, 0, PNG_BYTES.length);
        // 不抛异常即通过（validate 返回 void，合法路径无副作用）
        ImageUploadValidator.validate(new MockMultipartFile("file", "a.png", "image/png", atLimit));
    }

    @Test
    @DisplayName("全量校验：扩展名白名单外拒绝（gif/html/无扩展名）")
    void validateRejectsDisallowedExtension() {
        for (String filename : new String[]{"cover.gif", "cover.html", "cover"}) {
            CustomExceptions ex = assertThrows(CustomExceptions.class,
                    () -> ImageUploadValidator.validate(multipart(PNG_BYTES, filename)));
            assertEquals("仅支持 JPG/PNG/WebP 格式图片", ex.getMessage());
        }
    }

    @Test
    @DisplayName("内容校验：合法图片（jpg/jpeg/png/webp 扩展名）返回 null")
    void validateContentAcceptsLegalImages() {
        assertNull(ImageUploadValidator.validateContent(multipart(JPEG_BYTES, "a.jpg")));
        assertNull(ImageUploadValidator.validateContent(multipart(JPEG_BYTES, "a.jpeg")));
        assertNull(ImageUploadValidator.validateContent(multipart(PNG_BYTES, "a.PNG")));
        assertNull(ImageUploadValidator.validateContent(multipart(WEBP_BYTES, "a.webp")));
    }

    @Test
    @DisplayName("内容校验：魔数与扩展名不符拒绝（png 扩展名夹带 JPEG 内容等改后缀攻击）")
    void validateContentRejectsMismatchedMagic() {
        assertEquals("图片内容与扩展名不符", ImageUploadValidator.validateContent(multipart(JPEG_BYTES, "fake.png")));
        assertEquals("图片内容与扩展名不符", ImageUploadValidator.validateContent(multipart(PNG_BYTES, "fake.jpg")));
        assertEquals("图片内容与扩展名不符", ImageUploadValidator.validateContent(multipart(WEBP_BYTES, "fake.png")));
    }

    @Test
    @DisplayName("内容校验：伪装魔数的非图片内容拒绝（HTML 夹带 png 扩展名）")
    void validateContentRejectsDisguisedContent() {
        assertEquals("仅支持 JPG/PNG/WebP 格式图片",
                ImageUploadValidator.validateContent(multipart(HTML_BYTES, "evil.png")));
    }

    @Test
    @DisplayName("魔数判定：JPEG/PNG/WebP 各自命中且互不误判")
    void magicNumberChecks() {
        assertTrue(ImageUploadValidator.isJpeg(JPEG_BYTES));
        assertTrue(ImageUploadValidator.isPng(PNG_BYTES));
        assertTrue(ImageUploadValidator.isWebp(WEBP_BYTES));
        assertFalse(ImageUploadValidator.isPng(JPEG_BYTES));
        assertFalse(ImageUploadValidator.isWebp(PNG_BYTES));
        assertFalse(ImageUploadValidator.isJpeg(WEBP_BYTES));
    }

    @Test
    @DisplayName("扩展名提取：统一小写、无扩展名返回空串")
    void extractExtensionNormalizesCase() {
        assertEquals("png", ImageUploadValidator.extractExtension("Cover.PNG"));
        assertEquals("jpg", ImageUploadValidator.extractExtension("a.b.jpg"));
        assertEquals("", ImageUploadValidator.extractExtension("noext"));
        assertEquals("", ImageUploadValidator.extractExtension(null));
    }
}
