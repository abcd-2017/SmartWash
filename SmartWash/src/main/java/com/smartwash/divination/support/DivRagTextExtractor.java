package com.smartwash.divination.support;

import com.smartwash.exception.CustomExceptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * RAG 语料文本提取（纯函数式静态工具，便于单测）。
 *
 * <p>管理端古籍上传为 multipart 表单（Web DivRagList.vue），后端不接收 JSON content 字段，
 * 文本从文件本身读取。支持 TXT/MD（UTF-8）与 DOCX（复用既有 POI 依赖按段落抽取）；
 * PDF 无可用解析器且二进制直存会产生乱码语料，显式拒绝并提示转换格式，避免脏数据入库。</p>
 *
 * <p>大小上限 10MB（低于 spring.servlet.multipart 的 20MB 全局上限，业务层强制兜底）。</p>
 */
public final class DivRagTextExtractor {

    /** 文件大小上限 10MB */
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private static final String EXT_TXT = "txt";
    private static final String EXT_MD = "md";
    private static final String EXT_DOCX = "docx";

    private DivRagTextExtractor() {
    }

    /**
     * 提取文件文本内容（UTF-8）。
     *
     * @throws CustomExceptions 文件为空/超限/格式不支持/解析失败时抛出
     */
    public static String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomExceptions("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new CustomExceptions(413, "文件不能超过10MB");
        }
        String extension = extractExtension(file.getOriginalFilename());
        switch (extension) {
            case EXT_TXT:
            case EXT_MD:
                return readUtf8(file);
            case EXT_DOCX:
                return extractDocx(file);
            default:
                // PDF 不在白名单：无解析器时若直存字节会污染切片与检索，宁可明确失败
                throw new CustomExceptions("仅支持 TXT/MD/DOCX 格式文件（PDF 请转换后上传）");
        }
    }

    /** 按 UTF-8 读取文本并去除可能的 BOM 头 */
    private static String readUtf8(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                    && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).trim();
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new CustomExceptions("文件读取失败，请重试");
        }
    }

    /** DOCX 按段落抽取文本（复用 poi-ooxml；表格文本不抽取，保持实现最小） */
    private static String extractDocx(MultipartFile file) {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            StringBuilder content = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    if (content.length() > 0) {
                        content.append('\n');
                    }
                    content.append(text.trim());
                }
            });
            String result = content.toString().trim();
            if (result.isEmpty()) {
                throw new CustomExceptions("DOCX 文档内容为空");
            }
            return result;
        } catch (CustomExceptions e) {
            throw e;
        } catch (IOException e) {
            throw new CustomExceptions("DOCX 解析失败，请确认文件未损坏");
        }
    }

    /** 提取小写扩展名（无扩展名返回空串） */
    private static String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.lastIndexOf('.') < 0) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}