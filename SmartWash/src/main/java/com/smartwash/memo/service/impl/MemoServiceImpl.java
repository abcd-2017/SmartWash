package com.smartwash.memo.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.memo.entity.Memo;
import com.smartwash.memo.from.AddMemoFrom;
import com.smartwash.memo.from.SearchMemoFrom;
import com.smartwash.memo.from.UpdateMemoFrom;
import com.smartwash.memo.mapper.MemoMapper;
import com.smartwash.memo.service.MemoService;
import com.smartwash.memo.vo.MemoVo;
import com.smartwash.memo.vo.TagStatVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 速记手账服务实现。
 * 读路径一律限定 user_id = 当前用户 且 is_delete = 0；
 * 写路径的删除/更新均为条件 UPDATE（谓词含归属与未删除），以影响行数裁决归属，
 * 不存在「先查后改」的 TOCTOU 窗口。
 */
@Slf4j
@Service
public class MemoServiceImpl extends ServiceImpl<MemoMapper, Memo> implements MemoService {

    /** 正文长度上限（与 memo.content 列宽、Form 校验一致） */
    private static final int CONTENT_MAX_LENGTH = 2000;
    /** 标签列宽上限（逗号分隔落库后的总长） */
    private static final int TAGS_COLUMN_MAX_LENGTH = 255;
    /** 单个标签长度上限（防单标签吃满整列） */
    private static final int SINGLE_TAG_MAX_LENGTH = 50;
    /** 逻辑删除值：1=已删除，0=正常 */
    private static final int DELETED = 1;
    private static final int NOT_DELETED = 0;
    /** 标签存储分隔符 */
    private static final String TAG_SEPARATOR = ",";

    // ==================== 新增 ====================

    @Override
    public Long create(AddMemoFrom from, Long userId) {
        String content = normalizeContent(from.getContent());
        String tags = normalizeTags(from.getTags());
        Memo memo = new Memo()
                .setUserId(userId)
                .setContent(content)
                .setTags(tags)
                .setPinned(NOT_DELETED)
                .setIsDelete(NOT_DELETED);
        this.save(memo);
        log.info("速记已新增, userId: {}, memoId: {}", userId, memo.getId());
        return memo.getId();
    }

    // ==================== 分页查询 ====================

