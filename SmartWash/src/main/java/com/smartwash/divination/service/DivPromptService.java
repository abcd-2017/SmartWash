package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivPromptVersion;
import com.smartwash.divination.mapper.DivPromptVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Prompt 版本管理服务。
 * 版本化 + 灰度激活（同一 method 只有一个激活版本）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivPromptService {

    private final DivPromptVersionMapper promptVersionMapper;

    /**
     * 获取指定方法的激活 prompt 版本。
     */
    public DivPromptVersion getActivePrompt(String method) {
        LambdaQueryWrapper<DivPromptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivPromptVersion::getMethod, method)
                .eq(DivPromptVersion::getStatus, 1);
        wrapper.orderByDesc(DivPromptVersion::getCreatedAt);
        wrapper.last("LIMIT 1");
        return promptVersionMapper.selectOne(wrapper);
    }

    /**
     * 获取指定方法的 prompt 版本列表。
     */
    public List<DivPromptVersion> listByMethod(String method) {
        LambdaQueryWrapper<DivPromptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivPromptVersion::getMethod, method);
        wrapper.orderByDesc(DivPromptVersion::getCreatedAt);
        return promptVersionMapper.selectList(wrapper);
    }

    /**
     * 保存 prompt 版本。
     */
    public Long save(DivPromptVersion version) {
        if (version.getId() != null) {
            promptVersionMapper.updateById(version);
            return version.getId();
        }
        promptVersionMapper.insert(version);
        return version.getId();
    }

    /**
     * 激活指定版本（同 method 其他版本置为退役）。
     */
    public void activate(Long id) {
        DivPromptVersion version = promptVersionMapper.selectById(id);
        if (version == null) return;

        // 同 method 所有版本置为退役
        LambdaQueryWrapper<DivPromptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivPromptVersion::getMethod, version.getMethod());
        DivPromptVersion update = new DivPromptVersion();
        update.setStatus(2);
        promptVersionMapper.update(update, wrapper);

        // 指定版本激活
        version.setStatus(1);
        promptVersionMapper.updateById(version);
        log.info("激活 prompt 版本, id: {}, method: {}, version: {}", id, version.getMethod(), version.getVersion());
    }

    public void deleteById(Long id) {
        promptVersionMapper.deleteById(id);
    }
}
