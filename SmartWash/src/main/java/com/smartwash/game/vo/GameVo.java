package com.smartwash.game.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 游戏视图对象（用户端下发）。
 * 不下发 enabled/remark/存储路径等管理字段，下发面最小化；端上按 sort 升序渲染，
 * gameKey 与分类 category 为端上白名单枚举 key，未知值显示默认样式。
 */
@Data
@Accessors(chain = true)
public class GameVo {

    /** 业务唯一标识，端上与转发 URL 引用用 */
    private String gameKey;

    /** 游戏名 */
    private String title;

    /** 一句话玩法描述 */
    private String description;

    /** 图标 key（端上白名单枚举映射） */
    private String icon;

    /** 图标色组 key：green/blue/orange/pink/teal/purple */
    private String color;

    /** 分类：puzzle/arcade/word/board/other */
    private String category;

    /** 角标文案（如 新/热），空则不显示 */
    private String badge;

    /** 最终可玩地址：MinIO 对象直链（指向入口文件），端上 WebView 直接加载 */
    private String url;
}
