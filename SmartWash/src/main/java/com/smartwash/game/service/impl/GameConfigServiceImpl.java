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

import org.springframework.web.util.UriUtils;

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
 * 下发形态与头像先例一致：url 存 MinIO 对象直链（endpoint/bucket/对象键/入口），端上 WebView 直拉，服务侧零转发代码。
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

    /** 自注入：@Cacheable 方法须经代理调用才生效，同类内部直调会绕过 AOP */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private GameConfigServiceImpl self;

    private final GamePackageValidator packageValidator;
    private final GameObjectStorage objectStorage;
    private final MinioConfig minioConfig;

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
                // 覆盖更新：保留 id/created_at，刷新包信息与元数据
                config.setId(existing.getId())
                        .setCreatedAt(existing.getCreatedAt());
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

    /** 单 html：固定落为 {gameKey}/index.html，入口即 index.html；内容读取成功后才清旧前缀，防半途失败丢旧包 */
    private GameConfig storeSingleHtml(String gameKey, MultipartFile file, UploadGameFrom from) {
        try {
            byte[] content = file.getBytes();
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

    /** 组装上传包形态的记录（url = MinIO 对象直链，指向入口文件，与头像 uploadFile 同一构造方式） */
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
                // 上传后默认不下发，是否上架由开关接口显式选择
                .setEnabled(0)
                .setSort(from.getSort() == null ? 100 : from.getSort())
                .setRemark(StrUtil.isBlank(from.getRemark()) ? null : from.getRemark());
        config.setUrl(buildPlayUrl(OBJECT_ROOT + gameKey + "/", entryPath));
        return config;
    }

    /**
     * 可玩地址 = MinIO 对象直链：{endpoint}/{bucket}/{objectKey}{entryPath}，
     * 对齐 MinioStorageServiceImpl.uploadFile 的 URL 构造（endpoint 直接拼接）；对象路径段 URL 编码兜底（zip 条目名可能含空格/中文）。
     */
    private String buildPlayUrl(String objectKey, String entryPath) {
        String endpoint = minioConfig.getEndpoint();
        String base = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
        return base + "/" + minioConfig.getBucketName() + "/"
                + UriUtils.encodePath(objectKey + entryPath, StandardCharsets.UTF_8);
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
                } catch (java.io.IOException ignore) {
                    // 临时目录清理尽力而为，系统临时目录兜底
                }
            });
        } catch (java.io.IOException ignore) {
            // 同上
        }
    }

    private AdminGameVo buildAdminVo(GameConfig config) {
        AdminGameVo vo = new AdminGameVo();
        BeanUtils.copyProperties(config, vo);
        return vo;
    }

    private GameVo buildVo(GameConfig config) {
        GameVo vo = new GameVo();
        BeanUtils.copyProperties(config, vo);
        return vo;
    }
}
