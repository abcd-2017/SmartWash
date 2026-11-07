package com.smartwash.memo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.memo.entity.Memo;
import com.smartwash.memo.from.AddMemoFrom;
import com.smartwash.memo.from.SearchMemoFrom;
import com.smartwash.memo.from.UpdateMemoFrom;
import com.smartwash.memo.vo.MemoVo;
import com.smartwash.memo.vo.TagStatVo;

import java.util.List;

/**
 * 速记手账服务接口。
 * 所有读写均以当前登录用户为主体（userId 由控制器从 UserContextHolder 取出后传入）。
 */
public interface MemoService extends IService<Memo> {

    /** 新增速记，返回新记录 id */
    Long create(AddMemoFrom from, Long userId);

    /** 分页查询当前用户的速记：pinned 优先，其余按更新时间倒序；keyword 模糊、tag 精确 */
    Page<MemoVo> list(SearchMemoFrom from, Long userId);

    /** 更新速记（patch 语义），校验归属，返回成功文案 */
    String update(Long id, UpdateMemoFrom from, Long userId);

    /** 逻辑删除速记（is_delete 置 1），校验归属，返回成功文案 */
    String delete(Long id, Long userId);

    /** 当前用户的标签聚合统计，按 count 倒序 */
    List<TagStatVo> tagStats(Long userId);
}
