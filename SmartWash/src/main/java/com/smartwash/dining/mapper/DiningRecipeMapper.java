package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningRecipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜谱 Mapper：MyBatis-Plus BaseMapper 覆盖常规 CRUD；
 * 食材反查搜索（契约 §2.4）用一条 join 查询取菜谱 ID 集合，再交回 LambdaQueryWrapper 组合其余条件。
 */
@Mapper
public interface DiningRecipeMapper extends BaseMapper<DiningRecipe> {

    /**
     * 按配料名模糊反查菜谱 ID（去重）。
     * 走 dining_recipe_ingredient join dining_ingredient，{@code #{keyword}} 预编译防注入；
     * 命中为空时由调用方短路为空结果，不回表。
     */
    @Select("SELECT DISTINCT ri.recipe_id FROM dining_recipe_ingredient ri "
            + "JOIN dining_ingredient i ON i.id = ri.ingredient_id "
            + "WHERE i.name LIKE CONCAT('%', #{keyword}, '%')")
    List<Long> selectRecipeIdsByIngredientName(@Param("keyword") String keyword);
}
