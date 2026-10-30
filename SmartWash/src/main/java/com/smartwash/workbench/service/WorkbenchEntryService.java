package com.smartwash.workbench.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.workbench.entity.WorkbenchEntry;
import com.smartwash.workbench.from.AddWorkbenchEntryFrom;
import com.smartwash.workbench.from.SearchWorkbenchEntryFrom;
import com.smartwash.workbench.from.UpdateWorkbenchEntryFrom;
import com.smartwash.workbench.vo.AdminWorkbenchEntryVo;
import com.smartwash.workbench.vo.WorkbenchEntryVo;

import java.util.List;

/**
 * 工作台入口配置服务。
 * 用户端下发走 Redis 缓存（workbenchEntries），管理端增删改统一 @CacheEvict 失效，
 * 保证端上拉到的入口列表与管理端配置最终一致。
 */
public interface WorkbenchEntryService extends IService<WorkbenchEntry> {

    /** 管理端：分页查询（title 模糊 + enabled 筛选，按 sort/id 升序与管理端排序语义一致） */
    Page<AdminWorkbenchEntryVo> adminPage(SearchWorkbenchEntryFrom from);

    /** 管理端：新增入口（entry_key 重复校验 + 唯一索引兜底裁决） */
    void addEntry(AddWorkbenchEntryFrom from);

    /** 管理端：更新入口（entry_key 不可修改；badge/description/remark 空串=清空） */
    void updateEntry(UpdateWorkbenchEntryFrom from);

    /** 管理端：批量物理删除（ids 逗号分隔） */
    void deleteEntries(String ids);

    /** 用户端：启用中的入口列表（sort 升序、id 升序，不分页），走缓存 */
    List<WorkbenchEntryVo> listEnabledEntries();
}
