/**
 * 公开路径白名单：无需 Bearer Token 即可访问的后端接口。
 *
 * 事实源（唯一权威）：
 *   SmartWash/src/main/java/com/smartwash/config/SecurityConfig.java
 *   → securityFilterChain(HttpSecurity) → authorizeHttpRequests(...)
 *   → requestMatchers(...).permitAll() 段。
 *
 * 同步约定：
 *   后端 permitAll 清单新增/删除/修改路径时，必须同步更新本文件；
 *   否则端上会对公开接口误注入 token，或对已转私有的接口漏注入 token。
 *   四端联动检查表见根目录 AGENTS.md。
 *
 * 语义（对齐 Android 端 @RequireAuthorization 未标注的行为）：
 *   命中白名单的请求不注入 Authorization，也不因本地无 token 而拦截——
 *   未登录用户同样能拉取启动配置、洗衣项目列表等只读数据。
 */

/**
 * 公开路径判定工具。
 *
 * 匹配顺序（固定）：
 *   1. url 为 undefined / 空串：不放宽（返回 false），避免空 URL 被误判为公开；
 *   2. 截断 query（"?" 及其后内容不参与匹配）；
 *   3. 先精确匹配 EXACT_PUBLIC_PATHS；
 *   4. 再前缀匹配 PREFIX_PUBLIC_PATHS（前缀统一以 "/" 结尾，避免 /web/t/ 误命中 /web/txxx 之类的路径）。
 */
export class PublicPaths {
  /** 精确匹配的公开路径（后端 permitAll 中的具体路径，不含通配段） */
  private static readonly EXACT_PUBLIC_PATHS: string[] = [
    "/web/orders/status",       // 订单状态字典
    "/web/laundryItems/all",    // 洗衣项目列表
    "/web/lockers/status",      // 柜机状态字典
    "/web/lockers/all",         // 柜机列表
    "/web/schools/allName",     // 学校名单
    "/web/payments/payType",    // 支付方式字典
    "/web/payments/payStatus", // 支付状态字典
    "/web/app/version",         // App 版本信息
    "/web/app/download",        // App 下载信息
    "/web/deeplink/routes",     // deeplink 字典（端上冷启动解析兜底）
    "/web/config",              // 服务端公开配置（MinIO 端点等）
    "/swagger-ui.html"          // Swagger 文档入口
  ]

  /** 前缀匹配的公开路径（后端 permitAll 中的通配段，统一以 "/" 结尾） */
  private static readonly PREFIX_PUBLIC_PATHS: string[] = [
    "/auth/",        // 认证接口（登录、注册、验证码）
    "/web/t/",       // 工具箱短码跳转（匿名扫码流量）
    "/swagger-ui/",  // Swagger UI 静态资源
    "/webjars/",     // Swagger 依赖的 webjar 资源
    "/v3/api-docs/"  // OpenAPI 文档数据
  ]

  /**
   * 判断请求 URL 是否命中公开路径白名单。
   * @param url Axios 请求 URL（相对路径，可能带 query，可能为 undefined）
   * @returns true 表示公开接口：调用方不得注入 token，也不得因本地无 token 而拦截
   */
  static isPublic(url: string | undefined): boolean {
    // 1. 空值不放宽
    if (url === undefined || url === "") {
      return false
    }
    // 2. 截断 query，仅以路径部分参与匹配
    const queryIndex = url.indexOf("?")
    const path = queryIndex >= 0 ? url.substring(0, queryIndex) : url
    // 3. 先精确匹配
    if (PublicPaths.EXACT_PUBLIC_PATHS.indexOf(path) >= 0) {
      return true
    }
    // 4. 后前缀匹配
    for (let i = 0; i < PublicPaths.PREFIX_PUBLIC_PATHS.length; i++) {
      if (path.startsWith(PublicPaths.PREFIX_PUBLIC_PATHS[i])) {
        return true
      }
    }
    return false
  }
}
