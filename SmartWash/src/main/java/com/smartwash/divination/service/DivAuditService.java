package com.smartwash.divination.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实审计服务（异步）。
 *
 * 定位：后置事实审计不撤回已推流文本，产出质量数据。
 * 规则：从解读文本中抽取引用的干支/六亲/卦名等字段，与 server_chart 比对 → audit_status。
 * fail 进管理端复审队列，喂 prompt 迭代。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivAuditService {

    private final DivRecordMapper recordMapper;
    private final DivInterpretationMapper interpretationMapper;

    /** 干支正则（用于从解读文本中提取引用） */
    private static final Pattern GANZHI_PATTERN = Pattern.compile(
            "[甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥]"
    );

    /** 卦名正则 */
    private static final Pattern GUA_PATTERN = Pattern.compile(
            "[乾坤震巽坎艮兑离]{2}"
    );

    /**
     * 异步审计解读文本的事实一致性。
     *
     * @param interpretationId 解读记录 ID
     */
    @Async
    public void auditAsync(Long interpretationId) {
        try {
            doAudit(interpretationId);
        } catch (Exception e) {
            log.error("事实审计异常, interpretationId: {}", interpretationId, e);
        }
    }

    private void doAudit(Long interpretationId) {
        DivInterpretation interpretation = interpretationMapper.selectById(interpretationId);
        if (interpretation == null) return;

        DivRecord record = recordMapper.selectById(interpretation.getRecordId());
        if (record == null) return;

        String content = interpretation.getContentMd();
        String serverChart = record.getServerChart();
        if (content == null || serverChart == null) {
            interpretation.setAuditStatus(0); // 待审
            interpretationMapper.updateById(interpretation);
            return;
        }

        // 1. 从解读文本中提取引用的干支
        Set<String> citedGanZhi = new HashSet<>();
        Matcher gzMatcher = GANZHI_PATTERN.matcher(content);
        while (gzMatcher.find()) {
            citedGanZhi.add(gzMatcher.group());
        }

        // 2. 从 server_chart 中提取所有干支
        Set<String> chartGanZhi = new HashSet<>();
        Matcher chartMatcher = GANZHI_PATTERN.matcher(serverChart);
        while (chartMatcher.find()) {
            chartGanZhi.add(chartMatcher.group());
        }

        // 3. 比对：解读引用的干支是否都在盘面中
        List<Map<String, String>> diffs = new ArrayList<>();
        int consistent = 1; // 默认通过
        for (String cited : citedGanZhi) {
            if (!chartGanZhi.contains(cited)) {
                consistent = 2; // 不一致
                Map<String, String> diff = new LinkedHashMap<>();
                diff.put("field", "ganzhi");
                diff.put("cited", cited);
                diff.put("inChart", "false");
                diffs.add(diff);
            }
        }

        // 4. 更新审计结果
        interpretation.setAuditStatus(consistent);
        JSONObject auditJson = new JSONObject();
        auditJson.put("citedGanZhi", citedGanZhi);
        auditJson.put("chartGanZhi", chartGanZhi);
        auditJson.put("diffs", diffs);
        interpretation.setAuditJson(auditJson.toJSONString());
        interpretationMapper.updateById(interpretation);

        log.info("事实审计完成, interpretationId: {}, status: {}", interpretationId, consistent);
    }
}
