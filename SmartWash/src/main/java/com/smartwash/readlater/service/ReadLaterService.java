package com.smartwash.readlater.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.readlater.entity.ReadLater;
import com.smartwash.readlater.from.AddReadLaterFrom;
import com.smartwash.readlater.from.SearchReadLaterFrom;
import com.smartwash.readlater.from.UpdateReadLaterFrom;
import com.smartwash.readlater.vo.ReadLaterVo;

/**
 * 稍后读（AI 摘要）服务。
 * 收藏 https 链接 → 抓取标题 → 按需 AI 摘要 → 标签/已读管理 → 一键转短链分享。
 */
public interface ReadLaterService extends IService<ReadLater> {

    /** 新增收藏：抓取页面标题（失败以 url 兜底，不阻塞入库），返回记录 id */
    Long add(AddReadLaterFrom from, Long userId);

    /** 我的收藏分页：keyword 模糊匹配 url+title+digest，未读优先、时间倒序 */
    IPage<ReadLaterVo> list(SearchReadLaterFrom from, Long userId);

    /** 按需生成 AI 摘要（平台默认模型），落库并返回摘要文本 */
    String generateDigest(Long id, Long userId);

    /** patch 更新（tags 整体替换 / 已读状态），归属校验 + 条件更新 */
    void update(Long id, UpdateReadLaterFrom from, Long userId);

    /** 逻辑删除（is_delete 置 1），归属校验 + 条件更新 */
    void delete(Long id, Long userId);

    /** 一键转短链：为收藏 url 创建 contentType=1 短码，返回完整短链地址 */
    String shorten(Long id, Long userId);
}
