package com.smartwash.toolbox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.from.SearchShortCodeFrom;
import com.smartwash.toolbox.from.UpdateShortCodeFrom;
import com.smartwash.toolbox.vo.AdminShortCodeVo;
import com.smartwash.toolbox.vo.ShortCodeStatsVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 短码内核服务。
 * 并发安全说明（方案 §5.2）：创建不走「先查后插」（TOCTOU 竞态窗口），
 * 直接插入、由 uk_code 唯一索引最终裁决，冲突时随机码重试、别名直接报错。
 */
public interface ToolboxShortCodeService extends IService<ToolboxShortCode> {

    /** 创建短码（唯一索引裁决 + 冲突重试；M1 仅交付 content_type=1 短链入口） */
    ShortCodeVo createShortCode(AddShortCodeFrom from, Long ownerUserId);

    /** 我的短码分页（按创建时间倒序） */
    IPage<ShortCodeVo> listMyShortCodes(SearchShortCodeFrom from, Long ownerUserId);

    /** 更新短码（target/expireAt），成功后清对应缓存，302 语义下改向立即生效 */
    ShortCodeVo updateShortCode(Long id, UpdateShortCodeFrom from, Long ownerUserId);

    /** 删除短码（物理删除 + 清缓存） */
    void deleteShortCode(Long id, Long ownerUserId);

    /** 当前用户短码的访问明细分页 */
    IPage<ShortCodeStatsVo> pageVisits(Long id, Long ownerUserId, long page, long size);

    /** 公开跳转解析（私有码一律 404，不暴露存在性） */
    ResolveResult resolveForRedirect(String code);

    /** owner 解析（私有码的唯一取值通道；校验归属，不符按 404 语义处理） */
    ShortCodeVo resolveForOwner(String code, Long ownerUserId);

    /** 管理端：全局分页（code/ownerPhone/contentType 筛选，不受 is_public 限制） */
    IPage<AdminShortCodeVo> adminPage(SearchShortCodeFrom from);

    /** 管理端：详情（含最近访问明细摘要） */
    AdminShortCodeVo adminDetail(Long id);

    /** 管理端：治理删除（物理删除并清缓存） */
    void adminDelete(Long id);

    /**
     * 跳转解析结果：状态语义对齐 HTTP（404 不存在/私有、410 过期/未解锁/达上限）。
     */
    @Getter
    @AllArgsConstructor
    class ResolveResult {

        public enum ResolveStatus { OK, NOT_FOUND, UNAVAILABLE }

        private final ResolveStatus status;

        /** 状态为 OK 时的跳转目标 */
        private final String target;

        public static ResolveResult ok(String target) {
            return new ResolveResult(ResolveStatus.OK, target);
        }

        public static ResolveResult notFound() {
            return new ResolveResult(ResolveStatus.NOT_FOUND, null);
        }

        public static ResolveResult unavailable() {
            return new ResolveResult(ResolveStatus.UNAVAILABLE, null);
        }

        public boolean isNotFound() {
            return status == ResolveStatus.NOT_FOUND;
        }

        public boolean isUnavailable() {
            return status == ResolveStatus.UNAVAILABLE;
        }
    }
}
