package com.smartwash.toolbox.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 过期短码清理任务测试：仅物理清理 expire_at 已过 30 天的记录与关联明细，
 * 阈值必须为 now - 30 天之前；失效判断在读取路径完成，本任务不参与。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ToolboxShortCodeCleaner 过期清理测试")
class ToolboxShortCodeCleanerTest {

    @Mock
    private ToolboxShortCodeService shortCodeService;
    @Mock
    private ToolboxShortVisitService visitService;

    private ToolboxShortCodeCleaner cleaner;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供断言前物化 wrapper 的 SQL 段
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ToolboxShortCode.class);
        TableInfoHelper.initTableInfo(assistant, ToolboxShortVisit.class);
    }

    @BeforeEach
    void setUp() {
        cleaner = new ToolboxShortCodeCleaner(shortCodeService, visitService);
    }

    @Test
    @DisplayName("无过期记录时不做任何删除")
    void cleanExpired_nothingExpired_noop() {
        when(shortCodeService.list(any(Wrapper.class))).thenReturn(List.of());

        cleaner.cleanExpired();

        verify(visitService, never()).remove(any(Wrapper.class));
        verify(shortCodeService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("清理过期记录：先删关联明细（按 code）再删主记录")
    void cleanExpired_removesVisitsThenRecords() {
        ToolboxShortCode expired = new ToolboxShortCode().setId(7L).setCode("old0001");
        when(shortCodeService.list(any(Wrapper.class))).thenReturn(List.of(expired));

        cleaner.cleanExpired();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ToolboxShortVisit>> visitCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(visitService, times(1)).remove(visitCaptor.capture());
        visitCaptor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        assertTrue(visitCaptor.getValue().getParamNameValuePairs().containsValue("old0001"),
                "明细必须按该短码的 code 删除");
        verify(shortCodeService, times(1)).removeById(7L);
    }

    @Test
    @DisplayName("清理阈值：expire_at 必须严格早于 now-30 天（宽限期内不清理）")
    void cleanExpired_thresholdIs30DaysAgo() {
        when(shortCodeService.list(any(Wrapper.class))).thenReturn(List.of());

        cleaner.cleanExpired();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ToolboxShortCode>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(shortCodeService).list(captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，阈值参数才写入 paramNameValuePairs
        Object threshold = captor.getValue().getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("清理条件必须包含 expire_at 时间阈值"));
        LocalDateTime latest = LocalDateTime.now().minusDays(30);
        assertTrue(((LocalDateTime) threshold).isBefore(latest),
                "阈值必须早于 now-30 天，宽限期内的过期短码不得清理");
    }
}
