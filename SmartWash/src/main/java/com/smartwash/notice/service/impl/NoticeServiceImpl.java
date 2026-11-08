package com.smartwash.notice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.notice.constant.NoticeType;
import com.smartwash.notice.entity.Notice;
import com.smartwash.notice.entity.NoticeRead;
import com.smartwash.notice.from.AddNoticeFrom;
import com.smartwash.notice.from.SearchNoticeFrom;
import com.smartwash.notice.mapper.NoticeMapper;
import com.smartwash.notice.mapper.NoticeReadMapper;
import com.smartwash.notice.service.NoticeService;
import com.smartwash.notice.vo.NoticeReadStatVo;
import com.smartwash.notice.vo.NoticeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内信服务实现（v1：运营通知 + 用户收件箱）。
 *
 * <p>已读口径（懒创建，不做全量 fan-out）：
 * notice_read 仅在用户首次标记时插入一条，未读 = notice 不在 read 表；
 * 未读数 = COUNT(notice) − COUNT(notice_read WHERE user_id)，两个 COUNT 相减；
 * uk_user_notice(user_id, notice_id) 唯一索引是幂等 DB 闸门，
 * 标记已读/全部已读的重复插入一律捕获 DuplicateKeyException 判幂等成功。</p>
 *
 * <p>v2 演进路径（本设计不做）：若未读数成为热点（用户量大 / 轮询频繁），
 * 改为 fan-out——发送时给全量用户写 notice_read 占位行（或 Redis 未读数缓存），
 * 未读数即单用户 COUNT，避免每次双 COUNT；表结构已留扩展位（type/deep_link）。</p>
 *
 * <p>归属：所有读路径强制 user_id = 当前登录用户；标记已读先校验 notice 存在，
 * 再按 (user_id, notice_id) 查重，越权/不存在一律按「不存在」处理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {

    /** 已读率保留小数位 */
    private static final int READ_RATE_SCALE = 1;
    /** 全部已读单次扫描上限（页大小）：与 BaseSearchFrom 分页上限对齐，防止全表扫描打满内存 */
    private static final long READ_ALL_SCAN_LIMIT = 100L;

    private final NoticeReadMapper noticeReadMapper;

    // ==================== 管理端 ====================

    @Override
    public Long publish(AddNoticeFrom from, Long creatorAdminId) {
        // @Valid 之外的服务端兜底：防绕过校验器写入非法类型
        if (!NoticeType.isValid(from.getType())) {
            throw new CustomExceptions("类型仅支持 SYSTEM/ANNOUNCEMENT");
        }
        Notice notice = new Notice()
                .setTitle(from.getTitle().trim())
                .setContent(from.getContent())
                .setType(from.getType())
                // 空白深链归一为 NULL，保持「可空」语义干净（纯文案公告无跳转）
                .setDeepLink(StrUtil.trimToNull(from.getDeepLink()))
                .setCreatorAdminId(creatorAdminId);
        this.save(notice);
        // 日志不落内容全文（内容可能含敏感信息），只记标题与类型
        log.info("站内信已发送, id: {}, type: {}, title: {}, creatorAdminId: {}",
                notice.getId(), notice.getType(), notice.getTitle(), creatorAdminId);
        return notice.getId();
    }

    @Override
    public Page<NoticeVo> adminPage(SearchNoticeFrom from) {
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(from.getTitle()), Notice::getTitle, from.getTitle());
        wrapper.eq(StrUtil.isNotBlank(from.getType()), Notice::getType, from.getType());
        // 管理台列表按创建时间倒序（最新发送在前），与用户端收件箱同序
        wrapper.orderByDesc(Notice::getCreatedAt).orderByDesc(Notice::getId);

        Page<Notice> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        return convertPage(page);
    }

    @Override
    public NoticeReadStatVo readStat(Long noticeId) {
        Notice notice = this.getById(noticeId);
        if (notice == null) {
            // 公告不存在时统计口径无意义，按「不存在」处理（404 语义，不暴露额外细节）
            throw new CustomExceptions(404, "公告不存在");
        }
        long totalUsers = noticeReadMapper.countUsers();
        long readCount = noticeReadMapper.countReadByNotice(noticeId);
        return new NoticeReadStatVo()
                .setTotalUsers(totalUsers)
                .setReadCount(readCount)
                .setReadRateText(formatReadRate(readCount, totalUsers));
    }

    // ==================== 用户端 ====================

    @Override
    public Page<NoticeVo> pageInbox(SearchNoticeFrom from, Long userId) {
        // 全量公告分页（不按用户过滤 notice 本身——公告对所有人可见，已读标记才按人区分）
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Notice::getCreatedAt).orderByDesc(Notice::getId);
        Page<Notice> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Page<NoticeVo> voPage = convertPage(page);
        if (voPage.getRecords().isEmpty()) {
            return voPage;
        }
        // 批量查当前用户已读集合（LEFT JOIN 语义的等价实现：一次查询回填整页，避免逐条查库）
        Set<Long> readNoticeIds = listReadNoticeIds(userId, voPage.getRecords().stream()
                .map(NoticeVo::getId).collect(Collectors.toList()));
        voPage.getRecords().forEach(vo -> vo.setIsRead(readNoticeIds.contains(vo.getId())));
        return voPage;
    }

    @Override
    public long countUnread(Long userId) {
        long total = this.count();
        long read = noticeReadMapper.selectCount(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getUserId, userId));
        // 已读数不会超过总数（唯一索引保证），防御性兜底防脏数据出负数
        return Math.max(0L, total - read);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long noticeId, Long userId) {
        Notice notice = this.getById(noticeId);
        if (notice == null) {
            throw new CustomExceptions("公告不存在");
        }
        // 幂等快路径：本人已读记录已存在 → 静默成功（覆盖重复点击与并发之一）
        NoticeRead existed = noticeReadMapper.selectOne(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getUserId, userId)
                .eq(NoticeRead::getNoticeId, noticeId));
        if (existed != null) {
            log.debug("站内信重复标记已读（幂等命中）, noticeId: {}, userId: {}", noticeId, userId);
            return;
        }
        try {
            noticeReadMapper.insert(new NoticeRead()
                    .setUserId(userId)
                    .setNoticeId(noticeId)
                    .setReadAt(LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            // 并发窗口：另一请求已插入同 (user_id, notice_id)，判幂等成功，不抛异常
            log.info("站内信并发标记已读幂等命中（唯一索引兜底）, noticeId: {}, userId: {}", noticeId, userId);
            return;
        }
        log.debug("站内信已读, noticeId: {}, userId: {}", noticeId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int readAll(Long userId) {
        // 全量公告（不分页，一次拉取上限内扫描；公告量级小，v1 不做游标批处理）
        Page<Notice> page = this.page(new Page<>(1, READ_ALL_SCAN_LIMIT),
                new LambdaQueryWrapper<Notice>().orderByDesc(Notice::getCreatedAt));
        if (page.getRecords().isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (Notice notice : page.getRecords()) {
            try {
                noticeReadMapper.insert(new NoticeRead()
                        .setUserId(userId)
                        .setNoticeId(notice.getId())
                        .setReadAt(LocalDateTime.now()));
                inserted++;
            } catch (DuplicateKeyException e) {
                // 存量已读 / 并发标记：吞重复键，不中断整批（幂等语义）
                log.debug("全部已读跳过已存在记录, noticeId: {}, userId: {}", notice.getId(), userId);
            }
        }
        log.info("站内信全部已读完成, userId: {}, 本次新增: {}", userId, inserted);
        return inserted;
    }

    // ==================== 内部方法 ====================

    /** 批量查当前用户已读的 noticeId 集合（用于分页 is_read 回填，一次查询替代逐条） */
    private Set<Long> listReadNoticeIds(Long userId, List<Long> noticeIds) {
        if (noticeIds.isEmpty()) {
            return Collections.emptySet();
        }
        return noticeReadMapper.selectList(new LambdaQueryWrapper<NoticeRead>()
                        .eq(NoticeRead::getUserId, userId)
                        .in(NoticeRead::getNoticeId, noticeIds))
                .stream()
                .map(NoticeRead::getNoticeId)
                .collect(Collectors.toSet());
    }

    private Page<NoticeVo> convertPage(Page<Notice> page) {
        Page<NoticeVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }
        // 管理端列表不下发 isRead（无当前用户语义）；用户端由 pageInbox 回填
        voPage.setRecords(page.getRecords().stream().map(this::buildVo).collect(Collectors.toList()));
        return voPage;
    }

    private NoticeVo buildVo(Notice notice) {
        NoticeVo vo = new NoticeVo();
        BeanUtils.copyProperties(notice, vo);
        return vo;
    }

    /** 已读率文案：分母为 0（无用户）时返回 "0.0%"，避免除零 */
    private String formatReadRate(long readCount, long totalUsers) {
        if (totalUsers <= 0) {
            return "0.0%";
        }
        BigDecimal rate = BigDecimal.valueOf(readCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalUsers), READ_RATE_SCALE, RoundingMode.HALF_UP);
        return rate.toPlainString() + "%";
    }
}
