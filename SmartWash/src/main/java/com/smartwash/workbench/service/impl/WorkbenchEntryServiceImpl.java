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
import com.smartwash.workbench.storage.WorkbenchImageStorage;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /** 配图存储（MinIO workbench/covers/ 前缀） */
    @Autowired
    private WorkbenchImageStorage workbenchImageStorage;

    /** 配图大小上限 5MB */
    private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;

    /** 配图类型白名单 */
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

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
        // notice 公告条允许无跳转（纯文案公告）；其余类型必须配 target，端上点击才有落点
        if (!"notice".equals(from.getType()) && !StringUtils.hasText(from.getTarget())) {
            throw new CustomExceptions("该卡片类型必须配置跳转 target");
        }
        WorkbenchEntry entry = new WorkbenchEntry();
        BeanUtils.copyProperties(from, entry);
        // 空串统一落 NULL（target/sectionTitle/imageUrl 与 description/badge 同语义）
        if (!StringUtils.hasText(from.getTarget())) {
            entry.setTarget(null);
        }
        if (!StringUtils.hasText(from.getSectionTitle())) {
            entry.setSectionTitle(null);
        }
        if (!StringUtils.hasText(from.getImageUrl())) {
            entry.setImageUrl(null);
        }
        try {
            this.save(entry);
        } catch (DuplicateKeyException e) {
            throw new CustomExceptions("entry_key 已存在");
        }
        log.info("新增工作台入口, entryKey: {}, title: {}, type: {}, sort: {}",
                entry.getEntryKey(), entry.getTitle(), entry.getType(), entry.getSort());
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
        if (StrUtil.isNotBlank(from.getType())) {
            entry.setType(from.getType());
        }
        if (StrUtil.isNotBlank(from.getTarget())) {
            entry.setTarget(from.getTarget());
        }
        if (from.getSectionTitle() != null) {
            entry.setSectionTitle(from.getSectionTitle().isEmpty() ? null : from.getSectionTitle());
        }
        if (from.getImageUrl() != null) {
            entry.setImageUrl(from.getImageUrl().isEmpty() ? null : from.getImageUrl());
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
        // 类型切换到非 notice 时兜底校验 target（避免存量 notice 改 tile 后端上点击无落点）
        if (!"notice".equals(entry.getType()) && !StringUtils.hasText(entry.getTarget())) {
            throw new CustomExceptions("该卡片类型必须配置跳转 target");
        }
        this.updateById(entry);
        log.info("更新工作台入口, id: {}, entryKey: {}", entry.getId(), entry.getEntryKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteEntries(String ids) {
        log.info("删除工作台入口, ids: {}", ids);
        for (String id : ids.split(",")) {
            this.removeById(parseId(id));
        }
    }

    /** ids 逐个解析为 Long；controller 已用 @Pattern 兜住畸形入参，此处再做一层防御，避免 NumberFormatException 直达全局兜底 */
    private Long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new CustomExceptions("ids 必须为逗号分隔的数字");
        }
    }

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomExceptions("请选择要上传的图片");
        }
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new CustomExceptions(413, "图片大小不能超过5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new CustomExceptions("仅支持 png / jpeg / webp 图片");
        }
        try {
            return workbenchImageStorage.putImage(file.getBytes(), contentType);
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            log.error("工作台配图上传失败", e);
            throw new CustomExceptions("图片上传失败，请稍后重试");
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
