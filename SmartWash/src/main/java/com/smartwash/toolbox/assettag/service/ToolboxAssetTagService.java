package com.smartwash.toolbox.assettag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartwash.toolbox.assettag.from.CreateAssetTagFrom;
import com.smartwash.toolbox.assettag.from.UpdateAssetTagFrom;
import com.smartwash.toolbox.assettag.vo.AssetTagPublicVo;
import com.smartwash.toolbox.assettag.vo.AssetTagVo;

/**
 * 物品标签（一物一码 M2）服务。
 * 主记录复用短码内核（contentType=8），明细表承载物品信息；创建/删除同事务级联。
 */
public interface ToolboxAssetTagService {

    /** 创建标签：主表 type=8 短码 + 明细行同事务；联系方式非空则加密落库 */
    AssetTagVo createAssetTag(CreateAssetTagFrom from, Long ownerUserId);

    /** 我的标签分页（按创建时间倒序，含扫码统计） */
    IPage<AssetTagVo> pageMyAssetTags(long page, long size, Long ownerUserId);

    /** 更新标签（信息/联系方式/寻物状态），归属校验不符按 404 语义 */
    AssetTagVo updateAssetTag(Long id, UpdateAssetTagFrom from, Long ownerUserId);

    /** 删除标签：明细 + 访问明细 + 主表同事务级联，归属校验不符按 404 语义 */
    void deleteAssetTag(Long id, Long ownerUserId);

    /** 管理端治理删除（级联同上，无归属校验） */
    void adminDelete(Long id);

    /** 公开解析：标签信息卡；联系方式仅「寻物中 + 已留」时解密下发；写入最近扫码时间 */
    AssetTagPublicVo resolvePublic(String code);
}
