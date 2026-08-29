package com.smartwash.divination.core;

import com.smartwash.divination.core.liuyao.LiuYaoEngine;
import com.smartwash.divination.core.meihua.MeiHuaEngine;
import com.smartwash.divination.core.qimen.QiMenEngine;
import com.smartwash.divination.core.liuren.LiuRenEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 服务端排盘核心门面。
 * 客户端上传「爻值/起卦参数 + 起卦时刻」原始输入，服务端用同一算法重算权威盘面（server_chart），
 * 与客户端上传盘面（client_chart）比对，不一致即标记 chart_verified=0。
 *
 * 四术算法均为确定性计算（零 AI），口诀级实现，与 Android 端 Kotlin core 差分一致。
 */
@Slf4j
@Component
public class DivinationCore {

    private final LiuYaoEngine liuYaoEngine = new LiuYaoEngine();
    private final MeiHuaEngine meiHuaEngine = new MeiHuaEngine();
    private final QiMenEngine qiMenEngine = new QiMenEngine();
    private final LiuRenEngine liuRenEngine = new LiuRenEngine();

    /**
     * 服务端重算权威盘面。
     *
     * @param method     liuyao/meihua/qimen/liuren
     * @param castMethod auto/manual/time
     * @param castAt     起卦时刻（epoch ms）
     * @param tzOffset   时区偏移分钟
     * @param lines      起卦原始输入（六爻爻值数组等）
     * @return 盘面 JSON
     */
    public String recalculate(String method, String castMethod, long castAt, int tzOffset, List<Integer> lines) {
        Map<String, Object> chart;
        switch (method) {
            case "liuyao" -> chart = liuYaoEngine.cast(castAt, tzOffset, lines, castMethod);
            case "meihua" -> chart = meiHuaEngine.cast(castAt, tzOffset, lines, castMethod);
            case "qimen" -> chart = qiMenEngine.cast(castAt, tzOffset, castMethod);
            case "liuren" -> chart = liuRenEngine.cast(castAt, tzOffset, castMethod);
            default -> throw new IllegalArgumentException("不支持的术数方法：" + method);
        }
        return toJson(chart);
    }

    /**
     * 比对客户端盘面与服务端重算盘面是否一致。
     * 采用关键字段深度比对（忽略格式/空白差异）。
     */
    public boolean verify(String clientChart, String serverChart) {
        if (clientChart == null || clientChart.isBlank()) {
            return false;
        }
        // 归一化比较：解析 JSON 后比较关键字段
        try {
            Map<String, Object> client = parseJson(clientChart);
            Map<String, Object> server = parseJson(serverChart);
            return chartsEqual(client, server);
        } catch (Exception e) {
            log.warn("盘面比对异常，视为不一致", e);
            return false;
        }
    }

    private boolean chartsEqual(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null) return false;
        // 比较核心字段：六爻的 lines/benGua/bianGua；梅花的 benGua/huGua/bianGua 等
        return compareField(a, b, "lines")
                && compareField(a, b, "benGua")
                && compareField(a, b, "bianGua")
                && compareField(a, b, "huGua")
                && compareField(a, b, "method");
    }

    private boolean compareField(Map<String, Object> a, Map<String, Object> b, String field) {
        Object va = a.get(field);
        Object vb = b.get(field);
        return va != null && va.equals(vb);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return (Map<String, Object>) com.alibaba.fastjson2.JSON.parseObject(json, Object.class);
    }

    private String toJson(Map<String, Object> chart) {
        return com.alibaba.fastjson2.JSON.toJSONString(chart);
    }
}
