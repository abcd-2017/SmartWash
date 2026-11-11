package com.smartwash.divination.service;

import com.smartwash.divination.entity.DivModelConfig;
import com.smartwash.divination.entity.DivPlatformSetting;
import com.smartwash.divination.entity.DivUserApiConfig;
import com.smartwash.divination.from.SaveUserApiFrom;
import com.smartwash.divination.llm.OpenAiCompatProvider;
import com.smartwash.divination.mapper.DivUserApiConfigMapper;
import com.smartwash.divination.support.DivUrlSafetyValidator;
import com.smartwash.exception.CustomExceptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户模型配置（BYOK）服务单测：双模式保存（目录/自定义）+ 零兜底解析 + 只写不读掩码回显。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DivUserApiConfigServiceTest {

    @Mock
    private DivUserApiConfigMapper userApiConfigMapper;
    @Mock
    private AesGcmCipher cipher;
    @Mock
    private DivModelConfigService modelConfigService;
    @Mock
    private DivPlatformSettingService platformSettingService;
    @Mock
    private DivUrlSafetyValidator urlSafetyValidator;

    @InjectMocks
    private DivUserApiConfigService service;

    private DivPlatformSetting setting(boolean enabled) {
        DivPlatformSetting setting = new DivPlatformSetting();
        setting.setId(1L);
        setting.setDivUseEnabled(enabled ? 1 : 0);
        setting.setByokDailyLimit(30);
        return setting;
    }

    private DivModelConfig catalog(Long id, int enabled, String baseUrl, String modelId) {
        DivModelConfig config = new DivModelConfig();
        config.setId(id);
        config.setVendor("DeepSeek");
        config.setName("DeepSeek Flash");
        config.setBaseUrl(baseUrl);
        config.setModelId(modelId);
        config.setEnabled(enabled);
        return config;
    }

    // ==================== saveUserConfig 双模式 ====================

    @Test
    @DisplayName("功能总开关关闭时保存被拒且不写库")
    void saveRejectedWhenDivUseDisabled() {
        when(platformSettingService.getSetting()).thenReturn(setting(false));

        SaveUserApiFrom from = new SaveUserApiFrom();
        from.setModelConfigId(1L);
        from.setApiKey("sk-test");

        assertThatThrownBy(() -> service.saveUserConfig(16L, from))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("未开放");
        verify(userApiConfigMapper, never()).insert(any(DivUserApiConfig.class));
    }

    @Test
    @DisplayName("apiKey 缺失被拒（只写不读契约：每次必须显式提供 Key）")
    void saveRejectedWithoutApiKey() {
        when(platformSettingService.getSetting()).thenReturn(setting(true));

        SaveUserApiFrom from = new SaveUserApiFrom();
        from.setModelConfigId(1L);

        assertThatThrownBy(() -> service.saveUserConfig(16L, from))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("API Key");
    }

    @Test
    @DisplayName("目录模式：条目不存在/停用被拒")
    void saveRejectedWhenCatalogMissingOrDisabled() {
        when(platformSettingService.getSetting()).thenReturn(setting(true));
        when(modelConfigService.getById(999L)).thenReturn(null);
        when(modelConfigService.getById(2L)).thenReturn(catalog(2L, 0, "https://api.deepseek.com", "x"));

        SaveUserApiFrom missing = new SaveUserApiFrom();
        missing.setModelConfigId(999L);
        missing.setApiKey("sk-test");
        assertThatThrownBy(() -> service.saveUserConfig(16L, missing))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("重新选择");

        SaveUserApiFrom disabled = new SaveUserApiFrom();
        disabled.setModelConfigId(2L);
        disabled.setApiKey("sk-test");
        assertThatThrownBy(() -> service.saveUserConfig(16L, disabled))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("重新选择");
    }

    @Test
    @DisplayName("目录模式保存成功：modelConfigId 落库、密文加密、verified=1，试呼用目录接入点")
    void saveCatalogModeSuccess() {
        when(platformSettingService.getSetting()).thenReturn(setting(true));
        when(modelConfigService.getById(1L))
                .thenReturn(catalog(1L, 1, "https://api.deepseek.com", "deepseek-flash"));
        when(modelConfigService.testConnectivity("https://api.deepseek.com", "sk-plain", "deepseek-flash"))
                .thenReturn(true);
        when(cipher.encrypt("sk-plain", 1)).thenReturn("v1:iv:ct");
        when(cipher.mask("sk-plain")).thenReturn("sk-****lain");
        when(userApiConfigMapper.selectOne(any())).thenReturn(null);

        SaveUserApiFrom from = new SaveUserApiFrom();
        from.setModelConfigId(1L);
        from.setApiKey("sk-plain");

        assertThatCode(() -> service.saveUserConfig(16L, from)).doesNotThrowAnyException();

        ArgumentCaptor<DivUserApiConfig> captor = ArgumentCaptor.forClass(DivUserApiConfig.class);
        verify(userApiConfigMapper).insert(captor.capture());
        DivUserApiConfig saved = captor.getValue();
        assertThat(saved.getModelConfigId()).isEqualTo(1L);
        assertThat(saved.getApiKeyCipher()).isEqualTo("v1:iv:ct");
        assertThat(saved.getApiKeyMask()).isEqualTo("sk-****lain");
        assertThat(saved.getVerified()).isEqualTo(1);
        assertThat(saved.getCustomBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(saved.getCustomModel()).isEqualTo("deepseek-flash");
    }

    @Test
    @DisplayName("试呼不通过不写库")
    void saveRejectedWhenProbeFails() {
        when(platformSettingService.getSetting()).thenReturn(setting(true));
        when(modelConfigService.getById(1L))
                .thenReturn(catalog(1L, 1, "https://api.deepseek.com", "deepseek-flash"));
        when(modelConfigService.testConnectivity(anyString(), anyString(), anyString())).thenReturn(false);

        SaveUserApiFrom from = new SaveUserApiFrom();
        from.setModelConfigId(1L);
        from.setApiKey("sk-bad");

        assertThatThrownBy(() -> service.saveUserConfig(16L, from))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("连通性");
        verify(userApiConfigMapper, never()).insert(any(DivUserApiConfig.class));
        verify(userApiConfigMapper, never()).updateById(any(DivUserApiConfig.class));
    }

    @Test
    @DisplayName("自定义模式：SSRF 校验失败不试呼不写库")
    void saveCustomModeSsrfRejected() {
        when(platformSettingService.getSetting()).thenReturn(setting(true));
        org.mockito.Mockito.doThrow(new CustomExceptions("接入点不允许指向内网或本机地址"))
                .when(urlSafetyValidator).validate("https://192.168.1.1/v1");

        SaveUserApiFrom from = new SaveUserApiFrom();
        from.setBaseUrl("https://192.168.1.1/v1");
        from.setModel("m");
        from.setApiKey("sk-test");

        assertThatThrownBy(() -> service.saveUserConfig(16L, from))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        verify(modelConfigService, never()).testConnectivity(anyString(), anyString(), anyString());
        verify(userApiConfigMapper, never()).insert(any(DivUserApiConfig.class));
    }

    // ==================== resolveUserProvider 零兜底解析 ====================

    @Test
    @DisplayName("无配置返回 null（解读链路据此发 428）")
    void resolveNullWhenNoConfig() {
        when(userApiConfigMapper.selectOne(any())).thenReturn(null);
        assertThat(service.resolveUserProvider(16L)).isNull();
    }

    @Test
    @DisplayName("目录模式：实时取目录接入点与模型名，key 用用户自己的")
    void resolveCatalogMode() {
        DivUserApiConfig config = new DivUserApiConfig();
        config.setId(7L);
        config.setUserId(16L);
        config.setModelConfigId(1L);
        config.setApiKeyCipher("v1:iv:ct");
        config.setEnabled(1);
        config.setVerified(1);
        when(userApiConfigMapper.selectOne(any())).thenReturn(config);
        when(modelConfigService.getById(1L))
                .thenReturn(catalog(1L, 1, "https://api.deepseek.com", "deepseek-flash"));
        when(cipher.decrypt("v1:iv:ct")).thenReturn("sk-plain");

        OpenAiCompatProvider provider = service.resolveUserProvider(16L);
        assertThat(provider).isNotNull();
        assertThat(provider.getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(provider.getModel()).isEqualTo("deepseek-flash");
        assertThat(provider.getApiKey()).isEqualTo("sk-plain");
        assertThat(provider.getSource()).isEqualTo("user");
        assertThat(provider.getCircuitKey()).isEqualTo("user:7");
    }

    @Test
    @DisplayName("目录被停用/删除后不可用（零兜底，无平台回退）")
    void resolveNullWhenCatalogDisabledOrDeleted() {
        DivUserApiConfig config = new DivUserApiConfig();
        config.setId(7L);
        config.setModelConfigId(1L);
        config.setApiKeyCipher("v1:iv:ct");
        config.setEnabled(1);
        config.setVerified(1);
        when(userApiConfigMapper.selectOne(any())).thenReturn(config);

        when(modelConfigService.getById(1L)).thenReturn(catalog(1L, 0, "https://api.deepseek.com", "x"));
        assertThat(service.resolveUserProvider(16L)).isNull();

        when(modelConfigService.getById(1L)).thenReturn(null);
        assertThat(service.resolveUserProvider(16L)).isNull();
        verify(cipher, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("自定义模式：取快照接入点与模型名")
    void resolveCustomMode() {
        DivUserApiConfig config = new DivUserApiConfig();
        config.setId(8L);
        config.setCustomBaseUrl("https://custom.example.com/v1");
        config.setCustomModel("my-model");
        config.setApiKeyCipher("v1:iv:ct");
        config.setEnabled(1);
        config.setVerified(1);
        when(userApiConfigMapper.selectOne(any())).thenReturn(config);
        when(cipher.decrypt("v1:iv:ct")).thenReturn("sk-plain");

        OpenAiCompatProvider provider = service.resolveUserProvider(16L);
        assertThat(provider).isNotNull();
        assertThat(provider.getBaseUrl()).isEqualTo("https://custom.example.com/v1");
        assertThat(provider.getModel()).isEqualTo("my-model");
    }

    // ==================== getUserConfig 掩码回显 ====================

    @Test
    @DisplayName("目录模式回显 vendor/modelName 与掩码，无明文无密文")
    void getUserConfigMasksKey() {
        DivUserApiConfig config = new DivUserApiConfig();
        config.setId(7L);
        config.setModelConfigId(1L);
        config.setApiKeyMask("sk-****lain");
        config.setVerified(1);
        config.setEnabled(1);
        config.setCustomBaseUrl("https://api.deepseek.com");
        config.setCustomModel("deepseek-flash");
        when(userApiConfigMapper.selectOne(any())).thenReturn(config);
        when(platformSettingService.getSetting()).thenReturn(setting(true));
        when(modelConfigService.getById(1L)).thenReturn(catalog(1L, 1, "https://api.deepseek.com", "deepseek-flash"));

        com.smartwash.divination.vo.UserApiConfigVo vo = service.getUserConfig(16L);
        assertThat(vo.getVendor()).isEqualTo("DeepSeek");
        assertThat(vo.getModelName()).isEqualTo("DeepSeek Flash");
        assertThat(vo.getApiKeyMask()).isEqualTo("sk-****lain");
        assertThat(vo.getDivUseEnabled()).isEqualTo(1);
        // 只写不读红线：VO 类型本身无明文/密文字段（字段名单断言防未来误加）
        assertThat(java.util.Arrays.stream(vo.getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("apiKeyCipher", "apiKey", "apiKeyPlain");
    }
}
