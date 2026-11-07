package com.smartwash.game.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.config.MinioConfig;
import com.smartwash.exception.GameServiceException;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.from.SearchGameFrom;
import com.smartwash.game.from.UpdateGameFrom;
import com.smartwash.game.from.UploadGameFrom;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.service.GameConfigService;
import com.smartwash.game.storage.GameObjectStorage;
import com.smartwash.game.support.GameContentTypes;
import com.smartwash.game.support.GamePackageValidator;
import com.smartwash.game.vo.AdminGameVo;
import com.smartwash.game.vo.GameVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 游戏库服务实现。
 * 缓存模式与 WorkbenchEntryServiceImpl 一致：读走 @Cacheable 的公共方法（经 self 自注入调用绕过 AOP 代理失效），
 * 写操作 @CacheEvict(allEntries) 全量失效；game_key 唯一性以 uk_game_key 唯一索引兜底并发。
 * 上传链路：校验 → 清旧前缀 → 解压/直写 MinIO（带缓存元数据）→ 建/更记录；删除链路：先清存储（fail-closed）再删记录。
 * 下发形态与头像先例一致：只落对象键，可玩地址读时拼 MinIO 直链（endpoint/bucket/对象键/入口），端上 WebView 直拉，服务侧零转发代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameConfigServiceImpl extends ServiceImpl<GameConfigMapper, GameConfig>
        implements GameConfigService {

    /** 用户端下发缓存空间名（TTL 见 RedisConfig） */
    public static final String CACHE_NAME = "gameList";

    /** 来源类型：1上传包 */
    private static final int SOURCE_TYPE_PACKAGE = 1;
    /** 来源类型：2外链 */
    private static final int SOURCE_TYPE_EXTERNAL = 2;

    /** MinIO 游戏对象根前缀 */
    private static final String OBJECT_ROOT = "game/";

    /** 封面上传大小上限（与工作台配图一致） */
    private static final long COVER_MAX_BYTES = 5L * 1024 * 1024;
    /** 允许的封面 MIME 类型 */
    private static final java.util.Set<String> COVER_CONTENT_TYPES =
            java.util.Set.of("image/png", "image/jpeg", "image/webp");

    /** 自注入：@Cacheable 方法须经代理调用才生效，同类内部直调会绕过 AOP */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private GameConfigServiceImpl self;

    private final GamePackageValidator packageValidator;
    private final GameObjectStorage objectStorage;
    private final MinioConfig minioConfig;
    private final com.smartwash.game.storage.GameCoverStorage gameCoverStorage;

    // ==================== 管理端：上传 ====================

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public AdminGameVo upload(UploadGameFrom from, MultipartFile file) {
        packageValidator.validateOriginalFile(file);
        String gameKey = from.getGameKey().trim();

        String filename = file.getOriginalFilename();
        String lowerName = filename == null ? "" : filename.toLowerCase();
        GameConfig config;
        if (lowerName.endsWith(".zip")) {
            config = storeZipPackage(gameKey, file, from);
        } else {
            config = storeSingleHtml(gameKey, file, from);
        }

        GameConfig existing = getByGameKey(gameKey);
        try {
            if (existing != null) {
                // 覆盖更新：保留 id/created_at，刷新包信息与元数据；
                // enabled/sort 沿用原值——热修包不该静默下架，排序由编辑接口调整
                config.setId(existing.getId())
                        .setCreatedAt(existing.getCreatedAt())
                        .setEnabled(existing.getEnabled())
                        .setSort(existing.getSort());
                this.updateById(config);
            } else {
                this.save(config);
            }
        } catch (DuplicateKeyException e) {
            throw new GameServiceException(GameServiceException.CONFLICT, "gameKey 已存在，请更换标识后重试");
        }
        log.info("上传游戏, gameKey: {}, title: {}, 覆盖更新: {}, 包字节: {}",
                gameKey, from.getTitle(), existing != null, config.getFileSize());
        return buildAdminVo(this.getById(config.getId()));
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public String uploadCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "请选择要上传的封面图");
        }
        if (file.getSize() > COVER_MAX_BYTES) {
            throw new GameServiceException(GameServiceException.PAYLOAD_TOO_LARGE, "封面大小不能超过5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (!COVER_CONTENT_TYPES.contains(contentType)) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "仅支持 png / jpeg / webp 图片");
        }
        try {
            return gameCoverStorage.putImage(file.getBytes(), contentType);
        } catch (GameServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("游戏封面上传失败", e);
            throw new GameServiceException(GameServiceException.INTERNAL_ERROR, "封面上传失败，请稍后重试");
        }
    }

    /** 单 html：固定落为 {gameKey}/index.html，入口即 index.html；内容读取成功后才清旧前缀，防半途失败丢旧包。
     * 上传安全：内容黑名单扫描（命中 403）+ 入口 meta CSP 注入（纵深防御） */
    private GameConfig storeSingleHtml(String gameKey, MultipartFile file, UploadGameFrom from) {
        try {
            byte[] raw = file.getBytes();
            packageValidator.scanHtmlContent(new String(raw, StandardCharsets.UTF_8));
            byte[] content = packageValidator
                    .applyCspMarkup(new String(raw, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
            removeOldPrefix(OBJECT_ROOT + gameKey + "/");
            String objectKey = OBJECT_ROOT + gameKey + "/index.html";
            objectStorage.putObject(objectKey, content, GameContentTypes.TEXT_HTML, GameObjectStorage.CACHE_CONTROL_IMMUTABLE);
            return buildPackageConfig(gameKey, from, "index.html", (long) content.length);
        } catch (java.io.IOException e) {
            log.error("游戏单文件读取失败, gameKey: {}", gameKey, e);
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "上传文件读取失败，请重试");
        }
    }

    /** zip：解压到临时目录（防炸弹 + ZipSlip 校验）→ 探测入口 → 清旧前缀 → 逐文件上传 → 清理临时目录 */
    private GameConfig storeZipPackage(String gameKey, MultipartFile file, UploadGameFrom from) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("smartwash-game-");
            GamePackageValidator.ExtractResult result;
            try (InputStream in = file.getInputStream()) {
                result = packageValidator.extractZip(in, tempDir);
            }
            String entryPath = packageValidator.resolveEntryPath(tempDir);
            if (StrUtil.isBlank(entryPath)) {
                throw new GameServiceException(GameServiceException.BAD_REQUEST,
                        "压缩包根目录缺少 html 入口文件（index.html）");
            }
            // 上传安全：全包 html 黑名单扫描（命中 403）+ 入口 meta CSP 注入（纵深防御）
            packageValidator.scanExtractedPackage(tempDir);
            packageValidator.injectEntryCsp(tempDir, entryPath);
            removeOldPrefix(OBJECT_ROOT + gameKey + "/");
            uploadDirectory(gameKey, tempDir);
            return buildPackageConfig(gameKey, from, entryPath, result.totalBytes());
        } catch (java.io.IOException e) {
            log.error("游戏 zip 解压临时目录创建失败, gameKey: {}", gameKey, e);
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "压缩包解析失败，请稍后重试");
        } finally {
            deleteTempDirQuietly(tempDir);
        }
    }

    /** 临时目录内容逐文件上传：对象键 = game/{gameKey}/{相对路径}，Content-Type 按扩展名 */
    private void uploadDirectory(String gameKey, Path tempDir) throws java.io.IOException {
        String prefix = OBJECT_ROOT + gameKey + "/";
        try (var walk = Files.walk(tempDir)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String relative = tempDir.relativize(path).toString().replace('\\', '/');
                byte[] content = Files.readAllBytes(path);
                objectStorage.putObject(prefix + relative, content, GameContentTypes.forFilename(relative),
                        GameObjectStorage.CACHE_CONTROL_IMMUTABLE);
            }
        }
    }

    /** 组装上传包形态的记录（只落 objectKey + entryPath，可玩地址读时拼装，见 playUrlOf） */
    private GameConfig buildPackageConfig(String gameKey, UploadGameFrom from, String entryPath, Long fileSize) {
        GameConfig config = new GameConfig()
                .setGameKey(gameKey)
                .setTitle(from.getTitle())
                .setDescription(StrUtil.isBlank(from.getDescription()) ? null : from.getDescription())
                .setIcon(StrUtil.isBlank(from.getIcon()) ? "default" : from.getIcon())
                .setColor(StrUtil.isBlank(from.getColor()) ? "green" : from.getColor())
                .setCategory(StrUtil.isBlank(from.getCategory()) ? "puzzle" : from.getCategory())
                .setSourceType(SOURCE_TYPE_PACKAGE)
                .setEntryPath(entryPath)
                .setObjectKey(OBJECT_ROOT + gameKey + "/")
                .setFileSize(fileSize)
                .setBadge(StrUtil.isBlank(from.getBadge()) ? null : from.getBadge())
                .setCoverUrl(normalizeCover(from.getCoverUrl()))
                // 新增游戏默认不下发，是否上架由开关接口显式选择（覆盖上传保留原值，见 upload）
                .setEnabled(0)
                .setSort(from.getSort() == null ? 100 : from.getSort())
                .setRemark(StrUtil.isBlank(from.getRemark()) ? null : from.getRemark());
        // 不再落库 url：objectKey + entryPath 已持久化，可玩地址读时拼装（端点变更不必刷库）
        return config;
    }

    /**
     * 可玩地址读时拼装：source_type=1 由 object_key + entry_path 推导；
     * source_type=2 为外部 https 地址，原样下发（不可当对象键重写）。
     */
    private String playUrlOf(GameConfig config) {
        if (config.getSourceType() != null && config.getSourceType() == SOURCE_TYPE_EXTERNAL) {
            return config.getUrl();
        }
        String objectKey = config.getObjectKey();
        if (StrUtil.isBlank(objectKey)) {
            return StrUtil.nullToEmpty(config.getUrl());
        }
        return minioConfig.urlOf(objectKey + StrUtil.nullToEmpty(config.getEntryPath()));
    }

    /**
     * 封面归一化为对象键：兼容管理端回填的完整直链（换域名历史数据）与手工填的键。
     * 存键不存全 URL，换 MinIO 端点不必刷库。
     */
    private String normalizeCover(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return minioConfig.toObjectKey(value);
    }

    /** 封面对象键 → 公共读直链（下发/管理视图用） */
    private String coverUrlOf(GameConfig config) {
        return StrUtil.isBlank(config.getCoverUrl()) ? null : minioConfig.urlOf(config.getCoverUrl());
    }

    // ==================== 管理端：分页 / 编辑 / 开关 / 删除 ====================

    @Override
    public Page<AdminGameVo> adminPage(SearchGameFrom from) {
        LambdaQueryWrapper<GameConfig> wrapper = new LambdaQueryWrapper<>();
        boolean hasKeyword = StringUtils.hasText(from.getKeyword());
        wrapper.and(hasKeyword, w -> w.like(GameConfig::getTitle, from.getKeyword())
                .or().like(GameConfig::getGameKey, from.getKeyword()));
        wrapper.eq(StringUtils.hasText(from.getCategory()), GameConfig::getCategory, from.getCategory());
        wrapper.eq(from.getEnabled() != null, GameConfig::getEnabled, from.getEnabled());
        // 与用户端渲染顺序一致（sort 升序、id 升序），后台所见即端上所得
        wrapper.orderByAsc(GameConfig::getSort).orderByAsc(GameConfig::getId);

        Page<GameConfig> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Page<AdminGameVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::buildAdminVo).toList());
        return voPage;
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void updateGame(Long id, UpdateGameFrom from) {
        GameConfig config = this.getById(id);
        if (config == null) {
            throw new GameServiceException(GameServiceException.NOT_FOUND, "游戏不存在");
        }
        // 仅覆盖传入字段（MyBatis-Plus updateById 跳过 null 列）；game_key 为端上与 URL 对齐标识，不允许修改
        if (StringUtils.hasText(from.getTitle())) {
            config.setTitle(from.getTitle());
        }
        if (from.getDescription() != null) {
            config.setDescription(from.getDescription().isEmpty() ? null : from.getDescription());
        }
        if (StrUtil.isNotBlank(from.getIcon())) {
            config.setIcon(from.getIcon());
        }
        if (StrUtil.isNotBlank(from.getColor())) {
            config.setColor(from.getColor());
        }
        if (StrUtil.isNotBlank(from.getCategory())) {
            config.setCategory(from.getCategory());
        }
        if (from.getBadge() != null) {
            config.setBadge(from.getBadge().isEmpty() ? null : from.getBadge());
        }
        if (from.getCoverUrl() != null) {
            config.setCoverUrl(from.getCoverUrl().isEmpty() ? null : normalizeCover(from.getCoverUrl()));
        }
        if (from.getSort() != null) {
            config.setSort(from.getSort());
        }
        if (from.getRemark() != null) {
            config.setRemark(from.getRemark().isEmpty() ? null : from.getRemark());
        }
        this.updateById(config);
        log.info("更新游戏元数据, id: {}, gameKey: {}", id, config.getGameKey());
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void changeEnabled(Long id, Integer enabled) {
        // CAS 条件更新：仅当当前状态与目标不同才生效，判影响行数（资金/状态操作硬性约束同款语义）
        boolean updated = this.update(new LambdaUpdateWrapper<GameConfig>()
                .eq(GameConfig::getId, id)
                .ne(GameConfig::getEnabled, enabled)
                .set(GameConfig::getEnabled, enabled));
        if (!updated) {
            // 0 行 = 不存在或已是目标状态；查一次区分，不存在给 404，已一致则幂等成功
            if (this.getById(id) == null) {
                throw new GameServiceException(GameServiceException.NOT_FOUND, "游戏不存在");
            }
            log.info("游戏开关幂等命中, id: {}, enabled: {}", id, enabled);
        }
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteGame(Long id) {
        GameConfig config = this.getById(id);
        if (config == null) {
            throw new GameServiceException(GameServiceException.NOT_FOUND, "游戏不存在");
        }
        // 先清存储（fail-closed：清理失败抛错阻断删除，可重试；防记录删了对象永久泄漏）
        if (config.getSourceType() != null && config.getSourceType() == SOURCE_TYPE_PACKAGE
                && StrUtil.isNotBlank(config.getObjectKey())) {
            objectStorage.removeObjectsByPrefix(config.getObjectKey());
        }
        this.removeById(id);
        log.info("删除游戏, id: {}, gameKey: {}, 已清理对象前缀: {}",
                id, config.getGameKey(), config.getObjectKey());
    }

    // ==================== 用户端 ====================

    @Override
    public List<GameVo> listEnabledGames() {
        return self.enabledGamesFromCache();
    }

    /**
     * 启用中的游戏列表（缓存命中路径）。
     * 仅 enabled=1，按 sort 升序、id 升序，不分页（规模 <100）；缓存失效由管理端写操作统一处理。
     */
    @Cacheable(value = CACHE_NAME, key = "'all'")
    public List<GameVo> enabledGamesFromCache() {
        // 排序语义（sort 升序、id 升序）由 XML 的 ORDER BY 保证，此处不二次排序
        return this.getBaseMapper().selectEnabledList().stream().map(this::buildVo).toList();
    }

    // ==================== 内部方法 ====================

    /** 覆盖上传前清理旧前缀（fail-closed：失败中止上传，防新旧包混挂）；对象不存在时存储层幂等静默 */
    private void removeOldPrefix(String prefix) {
        // 存储层已统一抛 GameServiceException（500 语义），此处直接穿透由全局 advice 转信封
        objectStorage.removeObjectsByPrefix(prefix);
    }

    private GameConfig getByGameKey(String gameKey) {
        // 直查 baseMapper（与 workbench/toolbox 内核一致），单测 mock 路径确定
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<GameConfig>()
                .eq(GameConfig::getGameKey, gameKey));
    }

    private void deleteTempDirQuietly(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (java.io.IOException e) {
                    // 临时目录清理尽力而为，系统临时目录兜底，debug 留痕即可
                    log.debug("游戏包临时文件删除失败, path: {}, 原因: {}", path, e.getMessage());
                }
            });
        } catch (java.io.IOException e) {
            // 同上：清理失败不阻断主流程
            log.debug("游戏包临时目录清理失败, tempDir: {}, 原因: {}", tempDir, e.getMessage());
        }
    }

    private AdminGameVo buildAdminVo(GameConfig config) {
        AdminGameVo vo = new AdminGameVo();
        BeanUtils.copyProperties(config, vo);
        vo.setUrl(playUrlOf(config));
        vo.setCoverUrl(coverUrlOf(config));
        return vo;
    }

    private GameVo buildVo(GameConfig config) {
        GameVo vo = new GameVo();
        BeanUtils.copyProperties(config, vo);
        vo.setUrl(playUrlOf(config));
        vo.setCoverUrl(coverUrlOf(config));
        return vo;
    }
}
