package com.smartwash.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartWash API")
                        .description("校园洗衣寄存柜管理平台 API 文档")
                        .version("1.0.0"))
                .tags(List.of(
                        new Tag().name("认证管理").description("用户登录、注册、验证码接口"),
                        new Tag().name("管理员管理").description("管理员用户的增删改查"),
                        new Tag().name("优惠券管理").description("优惠券模板的增删改查"),
                        new Tag().name("仪表盘").description("后台工作台统计数据"),
                        new Tag().name("洗衣项目管理").description("洗衣套餐/项目的增删改查"),
                        new Tag().name("寄存柜管理").description("寄存柜的增删改查及状态查询"),
                        new Tag().name("订单管理").description("后台订单的查询、删除及状态更新"),
                        new Tag().name("支付记录管理").description("后台支付记录的增删改查及枚举查询"),
                        new Tag().name("充值记录管理").description("后台充值记录查询"),
                        new Tag().name("角色管理").description("系统角色的增删改查"),
                        new Tag().name("学校管理").description("学校的增删改查"),
                        new Tag().name("用户优惠券管理").description("后台管理用户领取的优惠券记录"),
                        new Tag().name("用户管理").description("后台用户的增删改查"),
                        new Tag().name("用户端-优惠券").description("用户端优惠券相关接口"),
                        new Tag().name("用户端-洗衣项目").description("用户端洗衣项目查询接口"),
                        new Tag().name("用户端-寄存柜").description("用户端寄存柜查询接口"),
                        new Tag().name("用户端-订单").description("用户端订单的创建、查询、状态变更等接口"),
                        new Tag().name("用户端-支付").description("用户端支付相关接口"),
                        new Tag().name("用户端-充值").description("用户端充值相关接口"),
                        new Tag().name("用户端-学校").description("用户端学校查询接口"),
                        new Tag().name("用户端-用户优惠券").description("用户端优惠券领取和使用相关接口"),
                        new Tag().name("用户端-用户信息").description("用户端个人信息管理接口")
                ));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("后台管理端")
                .pathsToMatch("/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi webApi() {
        return GroupedOpenApi.builder()
                .group("web")
                .displayName("用户端")
                .pathsToMatch("/web/**", "/auth/**")
                .build();
    }
}
