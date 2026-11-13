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
 * 站内信服务实现（v1：运营通知 + 用户收件箱；v2：业务事件定向通知）。
 *
 * <p>已读口径（懒创建，不做全量 fan-out）：
 * notice_read 仅在用户首次标记时插入一条，未读 = 可见 notice 不在 read 表；
 * 可见集合 = 广播（user_id IS NULL）或定向给我（user_id = 当前用户）；
 * 未读数 = COUNT(可见 notice) − COUNT(notice_read WHERE user_id)，两个 COUNT 相减；
 * uk_user_notice(user_id, notice_id) 唯一索引是幂等 DB 闸门，
 * 标记已读/全部已读的重复插入一律捕获 DuplicateKeyException 判幂等成功。</p>
 *
 * <p>定向发送实现取舍（多行 vs 关联表）：选择「按接收人一人一行」（notice.user_id 填接收人），
 * 不新增接收人关联表。理由：
 * ① 定向场景是点单订单参与者（几人到几十人），存储冗余可忽略，无需为省空间引入 JOIN/EXISTS；
 * ② 未读数与已读幂等沿用既有「懒创建双 COUNT 相减 + uk_user_notice」口径，零结构改动，
 *    关联表方案会破坏该口径（可见性判断变成 EXISTS 子查询）并需迁移全部读路径，侵入过大；
 * ③ 已知局限：同内容 N 行冗余；定向消息无发送级幂等闸门，防重由调用方（业务事件消费侧）负责。</p>
 *
 * <p>v2 演进路径（本设计不做）：若未读数成为热点（用户量大 / 轮询频繁），
 * 改为 fan-out——发送时给全量用户写 notice_read 占位行（或 Redis 未读数缓存），
 * 未读数即单用户 COUNT，避免每次双 COUNT；表结构已留扩展位（type/deep_link/user_id）。</p>
 *
 * <p>归属：所有读路径按「广播或定向给我」过滤；标记已读先校验 notice 存在且对本人可见，
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
        // @Valid 之外的服务端兜底：管理端仅开放广播类型（SYSTEM/ANNOUNCEMENT），
        // DINING_* 业务事件类型只走 publishTargeted 定向入口
        if (!NoticeType.isValidBroadcast(from.getType())) {
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

    // ==================== 定向发送（业务事件入箱） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishTargeted(NoticeType type, String title, String content, String deepLink, List<Long> userIds) {
        // 入参防御：类型经枚举编译期白名单；空接收人/空白必填项是调用方编程错误，fail-fast 而非静默 no-op
        // （静默成功会让业务事件侧误判「已送达」，留下无人收到的通知）
        if (type == null || StrUtil.isBlank(title) || StrUtil.isBlank(content)
                || userIds == null || userIds.isEmpty()) {
            throw new CustomExceptions("定向发送参数不完整（类型/标题/内容/接收人均必填，接收人列表不可为空）");
        }
        // 保序去重：同一接收人重复传入视为调用方失误，一人只发一条
        List<Long> distinctUserIds = userIds.stream().distinct().toList();
        // 深链空白归一为 NULL，与管理端 publish 的归一口径一致
        String normalizedLink = StrUtil.trimToNull(deepLink);
        for (Long userId : distinctUserIds) {
            this.save(new Notice()
                    .setTitle(title.trim())
                    .setContent(content)
                    .setType(type.getType())
                    .setDeepLink(normalizedLink)
                    .setUserId(userId));
        }
        // 多行同事务：任一行失败整体回滚，事件侧可整体重试
        log.info("站内信定向发送完成, type: {}, title: {}, 接收人数: {}", type.getType(), title.trim(), distinctUserIds.size());
        return distinctUserIds.size();
    }

    // ==================== 用户端 ====================

    @Override
    public Page<NoticeVo> pageInbox(SearchNoticeFrom from, Long userId) {
        // 收件箱口径：只看「广播或定向给我」的公告（定向给别人的不可见），已读标记按人区分
        LambdaQueryWrapper<Notice> wrapper = visibleToUserWrapper(userId);
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
        // 可见总数 = 广播（user_id IS NULL）或定向给我（user_id = 本人）；定向给别人的不计入我的未读
        long visible = this.count(visibleToUserWrapper(userId));
        long read = noticeReadMapper.selectCount(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getUserId, userId));
        // 已读数不会超过可见总数（唯一索引保证），防御性兜底防脏数据出负数
        return Math.max(0L, visible - read);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long noticeId, Long userId) {
        Notice notice = this.getById(noticeId);
        // 可见性校验：广播（user_id=NULL）对所有人可见；定向给别人的消息对本人按「不存在」处理
        // （与越权同语义，不暴露消息存在性），防止给不可见消息留下已读记录污染未读数口径
        if (notice == null || (notice.getUserId() != null && !notice.getUserId().equals(userId))) {
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
        // 全部已读只覆盖「广播或定向给我」的可见公告（不分页，一次拉取上限内扫描；公告量级小，v1 不做游标批处理）
        LambdaQueryWrapper<Notice> wrapper = visibleToUserWrapper(userId);
        wrapper.orderByDesc(Notice::getCreatedAt);
        Page<Notice> page = this.page(new Page<>(1, READ_ALL_SCAN_LIMIT), wrapper);
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

    /**
     * 「广播或定向给我」可见性条件（未读数 / 收件箱 / 全部已读共用同一口径）：
     * user_id IS NULL OR user_id = 本人。广播行 user_id 恒为 NULL，存量广播数据行为不变。
     */
    private LambdaQueryWrapper<Notice> visibleToUserWrapper(Long userId) {
        return new LambdaQueryWrapper<Notice>()
                .and(w -> w.isNull(Notice::getUserId).or().eq(Notice::getUserId, userId));
    }

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
