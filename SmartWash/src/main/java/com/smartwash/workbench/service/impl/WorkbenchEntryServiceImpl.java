package com.smartwash.workbench.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.workbench.entity.WorkbenchEntry;
import com.smartwash.workbench.from.AddWorkbenchEntryFrom;
import com.smartwash.workbench.from.SearchWorkbenchEntryFrom;
import com.smartwash.workbench.from.UpdateWorkbenchEntryFrom;
import com.smartwash.workbench.mapper.WorkbenchEntryMapper;
import com.smartwash.workbench.service.WorkbenchEntryService;
import com.smartwash.workbench.vo.AdminWorkbenchEntryVo;
import com.smartwash.workbench.vo.WorkbenchEntryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工作台入口配置服务实现。
 * 缓存模式与 SchoolsServiceImpl 一致：读走 @Cacheable 的公共方法（经 self 自注入调用绕过 AOP 代理失效），
 * 写操作 @CacheEvict(allEntries) 全量失效；entry_key 唯一性以 uk_entry_key 唯一索引兜底并发。
 */
@Slf4j
@Service
public class WorkbenchEntryServiceImpl extends ServiceImpl<WorkbenchEntryMapper, WorkbenchEntry>
        implements WorkbenchEntryService {

    /** 用户端下发缓存空间名（TTL 见 RedisConfig） */
    public static final String CACHE_NAME = "workbenchEntries";

    /** 启用状态：1启用 */
    private static final int ENABLED_TRUE = 1;

    /** 自注入：@Cacheable 方法须经代理调用才生效，同类内部直调会绕过 AOP */
    @Lazy
    @Autowired
    private WorkbenchEntryServiceImpl self;

    // ==================== 管理端 ====================

    @Override
    public Page<AdminWorkbenchEntryVo> adminPage(SearchWorkbenchEntryFrom from) {
        LambdaQueryWrapper<WorkbenchEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(from.getTitle()), WorkbenchEntry::getTitle, from.getTitle());
        wrapper.eq(from.getEnabled() != null, WorkbenchEntry::getEnabled, from.getEnabled());
        // 与用户端渲染顺序一致（sort 升序、id 升序），后台所见即端上所得
        wrapper.orderByAsc(WorkbenchEntry::getSort).orderByAsc(WorkbenchEntry::getId);

        Page<WorkbenchEntry> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Page<AdminWorkbenchEntryVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::buildAdminVo).toList());
        return voPage;
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void addEntry(AddWorkbenchEntryFrom from) {
        // 先查重给业务侧友好提示；并发窗口由 uk_entry_key 唯一索引最终裁决
        if (this.getByEntryKey(from.getEntryKey()) != null) {
            throw new CustomExceptions("entry_key 已存在");
        }
        WorkbenchEntry entry = new WorkbenchEntry();
        BeanUtils.copyProperties(from, entry);
        try {
            this.save(entry);
        } catch (DuplicateKeyException e) {
            throw new CustomExceptions("entry_key 已存在");
        }
        log.info("新增工作台入口, entryKey: {}, title: {}, sort: {}", entry.getEntryKey(), entry.getTitle(), entry.getSort());
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void updateEntry(UpdateWorkbenchEntryFrom from) {
        WorkbenchEntry entry = this.getById(from.getId());
        if (entry == null) {
            throw new CustomExceptions("入口不存在");
        }
        // 仅覆盖传入字段（MyBatis-Plus updateById 跳过 null 列）；entry_key 为端上对齐标识，不允许修改
        if (StringUtils.hasText(from.getTitle())) {
            entry.setTitle(from.getTitle());
        }
        if (from.getDescription() != null) {
            entry.setDescription(from.getDescription().isEmpty() ? null : from.getDescription());
        }
        if (StrUtil.isNotBlank(from.getIcon())) {
            entry.setIcon(from.getIcon());
        }
        if (StrUtil.isNotBlank(from.getColor())) {
            entry.setColor(from.getColor());
        }
        if (StrUtil.isNotBlank(from.getTarget())) {
            entry.setTarget(from.getTarget());
        }
        if (from.getBadge() != null) {
            entry.setBadge(from.getBadge().isEmpty() ? null : from.getBadge());
        }
        if (from.getEnabled() != null) {
            entry.setEnabled(from.getEnabled());
        }
        if (from.getSort() != null) {
            entry.setSort(from.getSort());
        }
        if (from.getRemark() != null) {
            entry.setRemark(from.getRemark().isEmpty() ? null : from.getRemark());
        }
        this.updateById(entry);
        log.info("更新工作台入口, id: {}, entryKey: {}", entry.getId(), entry.getEntryKey());
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteEntries(String ids) {
        log.info("删除工作台入口, ids: {}", ids);
        for (String id : ids.split(",")) {
            this.removeById(Long.parseLong(id.trim()));
        }
    }

    // ==================== 用户端 ====================

    @Override
    public List<WorkbenchEntryVo> listEnabledEntries() {
        return self.enabledEntriesFromCache();
    }

    /**
     * 启用中的入口列表（缓存命中路径）。
     * 仅 enabled=1，按 sort 升序、id 升序，不分页；缓存失效由管理端写操作统一处理。
     */
    @Cacheable(value = CACHE_NAME, key = "'all'")
    public List<WorkbenchEntryVo> enabledEntriesFromCache() {
        LambdaQueryWrapper<WorkbenchEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkbenchEntry::getEnabled, ENABLED_TRUE);
        wrapper.orderByAsc(WorkbenchEntry::getSort).orderByAsc(WorkbenchEntry::getId);
        return this.list(wrapper).stream().map(this::buildVo).toList();
    }

    // ==================== 内部方法 ====================

    private WorkbenchEntry getByEntryKey(String entryKey) {
        // 直查 baseMapper（与 toolbox 内核一致），单测 mock 路径确定
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<WorkbenchEntry>()
                .eq(WorkbenchEntry::getEntryKey, entryKey));
    }

    private WorkbenchEntryVo buildVo(WorkbenchEntry entry) {
        WorkbenchEntryVo vo = new WorkbenchEntryVo();
        BeanUtils.copyProperties(entry, vo);
        return vo;
    }

    private AdminWorkbenchEntryVo buildAdminVo(WorkbenchEntry entry) {
        AdminWorkbenchEntryVo vo = new AdminWorkbenchEntryVo();
        BeanUtils.copyProperties(entry, vo);
        return vo;
    }
}
