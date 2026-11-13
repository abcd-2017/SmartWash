package com.smartwash.notice.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.notice.constant.NoticeType;
import com.smartwash.notice.entity.NoticeRead;
import com.smartwash.notice.from.SearchNoticeFrom;
import com.smartwash.notice.vo.NoticeVo;
import com.smartwash.notice.entity.Notice;
import com.smartwash.notice.mapper.NoticeMapper;
import com.smartwash.notice.mapper.NoticeReadMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内信服务测试（纯 Mockito，不依赖真实数据库）。
 * 覆盖 05 号票验收：广播行为回归、定向发送（多接收人一次调用）、
 * 未读数「广播或定向给我」口径、已读幂等（uk_user_notice）不破坏。
 * 口径断言方式：捕获传给 Mapper 的查询参数与 Wrapper（Mapper 是服务层的外部边界，
 * 等价于验证数据库将执行的查询条件，不耦合 wrapper 的构造写法）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NoticeServiceImpl 定向能力扩展测试")
class NoticeServiceImplTest {

    @Mock
    private NoticeMapper noticeMapper;

    @Mock
    private NoticeReadMapper noticeReadMapper;

    private NoticeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Notice.class);
    }

    @BeforeEach
    void setUp() {
        service = new NoticeServiceImpl(noticeReadMapper);
        // ServiceImpl 的内置方法走父类 baseMapper 字段，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", noticeMapper);
    }

    // ==================== 定向发送 ====================

    @Test
    @DisplayName("定向发送：多接收人一次调用，一人一行且 user_id 落到对应接收人")
    void publishTargeted_sendsOneRowPerUser() {
        when(noticeMapper.insert(any(Notice.class))).thenReturn(1);

        int sent = service.publishTargeted(NoticeType.DINING_JOIN, "张三加入了「今晚吃啥」",
                "张三 已加入点单，快去看看 TA 选了什么", "smartwash://dining/order/42?t=abc",
                List.of(11L, 22L, 33L));

        assertEquals(3, sent);
        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper, times(3)).insert(captor.capture());
        List<Notice> rows = captor.getAllValues();
        assertEquals(List.of(11L, 22L, 33L), rows.stream().map(Notice::getUserId).toList(),
                "每个接收人一行，user_id 必须逐行对应");
        for (Notice row : rows) {
            assertEquals("DINING_JOIN", row.getType());
            assertEquals("张三加入了「今晚吃啥」", row.getTitle());
            assertEquals("张三 已加入点单，快去看看 TA 选了什么", row.getContent());
            assertEquals("smartwash://dining/order/42?t=abc", row.getDeepLink());
        }
    }

    @Test
    @DisplayName("定向发送：接收人列表去重，同一人重复传入只发一条")
    void publishTargeted_deduplicatesReceivers() {
        when(noticeMapper.insert(any(Notice.class))).thenReturn(1);

        int sent = service.publishTargeted(NoticeType.DINING_FINISH, "点单已结束",
                "购买清单已生成", null, List.of(11L, 11L, 22L));

        assertEquals(2, sent);
        verify(noticeMapper, times(2)).insert(any(Notice.class));
    }

    @Test
    @DisplayName("定向发送：接收人列表为空或必填项缺失时拒绝发送")
    void publishTargeted_rejectsInvalidInput() {
        CustomExceptions emptyUsers = assertThrows(CustomExceptions.class,
                () -> service.publishTargeted(NoticeType.DINING_JOIN, "标题", "内容", null, List.of()));
        assertTrue(emptyUsers.getMessage().startsWith("定向发送参数不完整"),
                "空接收人列表必须被拒绝并给出参数不完整语义");

        CustomExceptions nullType = assertThrows(CustomExceptions.class,
                () -> service.publishTargeted(null, "标题", "内容", null, List.of(11L)));
        assertTrue(nullType.getMessage().startsWith("定向发送参数不完整"),
                "类型缺失必须被拒绝并给出参数不完整语义");
    }

    // ==================== 未读数口径 ====================

    @Test
    @DisplayName("未读数口径：可见总数按「广播或定向给我」过滤，未读 = 可见 − 已读")
    void countUnread_countsBroadcastOrTargetedToMe() {
        // DB 侧可见 notice 总数（广播 + 定向给我）由查询条件过滤，这里只 mock 返回值
        when(noticeMapper.selectCount(any())).thenReturn(5L);
        when(noticeReadMapper.selectCount(any())).thenReturn(2L);

        long unread = service.countUnread(9L);

        assertEquals(3L, unread, "未读 = 可见总数 − 本人已读数");

        // 口径锁定：notice 计数查询必须带「user_id IS NULL OR user_id = 本人」条件，
        // 否则定向给别人的消息会被误计入我的未读
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Notice>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(noticeMapper).selectCount(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sqlSegment.contains("USER_ID IS NULL"),
                "未读数计数必须包含广播（user_id IS NULL）条件，实际片段: " + sqlSegment);
        assertTrue(sqlSegment.contains(" OR "), "广播与定向条件必须是 OR 关系，实际片段: " + sqlSegment);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(9L),
                "未读数计数必须包含定向给我（user_id = 本人）条件");
    }

    // ==================== 收件箱 / 全部已读口径 ====================

    @Test
    @DisplayName("收件箱口径：分页查询带「广播或定向给我」条件，is_read 按本人回填")
    void pageInbox_filtersByVisibilityAndFillsIsRead() {
        Notice broadcast = new Notice().setId(1L).setTitle("系统维护通知").setType("SYSTEM").setDeepLink(null);
        Notice mine = new Notice().setId(2L).setTitle("张三加入了点单").setType("DINING_JOIN").setUserId(9L);
        Page<Notice> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(broadcast, mine));
        doReturn(page).when(noticeMapper).selectPage(any(), any());
        // 本人已读集合：仅 notice 1
        when(noticeReadMapper.selectList(any()))
                .thenReturn(List.of(new NoticeRead().setUserId(9L).setNoticeId(1L)));

        Page<NoticeVo> inbox = service.pageInbox(new SearchNoticeFrom(), 9L);

        assertEquals(2, inbox.getRecords().size());
        assertEquals(Boolean.TRUE, inbox.getRecords().get(0).getIsRead(), "广播已读记录须回填 isRead=true");
        assertEquals(Boolean.FALSE, inbox.getRecords().get(1).getIsRead(), "定向给我的消息默认未读");

        // 口径锁定：收件箱查询必须带「广播或定向给我」条件
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Notice>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(noticeMapper).selectPage(any(), captor.capture());
        assertVisibleToUserCondition(captor.getValue(), 9L, "收件箱");
    }

    @Test
    @DisplayName("全部已读口径：仅对「广播或定向给我」的公告插入已读，重复键吞掉")
    void readAll_onlyMarksVisibleNotices() {
        Notice n1 = new Notice().setId(1L).setTitle("广播").setType("SYSTEM");
        Notice n2 = new Notice().setId(2L).setTitle("给我的").setType("DINING_JOIN").setUserId(9L);
        Page<Notice> page = new Page<>(1, 100, 2);
        page.setRecords(List.of(n1, n2));
        doReturn(page).when(noticeMapper).selectPage(any(), any());
        // 第二条插入撞唯一索引：模拟部分已读的存量数据，幂等不中断整批
        when(noticeReadMapper.insert(any(NoticeRead.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("uk_user_notice"));

        int inserted = service.readAll(9L);

        assertEquals(1, inserted, "撞唯一索引的重复标记必须吞掉且不计入新增");
        verify(noticeReadMapper, times(2)).insert(any(NoticeRead.class));

        // 口径锁定：全部已读扫描必须带「广播或定向给我」条件
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Notice>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(noticeMapper).selectPage(any(), captor.capture());
        assertVisibleToUserCondition(captor.getValue(), 9L, "全部已读");
    }

    /** 复用断言：wrapper 必须含「user_id IS NULL OR user_id = 本人」可见性条件 */
    private void assertVisibleToUserCondition(LambdaQueryWrapper<Notice> wrapper,
                                              Long userId, String scene) {
        String sqlSegment = wrapper.getSqlSegment().toUpperCase();
        assertTrue(sqlSegment.contains("USER_ID IS NULL"),
                scene + "查询必须包含广播（user_id IS NULL）条件，实际片段: " + sqlSegment);
        assertTrue(sqlSegment.contains(" OR "), scene + "广播与定向条件必须是 OR 关系，实际片段: " + sqlSegment);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(userId),
                scene + "查询必须包含定向给我（user_id = 本人）条件");
    }

    // ==================== 已读幂等与可见性 ====================

    @Test
    @DisplayName("标记已读：定向给别人的消息对本人按「不存在」拒绝，广播与定向给我的正常标记")
    void markRead_rejectsNoticeTargetedToOthers() {
        Notice targetedToOthers = new Notice().setId(3L).setTitle("给77号用户的").setType("DINING_FINISH").setUserId(77L);
        when(noticeMapper.selectById(3L)).thenReturn(targetedToOthers);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.markRead(3L, 9L));
        assertEquals("公告不存在", ex.getMessage(), "不可见定向消息必须与不存在同语义（不暴露存在性）");
        verify(noticeReadMapper, never()).insert(any(NoticeRead.class));

        // 广播消息对本人可见，可正常标记已读
        Notice broadcast = new Notice().setId(1L).setTitle("系统维护通知").setType("SYSTEM");
        when(noticeMapper.selectById(1L)).thenReturn(broadcast);
        when(noticeReadMapper.selectOne(any())).thenReturn(null);
        when(noticeReadMapper.insert(any(NoticeRead.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.markRead(1L, 9L));
        verify(noticeReadMapper).insert(any(NoticeRead.class));

        // 定向给我的消息同样可标记已读
        Notice targetedToMe = new Notice().setId(2L).setTitle("给我的").setType("DINING_JOIN").setUserId(9L);
        when(noticeMapper.selectById(2L)).thenReturn(targetedToMe);
        when(noticeReadMapper.selectOne(any())).thenReturn(null);

        assertDoesNotThrow(() -> service.markRead(2L, 9L));
        verify(noticeReadMapper, times(2)).insert(any(NoticeRead.class));
    }

    @Test
    @DisplayName("已读幂等回归：重复标记静默成功不再插入；并发撞 uk_user_notice 亦静默成功")
    void markRead_idempotentRegression() {
        Notice broadcast = new Notice().setId(1L).setTitle("系统维护通知").setType("SYSTEM");
        when(noticeMapper.selectById(1L)).thenReturn(broadcast);

        // 快路径：本人已读记录已存在 → 静默成功，不再插入
        when(noticeReadMapper.selectOne(any()))
                .thenReturn(new NoticeRead().setUserId(9L).setNoticeId(1L));
        assertDoesNotThrow(() -> service.markRead(1L, 9L));
        verify(noticeReadMapper, never()).insert(any(NoticeRead.class));

        // 并发窗口：查重未命中但插入撞唯一索引 → 判幂等成功
        when(noticeReadMapper.selectOne(any())).thenReturn(null);
        when(noticeReadMapper.insert(any(NoticeRead.class))).thenThrow(new DuplicateKeyException("uk_user_notice"));
        assertDoesNotThrow(() -> service.markRead(1L, 9L));
    }

    // ==================== 广播回归（既有行为不变） ====================

    @Test
    @DisplayName("广播回归：管理端 publish 不设 user_id（广播语义不变），空白深链归一 NULL")
    void publish_broadcastRegression() {
        com.smartwash.notice.from.AddNoticeFrom from = new com.smartwash.notice.from.AddNoticeFrom();
        from.setTitle("  系统维护公告  ");
        from.setContent("今晚 02:00 维护");
        from.setType("SYSTEM");
        from.setDeepLink("   ");
        when(noticeMapper.insert(any(Notice.class))).thenReturn(1);

        service.publish(from, 100L);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(captor.capture());
        Notice saved = captor.getValue();
        assertNull(saved.getUserId(), "管理端发送的公告必须是广播（user_id=NULL），定向只走 publishTargeted");
        assertNull(saved.getDeepLink(), "空白深链必须归一为 NULL（纯文案公告语义）");
        assertEquals("系统维护公告", saved.getTitle());
        assertEquals("SYSTEM", saved.getType());
        assertEquals(100L, saved.getCreatorAdminId());
    }

    @Test
    @DisplayName("广播回归：管理端 publish 仅开放 SYSTEM/ANNOUNCEMENT，DINING 类型不得从管理端发出")
    void publish_rejectsDiningTypeFromAdminEndpoint() {
        com.smartwash.notice.from.AddNoticeFrom bad = new com.smartwash.notice.from.AddNoticeFrom();
        bad.setTitle("标题"); bad.setContent("内容"); bad.setType("DINING_JOIN");

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.publish(bad, 100L));
        assertEquals("类型仅支持 SYSTEM/ANNOUNCEMENT", ex.getMessage());
        verify(noticeMapper, never()).insert(any(Notice.class));
    }
}
