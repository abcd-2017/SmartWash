package com.smartwash.common;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 枚举工具类
 */
public class EnumUtils {

    /**
     * 将枚举数组转换为 Map，key 为枚举的 code，value 为描述
     */
    public static <T> Map<String, String> toMap(T[] values, Function<T, String> keyMapper, Function<T, String> valueMapper) {
        return Arrays.stream(values).collect(Collectors.toMap(keyMapper, valueMapper));
    }
}
