package com.smartwash.from.laundry_item;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchLaundryItemsFrom extends BaseSearchFrom {
    private Long itemId;

    private String itemName;
}
