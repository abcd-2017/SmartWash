package com.smartwash.config;

import com.smartwash.vo.dashboard.DashboardVo;
import com.smartwash.vo.laudry.LaundryPackageVo;
import com.smartwash.vo.schools.SchoolNameVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 缓存 Jackson 序列化器回归测试（替代 FastJSON2 WriteClassName）。
 * 覆盖：
 * 1. 带类型往返：List/POJO/BigDecimal/LocalDateTime 还原为原始类型，服务侧取缓存后强转不失败；
 * 2. 多态白名单：非 com.smartwash./JDK 基础类型的 @type 一律拒绝，防止缓存反序列化漏洞。
 */
@DisplayName("CacheJsonSerializer 缓存序列化白名单测试")
class CacheJsonSerializerTest {

    private final CacheJsonSerializer serializer = new CacheJsonSerializer();

    private static SchoolNameVo school(long id, String name) {
        SchoolNameVo vo = new SchoolNameVo();
        vo.setSchoolId(id);
        vo.setSchoolName(name);
        return vo;
    }

    @Test
    @DisplayName("List< SchoolNameVo > 往返：还原为 ArrayList 且元素类型为 SchoolNameVo")
    void roundTrip_listOfVo() {
        List<SchoolNameVo> value = List.of(school(1L, "第一中学"), school(2L, "第二中学"));

        Object restored = serializer.deserialize(serializer.serialize(value));

        assertInstanceOf(List.class, restored);
        List<?> list = (List<?>) restored;
        assertEquals(2, list.size());
        assertInstanceOf(SchoolNameVo.class, list.get(0), "元素必须还原为业务类型，否则服务侧强转会 ClassCastException");
        assertEquals("第一中学", ((SchoolNameVo) list.get(0)).getSchoolName());
    }

    @Test
    @DisplayName("DashboardVo（含 BigDecimal）往返：金额精度不丢失")
    void roundTrip_dashboardVoWithBigDecimal() {
        DashboardVo vo = new DashboardVo();
        vo.setSchoolCount(3L);
        vo.setUserCount(120L);
        vo.setTodayOrderCount(8L);
        vo.setTodayIncome(new BigDecimal("123.45"));

        Object restored = serializer.deserialize(serializer.serialize(vo));

        assertInstanceOf(DashboardVo.class, restored);
        assertEquals(0, new BigDecimal("123.45").compareTo(((DashboardVo) restored).getTodayIncome()));
    }

    @Test
    @DisplayName("LaundryPackageVo（含 LocalDateTime）往返：时间格式还原为 LocalDateTime")
    void roundTrip_voWithLocalDateTime() {
        LaundryPackageVo vo = new LaundryPackageVo();
        vo.setItemId(9L);
        vo.setItemName("标准洗");
        vo.setBasePrice(new BigDecimal("15.00"));
        vo.setCreatedAt(LocalDateTime.of(2026, 11, 3, 21, 14, 51));

        Object restored = serializer.deserialize(serializer.serialize(vo));

        assertInstanceOf(LaundryPackageVo.class, restored);
        assertEquals(LocalDateTime.of(2026, 11, 3, 21, 14, 51), ((LaundryPackageVo) restored).getCreatedAt());
    }

    @Test
    @DisplayName("多态白名单：@type 被篡改为非白名单类时拒绝反序列化")
    void polymorphicWhitelist_rejectsForeignType() {
        String json = new String(serializer.serialize(school(1L, "第一中学")));
        String tampered = json.replace("com.smartwash.", "com.evil.");
        assertFalseWhitelist(tampered);
    }

    private void assertFalseWhitelist(String tamperedJson) {
        assertNotNull(tamperedJson);
        assertThrows(SerializationException.class,
                () -> serializer.deserialize(tamperedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "缓存 JSON 中的 @type 不在白名单内必须拒绝，防止反序列化漏洞");
    }
}
