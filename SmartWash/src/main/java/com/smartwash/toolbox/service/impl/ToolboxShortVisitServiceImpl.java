package com.smartwash.toolbox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.mapper.ToolboxShortVisitMapper;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import org.springframework.stereotype.Service;

@Service
public class ToolboxShortVisitServiceImpl extends ServiceImpl<ToolboxShortVisitMapper, ToolboxShortVisit>
        implements ToolboxShortVisitService {
}
