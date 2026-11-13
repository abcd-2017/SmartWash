package com.smartwash.toolbox.assettag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.crypto.ToolboxGcmCipher;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.toolbox.assettag.entity.ToolboxAssetTag;
import com.smartwash.toolbox.assettag.from.CreateAssetTagFrom;
import com.smartwash.toolbox.assettag.from.UpdateAssetTagFrom;
import com.smartwash.toolbox.assettag.mapper.ToolboxAssetTagMapper;
import com.smartwash.toolbox.assettag.vo.AssetTagPublicVo;
import com.smartwash.toolbox.assettag.vo.AssetTagVo;
import com.smartwash.toolbox.assettag.service.ToolboxAssetTagService;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import com.smartwash.toolbox.toolkit.ShortCodeContentType;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
import com.smartwash.toolbox.vo.ShortCodeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物品标签服务实现。
 * 隐私红线：联系方式静态加密；明文仅「寻物中 + 已留」时解密下发；任何端/管理端不回显密文与明文。
 * 标签状态裁定时在后端（端上只做按钮可见性，对齐订阅模块惯例）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolboxAssetTagServiceImpl implements ToolboxAssetTagService {

    /** 业务码：404（标签不存在/归属不符，不暴露差异） */
    private static final int CODE_NOT_FOUND = 404;

    /** 密钥版本（TOOLBOX_MASTER_KEY 当前代，与私密文本同代） */
    private static final int TAG_KEY_VERSION = 1;

    /** 标签状态：0 正常（联系方式不下发）/ 1 寻物中（下发）/ 2 已寻回（不下发） */
    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_LOST = 1;
    private static final int STATUS_FOUND = 2;

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ToolboxShortCodeMapper toolboxShortCodeMapper;
    private final ToolboxShortVisitService toolboxShortVisitService;
    private final ToolboxAssetTagMapper assetTagMapper;
    private final ToolboxGcmCipher toolboxGcmCipher;
    private final WebBaseUrlResolver webBaseUrlResolver;

    @Override
    @Transactional
    public AssetTagVo createAssetTag(CreateAssetTagFrom from, Long ownerUserId) {
        AddShortCodeFrom shortForm = new AddShortCodeFrom();
        shortForm.setContentType(ShortCodeContentType.ASSET_TAG);
        shortForm.setCustomCode(StrUtil.blankToDefault(from.getCustomCode(), null));
        shortForm.setIsPublic(1);
        // 标签默认永久（expireAt 不传）；过期能力随内核保留
        ShortCodeVo shortVo = toolboxShortCodeService.createShortCode(shortForm, ownerUserId);

        ToolboxAssetTag tag = new ToolboxAssetTag()
                .setCode(shortVo.getCode())
                .setItemName(from.getItemName().trim())
                .setItemDesc(StrUtil.blankToDefault(from.getItemDesc(), null))
                .setStatus(STATUS_NORMAL)
                .setContactCipher(encryptContact(from.getContact()));
        assetTagMapper.insert(tag);
        log.info("物品标签创建成功, code: {}, ownerUserId: {}", shortVo.getCode(), ownerUserId);
        return buildVo(shortVo, tag);
    }

    @Override
    public IPage<AssetTagVo> pageMyAssetTags(long page, long size, Long ownerUserId) {
        IPage<ToolboxShortCode> mainPage = toolboxShortCodeService.page(new Page<>(page, size),
                new LambdaQueryWrapper<ToolboxShortCode>()
                        .eq(ToolboxShortCode::getOwnerUserId, ownerUserId)
                        .eq(ToolboxShortCode::getContentType, ShortCodeContentType.ASSET_TAG)
                        .orderByDesc(ToolboxShortCode::getCreatedAt));
        // 明细按 code 批量取（一次 IN，无 N+1）
        Set<String> codes = mainPage.getRecords().stream()
                .map(ToolboxShortCode::getCode)
                .collect(Collectors.toSet());
        Map<String, ToolboxAssetTag> tagMap = codes.isEmpty()
                ? Map.of()
                : assetTagMapper.selectList(new LambdaQueryWrapper<ToolboxAssetTag>()
                        .in(ToolboxAssetTag::getCode, codes)).stream()
                .collect(Collectors.toMap(ToolboxAssetTag::getCode, Function.identity()));

        Page<AssetTagVo> voPage = new Page<>(mainPage.getCurrent(), mainPage.getSize(), mainPage.getTotal());
        voPage.setRecords(mainPage.getRecords().stream()
                .map(main -> buildVo(main, tagMap.get(main.getCode())))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public AssetTagVo updateAssetTag(Long id, UpdateAssetTagFrom from, Long ownerUserId) {
        ToolboxShortCode main = getOwnedMain(id, ownerUserId);
        ToolboxAssetTag tag = requireDetail(main.getCode());
        applyUpdate(tag, from);
        return buildVo(main, tag);
    }

    @Override
    @Transactional
    public void deleteAssetTag(Long id, Long ownerUserId) {
        ToolboxShortCode main = getOwnedMain(id, ownerUserId);
        cascadeRemove(main);
        log.info("物品标签已删除, code: {}, ownerUserId: {}", main.getCode(), ownerUserId);
    }

    @Override
    @Transactional
    public void adminDelete(Long id) {
        ToolboxShortCode main = toolboxShortCodeMapper.selectById(id);
        if (main == null || ShortCodeContentType.ASSET_TAG != main.getContentType()) {
            throw new CustomExceptions(CODE_NOT_FOUND, "标签不存在");
        }
        cascadeRemove(main);
        log.info("管理端删除物品标签, code: {}", main.getCode());
    }

    @Override
    public AssetTagPublicVo resolvePublic(String code) {
        ToolboxShortCode main = getByCode(code);
        // 主表缺失/非标签类型/已过期/私有异动 → 一律 404（不暴露差异；标签恒公开由创建侧保证）
        if (main == null || ShortCodeContentType.ASSET_TAG != main.getContentType()) {
            throw new CustomExceptions(CODE_NOT_FOUND, "标签不存在或已失效");
        }
        if (main.getExpireAt() != null && main.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new CustomExceptions(CODE_NOT_FOUND, "标签不存在或已失效");
        }
        ToolboxAssetTag tag = assetTagMapper.selectOne(new LambdaQueryWrapper<ToolboxAssetTag>()
                .eq(ToolboxAssetTag::getCode, code));
        if (tag == null) {
            // 主表在而明细悬挂（管理端直删主表的历史数据）：按失效处理
            throw new CustomExceptions(CODE_NOT_FOUND, "标签不存在或已失效");
        }
        // 扫码线索：仅时间与计数（IP 摘要走访问明细事件，由控制器发布）
        assetTagMapper.update(null, new LambdaUpdateWrapper<ToolboxAssetTag>()
                .eq(ToolboxAssetTag::getId, tag.getId())
                .set(ToolboxAssetTag::getLastScannedAt, LocalDateTime.now()));

        AssetTagPublicVo vo = new AssetTagPublicVo()
                .setItemName(tag.getItemName())
                .setItemDesc(tag.getItemDesc())
                .setStatus(tag.getStatus());
        // 隐私矩阵：仅「寻物中 + 已留联系方式」解密下发
        if (tag.getStatus() != null && tag.getStatus() == STATUS_LOST && StrUtil.isNotBlank(tag.getContactCipher())) {
            vo.setHasContact(true);
            vo.setContact(toolboxGcmCipher.decrypt(tag.getContactCipher()));
        }
        return vo;
    }

    // ==================== 内部方法 ====================

    private ToolboxShortCode getOwnedMain(Long id, Long ownerUserId) {
        ToolboxShortCode main = toolboxShortCodeMapper.selectById(id);
        if (main == null || !main.getOwnerUserId().equals(ownerUserId)
                || ShortCodeContentType.ASSET_TAG != main.getContentType()) {
            // 归属不符/类型不符一律 404，不暴露存在性（对齐短码 resolveForOwner 口径）
            throw new CustomExceptions(CODE_NOT_FOUND, "标签不存在");
        }
        return main;
    }

    private ToolboxAssetTag requireDetail(String code) {
        ToolboxAssetTag tag = assetTagMapper.selectOne(new LambdaQueryWrapper<ToolboxAssetTag>()
                .eq(ToolboxAssetTag::getCode, code));
        if (tag == null) {
            throw new CustomExceptions(CODE_NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    private void applyUpdate(ToolboxAssetTag tag, UpdateAssetTagFrom from) {
        if (from.getItemName() != null && StrUtil.isNotBlank(from.getItemName())) {
            tag.setItemName(from.getItemName().trim());
        }
        if (from.getItemDesc() != null) {
            tag.setItemDesc(StrUtil.blankToDefault(from.getItemDesc(), null));
        }
        if (from.getContact() != null) {
            // 空串 = 清空联系方式；非空重新加密
            tag.setContactCipher(StrUtil.isBlank(from.getContact()) ? null : encryptContact(from.getContact()));
        }
        if (from.getStatus() != null) {
            tag.setStatus(from.getStatus());
        }
        assetTagMapper.updateById(tag);
    }

    /** 级联删除：明细 + 访问明细 + 主表（对齐短码删除「跟随删除访问明细」口径） */
    private void cascadeRemove(ToolboxShortCode main) {
        assetTagMapper.delete(new LambdaQueryWrapper<ToolboxAssetTag>()
                .eq(ToolboxAssetTag::getCode, main.getCode()));
        toolboxShortVisitService.remove(new LambdaQueryWrapper<com.smartwash.toolbox.entity.ToolboxShortVisit>()
                .eq(com.smartwash.toolbox.entity.ToolboxShortVisit::getCode, main.getCode()));
        toolboxShortCodeService.removeById(main.getId());
    }

    private String encryptContact(String contact) {
        return StrUtil.isBlank(contact) ? null : toolboxGcmCipher.encrypt(contact, TAG_KEY_VERSION);
    }

    private String tagUrlOf(String code) {
        String base = webBaseUrlResolver.resolve();
        if (StrUtil.isBlank(base)) {
            return "";
        }
        return StrUtil.removeSuffix(base, "/") + "/tag/" + code;
    }

    private AssetTagVo buildVo(ToolboxShortCode main, ToolboxAssetTag tag) {
        return new AssetTagVo()
                .setId(main.getId())
                .setCode(main.getCode())
                .setItemName(tag == null ? null : tag.getItemName())
                .setItemDesc(tag == null ? null : tag.getItemDesc())
                .setStatus(tag == null ? STATUS_NORMAL : tag.getStatus())
                .setHasContact(tag != null && StrUtil.isNotBlank(tag.getContactCipher()))
                .setTagUrl(tagUrlOf(main.getCode()))
                .setScanCount(main.getClickCount())
                .setLastScannedAt(tag == null ? null : tag.getLastScannedAt())
                .setCreatedAt(main.getCreatedAt());
    }

    /** 创建即时回执：主记录尚未回查，以 createShortCode 返回的 ShortCodeVo 构造（点击数/创建时间由列表页承载） */
    private AssetTagVo buildVo(ShortCodeVo shortVo, ToolboxAssetTag tag) {
        return buildVo(new ToolboxShortCode()
                .setId(shortVo.getId())
                .setCode(shortVo.getCode())
                .setClickCount(0L), tag);
    }

    private ToolboxShortCode getByCode(String code) {
        return toolboxShortCodeMapper.selectOne(new LambdaQueryWrapper<ToolboxShortCode>()
                .eq(ToolboxShortCode::getCode, code));
    }
}
