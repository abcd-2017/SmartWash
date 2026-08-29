package com.smartwash.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应视图对象：同时下发 JWT 令牌与登录者角色。
 * role 说明：管理员取 roles 表中该管理员实际绑定的角色名（root/schools_admin/plant 等），
 * 普通用户固定为 user；供 Web 管理后台按角色渲染菜单（评审报告 Web 端 #2/#3 的前置）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo {

    /** JWT 令牌（7 天有效期），后续请求以 Bearer Token 携带 */
    private String token;

    /** 登录者角色标识：管理员为 roles.role_name 实际值，普通用户为 user */
    private String role;
}