    @Override
    public Page<MemoVo> list(SearchMemoFrom from, Long userId) {
        LambdaQueryWrapper<Memo> wrapper = new LambdaQueryWrapper<Memo>()
                .eq(Memo::getUserId, userId)
                .eq(Memo::getIsDelete, NOT_DELETED)
                // 关键词对 content 模糊匹配
                .like(StrUtil.isNotBlank(from.getKeyword()), Memo::getContent, StrUtil.trim(from.getKeyword()))
                // 标签精确过滤：FIND_IN_SET 匹配逗号分隔列表中的完整标签，{0} 预编译占位
                .apply(StrUtil.isNotBlank(from.getTag()), "FIND_IN_SET({0}, tags) > 0", StrUtil.trim(from.getTag()))
                // 置顶优先，其余按更新时间倒序
                .orderByDesc(Memo::getPinned)
                .orderByDesc(Memo::getUpdatedAt);
        Page<Memo> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        List<MemoVo> records = page.getRecords().stream().map(this::buildVo).collect(Collectors.toList());
        Page<MemoVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    // ==================== 更新（patch 语义 + 条件 UPDATE） ====================

    @Override
    public String update(Long id, UpdateMemoFrom from, Long userId) {
        boolean hasContent = from.getContent() != null;
        boolean hasTags = from.getTags() != null;
        boolean hasPinned = from.getPinned() != null;
        if (!hasContent && !hasTags && !hasPinned) {
            throw new CustomExceptions("无可更新字段");
        }
        String content = hasContent ? normalizeContent(from.getContent()) : null;
        String tags = hasTags ? normalizeTags(from.getTags()) : null;
        Integer pinned = hasPinned ? (Boolean.TRUE.equals(from.getPinned()) ? 1 : 0) : null;

        LambdaUpdateWrapper<Memo> wrapper = new LambdaUpdateWrapper<Memo>()
                .eq(Memo::getId, id)
                .eq(Memo::getUserId, userId)
                .eq(Memo::getIsDelete, NOT_DELETED)
                .set(hasContent, Memo::getContent, content)
                .set(hasTags, Memo::getTags, tags)
                .set(hasPinned, Memo::getPinned, pinned);
        boolean updated = this.update(wrapper);
        if (!updated) {
            // 影响行数为 0：不存在 / 非本人 / 已删除，统一 404 语义，不区分暴露
            throw new CustomExceptions(404, "手账不存在或已删除");
        }
        log.info("速记已更新, userId: {}, memoId: {}", userId, id);
        return "更新成功";
    }

    // ==================== 逻辑删除（条件 UPDATE） ====================

    @Override
    public String delete(Long id, Long userId) {
        LambdaUpdateWrapper<Memo> wrapper = new LambdaUpdateWrapper<Memo>()
                .eq(Memo::getId, id)
                .eq(Memo::getUserId, userId)
                .eq(Memo::getIsDelete, NOT_DELETED)
                .set(Memo::getIsDelete, DELETED);
        boolean updated = this.update(wrapper);
        if (!updated) {
            throw new CustomExceptions(404, "手账不存在或已删除");
        }
        log.info("速记已逻辑删除, userId: {}, memoId: {}", userId, id);
        return "删除成功";
    }

    // ==================== 标签聚合 ====================

    @Override
    public List<TagStatVo> tagStats(Long userId) {
        List<Memo> memos = this.list(new LambdaQueryWrapper<Memo>()
                .select(Memo::getTags)
                .eq(Memo::getUserId, userId)
                .eq(Memo::getIsDelete, NOT_DELETED)
                .isNotNull(Memo::getTags));
        Map<String, Long> counts = memos.stream()
                .map(Memo::getTags)
                .filter(StrUtil::isNotBlank)
                .flatMap(tags -> Arrays.stream(tags.split(TAG_SEPARATOR)))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.groupingBy(tag -> tag, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                // count 倒序；同频按标签字典序，保证输出稳定
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new TagStatVo().setTag(entry.getKey()).setCount(entry.getValue()))
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    /** 正文归一：trim + 非空 + 长度兜底（@Valid 之外的服务端校验） */
    private String normalizeContent(String content) {
        if (StrUtil.isBlank(content)) {
            throw new CustomExceptions("内容不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > CONTENT_MAX_LENGTH) {
            throw new CustomExceptions("内容过长（上限" + CONTENT_MAX_LENGTH + "字符）");
        }
        return trimmed;
    }

    /**
     * 标签归一：trim → 去空 → 去重（保序）→ 半角逗号拒绝 → 长度校验。
     * 空集落库为 NULL，保持「无标签」语义干净。
     */
    private String normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        List<String> cleaned = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return null;
        }
        for (String tag : cleaned) {
            if (tag.contains(TAG_SEPARATOR)) {
                throw new CustomExceptions("标签不能包含逗号");
            }
            if (tag.length() > SINGLE_TAG_MAX_LENGTH) {
                throw new CustomExceptions("单个标签过长（上限" + SINGLE_TAG_MAX_LENGTH + "字符）");
            }
        }
        String joined = String.join(TAG_SEPARATOR, cleaned);
        if (joined.length() > TAGS_COLUMN_MAX_LENGTH) {
            throw new CustomExceptions("标签总长超限（上限" + TAGS_COLUMN_MAX_LENGTH + "字符）");
        }
        return joined;
    }

    private MemoVo buildVo(Memo memo) {
        List<String> tagList = StrUtil.isBlank(memo.getTags())
                ? List.of()
                : Arrays.stream(memo.getTags().split(TAG_SEPARATOR))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toList());
        return new MemoVo()
                .setId(memo.getId())
                .setContent(memo.getContent())
                .setTags(tagList)
                .setPinned(Objects.equals(memo.getPinned(), 1))
                .setCreatedAt(memo.getCreatedAt())
                .setUpdatedAt(memo.getUpdatedAt());
    }
}
