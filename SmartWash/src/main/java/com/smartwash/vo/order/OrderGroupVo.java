package com.smartwash.vo.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderGroupVo {
    private List<ShowOrderVo> items;
    private boolean hasMore;
    private int total;
}