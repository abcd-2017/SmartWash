package com.smartwash.toolbox.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.entity.Lockers;
import com.smartwash.entity.Schools;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.mapper.SchoolsMapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxLockerTagService;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.toolkit.ShortCodeContentType;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
import com.smartwash.toolbox.vo.LockerTagBatchItemVo;
import com.smartwash.toolbox.vo.LockerTagVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 储物柜资产码服务实现。
 * 幂等：别名 L{lockerId} 查在前、唯一索引兜底（uk_code），重复生成复用既有活码；
 * 解析：活码存在 + 类型=2 + target 绑定与别名一致三重校验，任一不符按 404（不暴露差异原因）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolboxLockerTagServiceImpl implements ToolboxLockerTagService {

    /** 业务码：404（码/柜子不存在或绑定不符） */
    private static final int CODE_NOT_FOUND = 404;

    /** 柜码别名：L + lockerId（自定义别名规则 4-16 位字母数字内，lockerId ≤ 15 位即合法） */
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^L(\\d{1,15})$");

    /** 落地页路径前缀（target 绑定形态 {web-base-url}/l/{lockerId}） */
    private static final String LANDING_PREFIX = "/l/";

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ToolboxShortCodeMapper toolboxShortCodeMapper;
    private final LockersMapper lockersMapper;
    private final SchoolsMapper schoolsMapper;
    private final WebBaseUrlResolver webBaseUrlResolver;

    @Override
    public ShortCodeVo generateForLocker(Long lockerId) {
        Lockers locker = requireLocker(lockerId);
        String target = requireWebBase() + LANDING_PREFIX + locker.getLockerId();
        String alias = lockerAlias(locker.getLockerId());
        // 幂等：别名查在前（uk_code 兜底并发），存在且绑定一致则复用
        ToolboxShortCode existing = getByCode(alias);
        if (existing != null && ShortCodeContentType.LIVE_CODE == existing.getContentType()) {
            return toolboxShortCodeService.resolveForOwner(alias, existing.getOwnerUserId());
        }
        AddShortCodeFrom from = new AddShortCodeFrom();
        from.setContentType(ShortCodeContentType.LIVE_CODE);
        from.setTarget(target);
        from.setCustomCode(alias);
        from.setIsPublic(1);
        // owner=0 表系统生成（owner_user_id 非空约束；管理端治理场景，归属人不参与柜码语义）
        ShortCodeVo vo = toolboxShortCodeService.createShortCode(from, 0L);
        log.info("柜码生成成功, lockerId: {}, code: {}", lockerId, vo.getCode());
        return vo;
    }

    @Override
    public List<LockerTagBatchItemVo> generateForSchool(Long schoolId) {
        List<Lockers> lockers = lockersMapper.selectList(new LambdaQueryWrapper<Lockers>()
                .eq(Lockers::getSchoolId, schoolId)
                .orderByAsc(Lockers::getLockerNumber));
        if (lockers.isEmpty()) {
            throw new CustomExceptions("该校暂无柜子");
        }
        List<LockerTagBatchItemVo> items = new ArrayList<>(lockers.size());
        for (Lockers locker : lockers) {
            ToolboxShortCode existing = getByCode(lockerAlias(locker.getLockerId()));
            boolean created = existing == null;
            ShortCodeVo vo = generateForLocker(locker.getLockerId());
            items.add(new LockerTagBatchItemVo()
                    .setLockerId(locker.getLockerId())
                    .setLockerNumber(locker.getLockerNumber())
                    .setCode(vo.getCode())
                    .setShortUrl(vo.getShortUrl())
                    .setCreated(created));
        }
        log.info("按校批量生成柜码完成, schoolId: {}, 共 {} 柜", schoolId, items.size());
        return items;
    }

    @Override
    public LockerTagVo resolve(String code) {
        Long lockerId = lockerIdFromCode(code);
        ToolboxShortCode tag = getByCode(code);
        // 活码存在 + 类型=2 + target 绑定与别名一致：任一不符一律 404（不暴露差异原因）
        if (lockerId == null || tag == null || ShortCodeContentType.LIVE_CODE != tag.getContentType()
                || !String.valueOf(lockerId).equals(boundLockerIdOf(tag.getTarget()))) {
            throw new CustomExceptions(CODE_NOT_FOUND, "柜码不存在或已失效");
        }
        Lockers locker = requireLocker(lockerId);
        Schools school = schoolsMapper.selectById(locker.getSchoolId());
        return new LockerTagVo()
                .setLockerId(locker.getLockerId())
                .setLockerNumber(locker.getLockerNumber())
                .setStatus(locker.getStatus())
                .setSchoolName(school == null ? null : school.getSchoolName())
                .setSchoolLocation(school == null ? null : school.getLocation());
    }

    // ==================== 内部方法 ====================

    private Lockers requireLocker(Long lockerId) {
        Lockers locker = lockersMapper.selectById(lockerId);
        if (locker == null) {
            throw new CustomExceptions("柜子不存在");
        }
        return locker;
    }

    /** Web 对外地址必须显式配置（柜码 target 指向 Web SPA 落地页，无法按请求推导） */
    private String requireWebBase() {
        String base = webBaseUrlResolver.resolve();
        if (StrUtil.isBlank(base)) {
            throw new CustomExceptions("未配置 Web 对外地址（toolbox.web-base-url），无法生成柜码");
        }
        return base;
    }

    private String lockerAlias(Long lockerId) {
        return "L" + lockerId;
    }

    /** 码 → lockerId（L{lockerId} 形态），不匹配返回 null */
    private Long lockerIdFromCode(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        Matcher matcher = ALIAS_PATTERN.matcher(code);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    /** target 绑定解析：以 /l/{lockerId} 结尾时返回 lockerId 串，否则 null */
    private String boundLockerIdOf(String target) {
        if (StrUtil.isBlank(target) || !target.contains(LANDING_PREFIX)) {
            return null;
        }
        int idx = target.lastIndexOf(LANDING_PREFIX);
        String tail = target.substring(idx + LANDING_PREFIX.length());
        // 尾段必须纯数字且无 path/query 残留
        return tail.matches("\\d{1,15}") ? tail : null;
    }

    private ToolboxShortCode getByCode(String code) {
        return toolboxShortCodeMapper.selectOne(new LambdaQueryWrapper<ToolboxShortCode>()
                .eq(ToolboxShortCode::getCode, code));
    }
}
