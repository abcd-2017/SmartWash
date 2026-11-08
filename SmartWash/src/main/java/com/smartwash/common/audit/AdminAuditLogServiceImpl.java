package com.smartwash.common.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.audit.mapper.AdminAuditLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 管理端审计日志服务实现。
 *
 * <p><b>并发安全取舍</b>：HMAC 链要求「读 prev_hash → 计算 hash → 落库」严格串行，
 * 并发写同一条链会断裂。这里用<b>单线程执行器</b>（1 worker + 有界队列 10000 + 丢弃策略）：
 * 队列天然把同链写串行化，且业务线程只做入队、绝不被审计拖慢；队列打满时丢弃新记录并 warn
 * （宁丢审计不阻业务）。单实例内部安全；<b>多实例部署时各节点自成链、跨节点会断链</b>
 * （与 OrderTimeoutManager 同类的单机限制），集群化需改 DB 行锁/分布式锁取 prev_hash。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl extends ServiceImpl<AdminAuditLogMapper, AdminAuditLog>
        implements IAdminAuditLogService {

    /** 链校验默认抽查条数 */
    private static final int DEFAULT_VERIFY_LIMIT = 500;

    /** 链校验单次上限（防一次拉全表） */
    private static final int MAX_VERIFY_LIMIT = 5000;

    /** 审计写队列上限：打满丢弃并 warn */
    private static final int CHAIN_QUEUE_CAPACITY = 10_000;

    private final AuditHashService auditHashService;

    /**
     * 单线程链写执行器：串行化「读 prev_hash → 算 hash → 落库」；守护线程不阻塞 JVM 退出。
     * 拒绝策略 = 丢弃 + warn（审计失败绝不阻塞业务）。
     */
    private final ExecutorService chainExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(CHAIN_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "audit-log-chain-writer");
                thread.setDaemon(true);
                return thread;
            },
            (runnable, executor) -> log.warn("审计日志写队列已满（{}），丢弃本次审计记录（不影响业务）", CHAIN_QUEUE_CAPACITY));

    @Override
    public void recordAsync(AdminAuditLog entry) {
        chainExecutor.execute(() -> {
            try {
                String prevHash = lastChainHash();
                entry.setPrevHash(prevHash);
                entry.setHash(auditHashService.computeEntryHash(entry, prevHash));
                save(entry);
            } catch (Exception e) {
                // 审计链路任何异常只 warn，绝不反向影响业务（业务线程早已返回）
                log.warn("审计日志落库失败, action={}, target={}:{}, adminUser={}",
                        entry.getAction(), entry.getTargetType(), entry.getTargetId(), entry.getAdminUser(), e);
            }
        });
    }

    @Override
    public Page<AuditLogVo> pageAuditLogs(SearchAuditLogFrom from) {
        Page<AdminAuditLog> page = page(new Page<>(from.getPage(), from.getSize()),
                new LambdaQueryWrapper<AdminAuditLog>()
                        .eq(StringUtils.hasText(from.getAdminUser()), AdminAuditLog::getAdminUser, from.getAdminUser())
                        .eq(StringUtils.hasText(from.getAction()), AdminAuditLog::getAction, from.getAction())
                        .eq(StringUtils.hasText(from.getTargetType()), AdminAuditLog::getTargetType, from.getTargetType())
                        .eq(StringUtils.hasText(from.getTargetId()), AdminAuditLog::getTargetId, from.getTargetId())
                        .ge(from.getStartTime() != null, AdminAuditLog::getCreatedAt, from.getStartTime())
                        .le(from.getEndTime() != null, AdminAuditLog::getCreatedAt, from.getEndTime())
                        .orderByDesc(AdminAuditLog::getId));
        Page<AuditLogVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVo).toList());
        return voPage;
    }

    @Override
    public AuditVerifyVo verifyChain(Integer limit) {
        int window = (limit == null || limit <= 0) ? DEFAULT_VERIFY_LIMIT : Math.min(limit, MAX_VERIFY_LIMIT);
        // 取最近 N 条（id 倒序）后反转为升序再交给链校验
        List<AdminAuditLog> rows = list(new LambdaQueryWrapper<AdminAuditLog>()
                .orderByDesc(AdminAuditLog::getId)
                .last("LIMIT " + window));
        Collections.reverse(rows);
        return auditHashService.verifyChain(rows);
    }

    /** 链尾 hash：无记录（或历史 hash 为空）时取 genesis */
    private String lastChainHash() {
        AdminAuditLog last = getBaseMapper().selectOne(new LambdaQueryWrapper<AdminAuditLog>()
                .select(AdminAuditLog::getId, AdminAuditLog::getHash)
                .orderByDesc(AdminAuditLog::getId)
                .last("LIMIT 1"));
        return (last == null || !StringUtils.hasText(last.getHash()))
                ? AuditHashService.GENESIS_HASH
                : last.getHash();
    }

    private AuditLogVo toVo(AdminAuditLog entity) {
        AuditLogVo vo = new AuditLogVo();
        vo.setId(entity.getId());
        vo.setAdminUser(entity.getAdminUser());
        vo.setAction(entity.getAction());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setBeforeJson(entity.getBeforeJson());
        vo.setAfterJson(entity.getAfterJson());
        vo.setIp(entity.getIp());
        vo.setPrevHash(entity.getPrevHash());
        vo.setHash(entity.getHash());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    @PreDestroy
    void shutdownChainExecutor() {
        // 平滑关闭：处理完队列内剩余审计再退出（最多等 5 秒，避免拖慢停机）
        chainExecutor.shutdown();
        try {
            if (!chainExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                chainExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            chainExecutor.shutdownNow();
        }
    }
}
