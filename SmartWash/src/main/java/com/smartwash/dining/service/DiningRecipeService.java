package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.SearchDiningRecipeFrom;
import com.smartwash.dining.from.SearchWebDiningRecipeFrom;
import com.smartwash.dining.from.UpdateDiningRecipeFrom;
import com.smartwash.dining.vo.DiningRecipeVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 菜谱服务：平台公共菜谱库的读写入口。
 * 管理端（02 号票）：Web 后台增删改查、上下架与封面上传；
 * 用户端（04 号票）：上架列表浏览（分类/关键词筛选）、详情、新增（source=USER 直上架）。
 * 封面上传两端共用同一校验与存储链路（{@link #uploadCover}）。
 */
public interface DiningRecipeService extends IService<DiningRecipe> {

    /** 管理端：分页查询（分类精确/状态/菜名关键词筛选，按 id 降序=新菜谱在前） */
    Page<DiningRecipeVo> adminPage(SearchDiningRecipeFrom from);

    /** 管理端：新增菜谱（source=ADMIN，记录创建管理员；分类传入时校验存在；默认上架） */
    void addRecipe(AddDiningRecipeFrom from);

    /** 管理端：修改菜谱（字段 null=不修改；传入分类时校验存在） */
    void updateRecipe(UpdateDiningRecipeFrom from);

    /** 管理端：批量物理删除（ids 逗号分隔；封面 MinIO 对象不回收，对齐 game/workbench 孤儿对象惯例） */
    void deleteRecipes(String ids);

    /** 管理端：上下架（status 仅接受 1=上架 0=下架；下架后不再出现在上架查询，历史点单订单不受影响） */
    void changeStatus(Long id, Integer status);

    /**
     * 管理端：上传封面（JPG/PNG/WebP + 魔数 + 扩展名一致性 + 5MB 上限），
     * 存 MinIO dining/covers/ 前缀，返回对象键（直链由 MinioConfig.urlOf 拼装，库内不存绝对 URL）
     */
    String uploadCover(MultipartFile file);

    // ==================== 用户端（04 号票） ====================

    /** 用户端：上架菜谱分页（仅 status=1，分类精确/菜名关键词筛选，按 id 降序=新菜谱在前） */
    Page<DiningRecipeVo> userPage(SearchWebDiningRecipeFrom from);

    /** 用户端：菜谱详情（仅上架可见；不存在或已下架均按业务失败拒绝，避免暴露下架菜谱内容） */
    DiningRecipeVo userDetail(Long id);

    /**
     * 用户端：新增菜谱（source=USER 直上架、创建人取传入用户 ID；分类哨兵协议与管理端一致：
     * null/0 均未分类、>0 校验存在）。无审核流，保存即可被所有人看到
     */
    void addUserRecipe(AddDiningRecipeFrom from, Long userId);
}
