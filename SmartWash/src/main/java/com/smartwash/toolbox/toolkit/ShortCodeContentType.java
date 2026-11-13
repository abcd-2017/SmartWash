package com.smartwash.toolbox.toolkit;

/**
 * 短码内核内容类型全集（toolbox_short_code.content_type）。
 *
 * <p>四端同步点：Android ShortCodeRules/ToolboxApi、鸿蒙 feature/toolbox/api.ets、Web dict.js。
 * 预留位（3/4/5）尚无实现；2 活码由储物柜资产码首消费；8 物品标签见 toolbox/assettag。</p>
 */
public final class ShortCodeContentType {

    public static final int SHORT_LINK = 1;
    public static final int LIVE_CODE = 2;
    public static final int DELIVERY_BOX = 3;
    public static final int IMAGE_HOST = 4;
    public static final int TIME_CAPSULE = 5;
    public static final int DEVICE_DELIVERY = 6;
    /** 私密文本（阅后即焚）：一次性揭示，成功即焚毁 */
    public static final int SECRET_TEXT = 7;
    public static final int ASSET_TAG = 8;

    private ShortCodeContentType() {
    }
}
