package com.smartwash.feature.laundry.repository

import com.smartwash.common.database.entity.LaundryItemEntity
import com.smartwash.common.database.entity.SchoolNameEntity
import com.smartwash.feature.laundry.network.vo.LaundryItem
import com.smartwash.feature.laundry.network.vo.SchoolName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 洗衣/学校缓存 Entity ↔ 网络 VO 映射 JVM 单测（纯函数，逐字段映射 + 往返一致性）。
 */
class EntityMappersTest {

    // ---------- LaundryItem ----------

    @Test
    fun `LaundryItemEntity toVo 逐字段映射`() {
        val entity = LaundryItemEntity(
            itemId = 1L,
            itemName = "标准洗",
            basePrice = 12.5f,
            description = "30 分钟",
        )

        val vo = entity.toVo()

        assertEquals(1L, vo.itemId)
        assertEquals("标准洗", vo.itemName)
        assertEquals(12.5f, vo.basePrice, 0f)
        assertEquals("30 分钟", vo.description)
    }

    @Test
    fun `LaundryItem toEntity 逐字段映射`() {
        val vo = LaundryItem(itemId = 2L, itemName = "精致洗", basePrice = 20f, description = "45 分钟")

        val entity = vo.toEntity()

        assertEquals(2L, entity.itemId)
        assertEquals("精致洗", entity.itemName)
        assertEquals(20f, entity.basePrice, 0f)
        assertEquals("45 分钟", entity.description)
    }

    @Test
    fun `LaundryItem 往返映射保持字段一致`() {
        val entity = LaundryItemEntity(itemId = 3L, itemName = "t", basePrice = 1f, description = "d")
        assertEquals(entity, entity.toVo().toEntity())
    }

    // ---------- SchoolName ----------

    @Test
    fun `SchoolNameEntity toVo 逐字段映射`() {
        val entity = SchoolNameEntity(schoolId = 10L, schoolName = "清华大学")

        val vo = entity.toVo()

        assertEquals(10L, vo.schoolId)
        assertEquals("清华大学", vo.schoolName)
    }

    @Test
    fun `SchoolName toEntity 逐字段映射`() {
        val vo = SchoolName(schoolId = 11L, schoolName = "北京大学")

        val entity = vo.toEntity()

        assertEquals(11L, entity.schoolId)
        assertEquals("北京大学", entity.schoolName)
    }

    @Test
    fun `SchoolName 往返映射保持字段一致`() {
        val entity = SchoolNameEntity(schoolId = 12L, schoolName = "浙江大学")
        assertEquals(entity, entity.toVo().toEntity())
    }
}
