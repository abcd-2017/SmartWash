package com.smartwash.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.LaundryItems;
import com.smartwash.from.laundry_item.AddLaundryItemsFrom;
import com.smartwash.from.laundry_item.SearchLaundryItemsFrom;
import com.smartwash.from.laundry_item.UpdateLaundryItemsFrom;
import com.smartwash.mapper.LaundryItemsMapper;
import com.smartwash.service.ILaundryItemsService;
import com.smartwash.vo.laudry.LaundryPackageVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Service
public class LaundryItemsServiceImpl extends ServiceImpl<LaundryItemsMapper, LaundryItems> implements ILaundryItemsService {
    //获取所有洗衣套餐
    @Override
    public Page<LaundryPackageVo> getAllLaundryPackage(SearchLaundryItemsFrom LaundryItemsFrom) {
        Page<LaundryItems> page = new Page<>(LaundryItemsFrom.getPage(), LaundryItemsFrom.getSize());
        LambdaQueryWrapper<LaundryItems> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.and(LaundryItemsFrom.getItemId() != null, b -> b.eq(LaundryItems::getItemId, LaundryItemsFrom.getItemName()));
        queryWrapper.and(StringUtils.hasText(LaundryItemsFrom.getItemName()), b -> b.like(LaundryItems::getItemName, LaundryItemsFrom.getItemName()));

        List<LaundryItems> LaundryItems = this.list(page, queryWrapper);
        Page<LaundryPackageVo> LaundryPackageVoPage = new Page<>();
        BeanUtils.copyProperties(page, LaundryPackageVoPage);
        LaundryPackageVoPage.setRecords(LaundryItems.stream().map(it -> {
            LaundryPackageVo LaundryPackageVo = new LaundryPackageVo();
            BeanUtils.copyProperties(it, LaundryPackageVo);
            return LaundryPackageVo;
        }).toList());

        return LaundryPackageVoPage;
    }

    //添加洗衣套餐
    @Override
    public Boolean addLaundryPackage(AddLaundryItemsFrom addLaundryItemsFrom) {
        LaundryItems LaundryItems = new LaundryItems();
        BeanUtils.copyProperties(addLaundryItemsFrom, LaundryItems);
        return save(LaundryItems);
    }

    //修改洗衣套餐
    @Override
    public Boolean updateLaundryPackage(UpdateLaundryItemsFrom LaundryItemsFrom) {
        LaundryItems school = getById(LaundryItemsFrom.getItemId());
        BeanUtils.copyProperties(LaundryItemsFrom, school);
        return updateById(school);
    }

    //删除洗衣套餐
    @Override
    public Boolean deleteLaundryPackage(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }

    @Override
    public LaundryItems getSearchByName(String itemName) {
        if (!StringUtils.hasText(itemName)) return null;
        LambdaQueryWrapper<LaundryItems> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LaundryItems::getItemName, itemName);
        return getOne(queryWrapper);
    }
}
