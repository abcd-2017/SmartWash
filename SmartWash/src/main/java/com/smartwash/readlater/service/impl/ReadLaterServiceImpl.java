package com.smartwash.readlater.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.divination.llm.ClientGoneException;
import com.smartwash.divination.llm.LlmCallException;
import com.smartwash.divination.llm.LlmChatResult;
import com.smartwash.divination.llm.LlmGateway;
import com.smartwash.divination.llm.OpenAiCompatClient;
import com.smartwash.divination.llm.OpenAiCompatProvider;
import com.smartwash.divination.service.DivModelConfigService;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.readlater.entity.ReadLater;
import com.smartwash.readlater.from.AddReadLaterFrom;
import com.smartwash.readlater.from.SearchReadLaterFrom;
import com.smartwash.readlater.from.UpdateReadLaterFrom;
import com.smartwash.readlater.mapper.ReadLaterMapper;
import com.smartwash.readlater.service.ReadLaterService;
import com.smartwash.readlater.toolkit.PageTitleFetcher;
import com.smartwash.readlater.vo.ReadLaterVo;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.vo.ShortCodeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 稍后读服务实现。
 *
 * 边界约定：
 *   - AI 摘要只走平台默认模型（divination 域 LlmGateway / DivModelConfigService，只调用不改其逻辑），
 *     禁止使用用户 BYOK（那是观象台域配置）；
 *   - 页面抓取前过 {@link com.smartwash.divination.support.DivUrlSafetyValidator}（SSRF fail-closed），
 *     抓取失败以 url 兜底标题，不阻塞入库；
 *   - 修改/删除一律条件 UPDATE（id + user_id + is_delete=0 谓词），判影响行数后再定成败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadLaterServiceImpl extends ServiceImpl<ReadLaterMapper, ReadLater> implements ReadLaterService {

    /** url 入库前缀校验（完整 SSRF 校验在抓取侧执行） */
    private static final String HTTPS_PREFIX = "https://";

    /** 标签存储规则：单标签上限（与速记手账域同口径） */
    private static final int TAG_MAX_LENGTH = 50;
    /** 标签存储规则：拼接后总长上限 = tags 列宽 */
    private static final int TAGS_TOTAL_MAX_LENGTH = 255;

    /** 摘要系统提示词：≤150 字提炼核心要点，只输出正文 */
    private static final String DIGEST_SYSTEM_PROMPT =
            "你是文章摘要助手。请根据给定的文章标题与链接信息，用不超过150字提炼文章核心要点。"
                    + "只输出摘要正文，不要任何前言、总结性套话或分点符号。";

    private final PageTitleFetcher pageTitleFetcher;
    private final LlmGateway llmGateway;
    private final OpenAiCompatClient llmClient;
    private final DivModelConfigService modelConfigService;
    private final ToolboxShortCodeService toolboxShortCodeService;

    // ==================== 新增（抓标题 → 入库） ====================

    @Override
    public Long add(AddReadLaterFrom from, Long userId) {
        String url = from.getUrl() == null ? "" : from.getUrl().trim();
        if (!url.toLowerCase().startsWith(HTTPS_PREFIX)) {
            throw new CustomExceptions("仅支持 https 链接");
        }

        // 抓取标题：校验不过/网络失败一律 null，以 url 兜底（SSRF fail-closed：内网地址不发起请求）
        String title = pageTitleFetcher.fetchTitle(url);
        title = title == null ? null : title.trim();
        if (!StringUtils.hasText(title)) {
            title = url;
        }
        // 标题列宽 255，超长截断兜底（抓取方可能返回超长 title）
        if (title.length() > 255) {
            title = title.substring(0, 255);
        }

        ReadLater record = new ReadLater()
                .setUserId(userId)
                .setUrl(url)
                .setTitle(title)
                .setTags(cleanTags(from.getTags()))
                .setStatus(ReadLater.STATUS_UNREAD)
                .setIsDelete(0);
        this.save(record);
        log.info("稍后读收藏成功, id: {}, userId: {}, title长度: {}", record.getId(), userId, title.length());
        return record.getId();
    }

    // ==================== 分页（keyword/状态/标签筛选，未读优先） ====================

    @Override
    public IPage<ReadLaterVo> list(SearchReadLaterFrom from, Long userId) {
        LambdaQueryWrapper<ReadLater> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReadLater::getUserId, userId)
                .eq(ReadLater::getIsDelete, 0);
        if (from.getStatus() != null) {
            wrapper.eq(ReadLater::getStatus, from.getStatus());
        }
        if (StringUtils.hasText(from.getTag())) {
            // 精确命中逗号分隔标签中的一项（FIND_IN_SET 走预编译参数）
            wrapper.apply("FIND_IN_SET({0}, tags)", from.getTag().trim());
        }
        if (StringUtils.hasText(from.getKeyword())) {
            String keyword = from.getKeyword().trim();
            wrapper.and(w -> w.like(ReadLater::getUrl, keyword)
                    .or().like(ReadLater::getTitle, keyword)
                    .or().like(ReadLater::getDigest, keyword));
        }
        // 排序策略：未读优先，同状态按收藏时间倒序
        wrapper.orderByAsc(ReadLater::getStatus)
                .orderByDesc(ReadLater::getCreatedAt);

        IPage<ReadLater> result = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Page<ReadLaterVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<ReadLaterVo> records = new ArrayList<>();
        for (ReadLater record : result.getRecords()) {
            records.add(buildVo(record));
        }
        voPage.setRecords(records);
        return voPage;
    }

    // ==================== AI 摘要（平台默认模型） ====================

    @Override
    public String generateDigest(Long id, Long userId) {
        ReadLater record = getOwnedRecord(id, userId);

        // 只解析平台默认模型（BYOK 传 null：那是观象台域配置，稍后读不消费）
        OpenAiCompatProvider platformProvider = modelConfigService.resolvePlatformDefaultProvider();
        LlmGateway.ResolvedProvider resolved;
        try {
            // 平台默认以 Supplier 懒加载注入；平台未配置时由网关落 yml bootstrap 兜底，全失败抛 IllegalStateException
            resolved = llmGateway.resolveProvider(null, () -> platformProvider);
        } catch (IllegalStateException e) {
            log.warn("稍后读摘要生成失败: 无可用模型供应商, id: {}, userId: {}", id, userId);
            throw new CustomExceptions("平台未配置解读模型，请先在管理端配置");
        }

        String title = StringUtils.hasText(record.getTitle()) ? record.getTitle() : record.getUrl();
        String userPrompt = "文章标题：" + title + "\n文章链接：" + record.getUrl();

        LlmChatResult result;
        try {
            // 非流式消费：增量回调为空操作，取最终完整文本
            result = llmClient.chatWithFallback(resolved.getProvider(), DIGEST_SYSTEM_PROMPT, userPrompt, chunk -> { });
        } catch (LlmCallException | ClientGoneException e) {
            // 熔断统计记失败（与观象台解读链路同口径），业务侧统一失败文案不透出供应商细节
            llmGateway.recordResult(resolved.getProvider(), false);
            log.warn("稍后读摘要 LLM 调用失败, id: {}, provider: {}, 原因: {}", id, resolved.getProvider().getName(), e.getMessage());
            throw new CustomExceptions("摘要生成失败，请稍后再试");
        }
        llmGateway.recordResult(resolved.getProvider(), true);

        String digest = result.getContent() == null ? "" : result.getContent().trim();
        if (digest.isEmpty()) {
            throw new CustomExceptions("摘要生成失败，请稍后再试");
        }

        // 条件更新落库（归属 + 未删除谓词），判影响行数
        LambdaUpdateWrapper<ReadLater> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ReadLater::getId, id)
                .eq(ReadLater::getUserId, userId)
                .eq(ReadLater::getIsDelete, 0)
                .set(ReadLater::getDigest, digest);
        if (!this.update(updateWrapper)) {
            throw new CustomExceptions("记录不存在");
        }
        log.info("稍后读摘要生成成功, id: {}, userId: {}, provider: {}, 摘要长度: {}",
                id, userId, resolved.getProvider().getName(), digest.length());
        return digest;
    }

    // ==================== patch 更新 / 逻辑删除 ====================

    @Override
    public void update(Long id, UpdateReadLaterFrom from, Long userId) {
        // patch 语义：至少传一个字段
        if (from.getTags() == null && from.getStatus() == null) {
            throw new CustomExceptions("请至少传入一个待更新字段");
        }
        LambdaUpdateWrapper<ReadLater> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ReadLater::getId, id)
                .eq(ReadLater::getUserId, userId)
                .eq(ReadLater::getIsDelete, 0);
        if (from.getTags() != null) {
            // 标签整体替换；空列表清洗后为 null，即清空标签
            updateWrapper.set(ReadLater::getTags, cleanTags(from.getTags()));
        }
        if (from.getStatus() != null) {
            if (from.getStatus() != ReadLater.STATUS_UNREAD && from.getStatus() != ReadLater.STATUS_READ) {
                throw new CustomExceptions("状态参数非法");
            }
            updateWrapper.set(ReadLater::getStatus, from.getStatus());
        }
        if (!this.update(updateWrapper)) {
            throw new CustomExceptions("记录不存在");
        }
        log.debug("稍后读更新成功, id: {}, userId: {}", id, userId);
    }

    @Override
    public void delete(Long id, Long userId) {
        LambdaUpdateWrapper<ReadLater> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ReadLater::getId, id)
                .eq(ReadLater::getUserId, userId)
                .eq(ReadLater::getIsDelete, 0)
                .set(ReadLater::getIsDelete, 1);
        if (!this.update(updateWrapper)) {
            throw new CustomExceptions("记录不存在");
        }
        log.info("稍后读逻辑删除成功, id: {}, userId: {}", id, userId);
    }

    // ==================== 一键转短链 ====================

    @Override
    public String shorten(Long id, Long userId) {
        ReadLater record = getOwnedRecord(id, userId);
        AddShortCodeFrom shortCodeFrom = new AddShortCodeFrom();
        shortCodeFrom.setTarget(record.getUrl());
        // 复用短码内核创建短链（contentType=1，公开可见性默认值），完整短链地址由内核拼装
        ShortCodeVo shortCode = toolboxShortCodeService.createShortCode(shortCodeFrom, userId);
        log.info("稍后读转短链成功, id: {}, userId: {}, code: {}", id, userId, shortCode.getCode());
        return shortCode.getShortUrl();
    }

    // ==================== 内部方法 ====================

    /** 归属校验：未删除 + 本人记录，否则按不存在处理（不暴露他人生存记录） */
    private ReadLater getOwnedRecord(Long id, Long userId) {
        ReadLater record = this.getById(id);
        if (record == null || !record.getUserId().equals(userId)
                || !Integer.valueOf(0).equals(record.getIsDelete())) {
            throw new CustomExceptions("记录不存在");
        }
        return record;
    }

    /**
     * 标签清洗（与速记手账域同口径）：trim → 去空 → 去重保序；
     * 含半角逗号直接业务报错（破坏分隔存储）；单标签/总长超限报错，不静默截断；
     * 清洗后为空落库 null。
     */
    private String cleanTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains(",")) {
                throw new CustomExceptions("标签不能包含逗号");
            }
            if (trimmed.length() > TAG_MAX_LENGTH) {
                throw new CustomExceptions("单个标签过长（上限" + TAG_MAX_LENGTH + "字符）");
            }
            cleaned.add(trimmed);
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        String joined = String.join(",", cleaned);
        if (joined.length() > TAGS_TOTAL_MAX_LENGTH) {
            throw new CustomExceptions("标签总长超限（上限" + TAGS_TOTAL_MAX_LENGTH + "字符）");
        }
        return joined;
    }

    /** 实体转 VO：tags 逗号分隔拆为数组 */
    private ReadLaterVo buildVo(ReadLater record) {
        List<String> tagList = new ArrayList<>();
        if (StrUtil.isNotBlank(record.getTags())) {
            for (String tag : record.getTags().split(",")) {
                if (!tag.isBlank()) {
                    tagList.add(tag);
                }
            }
        }
        return new ReadLaterVo()
                .setId(record.getId())
                .setUrl(record.getUrl())
                .setTitle(record.getTitle())
                .setDigest(record.getDigest())
                .setTags(tagList)
                .setStatus(record.getStatus())
                .setCreatedAt(record.getCreatedAt())
                .setUpdatedAt(record.getUpdatedAt());
    }
}
