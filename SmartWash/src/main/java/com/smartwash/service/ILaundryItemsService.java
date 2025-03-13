package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.LaundryItems;
import com.smartwash.from.laundry_item.AddLaundryItemsFrom;
import com.smartwash.from.laundry_item.SearchLaundryItemsFrom;
import com.smartwash.from.laundry_item.UpdateLaundryItemsFrom;
import com.smartwash.vo.laudry.LaundryPackageVo;
import jakarta.validation.Valid;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface ILaundryItemsService extends IService<LaundryItems> {

    Page<LaundryPackageVo> getAllLaundryPackage(SearchLaundryItemsFrom laundryPackageFrom);

    Boolean addLaundryPackage(AddLaundryItemsFrom addLaundryPackageFrom);

    Boolean updateLaundryPackage( UpdateLaundryItemsFrom laundryPackageFrom);

    Boolean deleteLaundryPackage(String ids);

    LaundryItems getSearchByName(String itemName);
}
