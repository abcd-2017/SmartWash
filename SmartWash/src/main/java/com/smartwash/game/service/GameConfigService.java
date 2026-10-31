package com.smartwash.game.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.from.SearchGameFrom;
import com.smartwash.game.from.UpdateGameFrom;
import com.smartwash.game.from.UploadGameFrom;
import com.smartwash.game.vo.AdminGameVo;
import com.smartwash.game.vo.GameVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 游戏库服务。
 * 管理端：上传（单 html / zip → MinIO 前缀 + 缓存元数据）、分页、元数据编辑、CAS 上下架开关、物理删除 + 存储清理。
 * 用户端：enabled=1 按 sort 升序全量下发（url 为 MinIO 对象直链，端上直拉）。
 */
public interface GameConfigService extends IService<GameConfig> {

    /**
     * 上传游戏（单 html 或 zip 包）。同 gameKey 重复上传 = 覆盖更新（先清旧前缀再写入，URL 不变）。
     *
     * @param from 表单元数据
     * @param file 游戏文件
     * @return 落库后的管理端视图（含可玩地址）
     */
    AdminGameVo upload(UploadGameFrom from, MultipartFile file);

    /**
     * 管理端分页查询：keyword 模糊（title/gameKey）+ category/enabled 精确，含未启用项，按 sort/id 升序。
     */
    Page<AdminGameVo> adminPage(SearchGameFrom from);

    /**
     * 更新元数据字段（gameKey 不可修改；enabled 走专用开关接口）。
     */
    void updateGame(Long id, UpdateGameFrom from);

    /**
     * 上下架开关（CAS 条件更新判影响行数，语义 = 选择哪些下发）。
     * 记录不存在 → 404；已是目标状态 → 幂等成功。
     */
    void changeEnabled(Long id, Integer enabled);

    /**
     * 物理删除：先清 MinIO 前缀（source_type=1，失败阻断可重试），再删记录。
     */
    void deleteGame(Long id);

    /**
     * 用户端下发列表：enabled=1 按 sort 升序、id 升序，不分页。
     */
    List<GameVo> listEnabledGames();
}
