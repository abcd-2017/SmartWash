package com.smartwash.utils;

import com.smartwash.exception.CustomExceptions;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * 取件码生成工具（评审报告后端 #40：收敛取件码可预测成分）。
 *
 * <p>格式契约：<code>userId:orderId:6位随机数字</code>——保留三段冒号结构，
 * Android 端（split(":") 取下标 2）与鸿蒙端（split(":").pop() 取末段）按此解析展示，
 * 仅将随机段由 4 位 hutool 随机数改为 SecureRandom 纯随机 6 位数字，消除 userId/orderId 之外的
 * 可预测成分，全局唯一性由"生成时查重 + pickup_code 唯一索引兜底"共同保证。
 */
public final class PickupCodeUtils {

    private PickupCodeUtils() {
    }

    /** 取件码随机段长度：6 位纯数字 */
    public static final int CODE_LENGTH = 6;

    /** 冲突重试上限：查重冲突时最多重试 3 次（连同首次共 4 次尝试），仍冲突则放弃 */
    public static final int MAX_RETRY = 3;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成完整取件码：userId:orderId:6位纯随机数字。
     *
     * @param userId        订单归属用户 ID（取件码前段，三端解析契约）
     * @param orderId       订单 ID（取件码中段，三端解析契约）
     * @param existsChecker 查重函数：入参为候选完整取件码，返回 true 表示库内已存在（pickup_code 唯一索引兜底）
     * @return 未冲突的完整取件码
     * @throws CustomExceptions 连续 {@link #MAX_RETRY} 次重试仍冲突时抛出，由调用方事务整体回滚
     */
    public static String generate(Long userId, Long orderId, Predicate<String> existsChecker) {
        for (int retry = 0; retry <= MAX_RETRY; retry++) {
            String code = format(userId, orderId, randomDigits());
            if (!existsChecker.test(code)) {
                return code;
            }
        }
        throw new CustomExceptions("取件码生成失败，请稍后重试");
    }

    /**
     * 拼装完整取件码：userId:orderId:随机段（三段冒号结构为三端解析契约，不可变更）
     */
    public static String format(Long userId, Long orderId, String randomPart) {
        return String.format("%d:%d:%s", userId, orderId, randomPart);
    }

    /**
     * SecureRandom 生成 6 位纯随机数字（每位独立取 0-9，前导 0 合法），风格与验证码生成保持一致
     */
    public static String randomDigits() {
        StringBuilder digits = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            digits.append(SECURE_RANDOM.nextInt(10));
        }
        return digits.toString();
    }
}
