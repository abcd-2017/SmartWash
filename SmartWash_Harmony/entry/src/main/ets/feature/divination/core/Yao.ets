/**
 * 爻值统一编码与摇卦随机源。
 *
 * 蓝本：安卓 feature/divination/src/main/java/com/smartwash/feature/divination/core/Yao.kt
 *
 * 爻值编码（全模块约定）：1=少阳 2=少阴 3=老阳○(动) 4=老阴×(动)。
 */

import cryptoFramework from '@kit.CryptoArchitectureKit';

/** 爻值（1=少阳 2=少阴 3=老阳○(动) 4=老阴×(动)） */
export class YaoValue {
  readonly code: number;
  readonly label: string;
  readonly isMoving: boolean;
  readonly isYang: boolean;

  private constructor(code: number, label: string, isMoving: boolean, isYang: boolean) {
    this.code = code;
    this.label = label;
    this.isMoving = isMoving;
    this.isYang = isYang;
  }

  static readonly SHAO_YANG: YaoValue = new YaoValue(1, '少阳', false, true);
  static readonly SHAO_YIN: YaoValue = new YaoValue(2, '少阴', false, false);
  static readonly LAO_YANG: YaoValue = new YaoValue(3, '老阳', true, true);
  static readonly LAO_YIN: YaoValue = new YaoValue(4, '老阴', true, false);

  static readonly ALL: YaoValue[] = [
    YaoValue.SHAO_YANG, YaoValue.SHAO_YIN, YaoValue.LAO_YANG, YaoValue.LAO_YIN,
  ];

  /** 二进制爻位：阳 1 / 阴 0 */
  get binary(): number {
    return this.isYang ? 1 : 0;
  }

  /** 动而变：老阳变阴，老阴变阳，静爻不变 */
  transformedBinary(): number {
    if (this === YaoValue.LAO_YANG) {
      return 0;
    }
    if (this === YaoValue.LAO_YIN) {
      return 1;
    }
    return this.binary;
  }

  static fromCode(code: number): YaoValue {
    const found = YaoValue.ALL.find((v) => v.code === code);
    if (found === undefined) {
      throw new Error(`非法爻值: ${code}`);
    }
    return found;
  }
}

/** 随机源抽象（对应安卓 SecureRandom，便于单测注入确定序列） */
export interface RandomSource {
  nextBoolean(): boolean;
}

/**
 * 基于 @ohos.security.cryptoFramework 的加密安全随机源。
 * 单枚铜钱用 1 字节随机数的奇偶判定（true=背 3 / false=字 2）。
 */
export class CryptoRandomSource implements RandomSource {
  private readonly random: cryptoFramework.Random = cryptoFramework.createRandom();

  nextBoolean(): boolean {
    const blob: cryptoFramework.DataBlob = this.random.generateRandomSync(1);
    return blob.data[0] % 2 === 0;
  }
}

/**
 * 摇卦随机源 —— 三枚铜钱，字记 3、背记 2：
 * 和 6=老阴(三字) 7=少阳(一背) 8=少阴(二背) 9=老阳(三背)。
 * 随机源默认 cryptoFramework（等价安卓 SecureRandom），禁用可预测随机。
 */
export class CoinTosser {
  private readonly randomSource: RandomSource;

  constructor(randomSource?: RandomSource) {
    this.randomSource = randomSource === undefined ? new CryptoRandomSource() : randomSource;
  }

  /** 单枚铜钱：true=背(3) false=字(2) */
  tossCoin(): boolean {
    return this.randomSource.nextBoolean();
  }

  /** 一掷三币得爻值 */
  tossLine(): YaoValue {
    let backs = 0;
    for (let i = 0; i < 3; i++) {
      if (this.tossCoin()) {
        backs++;
      }
    }
    if (backs === 0) {
      return YaoValue.LAO_YIN;   // 6
    }
    if (backs === 1) {
      return YaoValue.SHAO_YANG; // 7
    }
    if (backs === 2) {
      return YaoValue.SHAO_YIN;  // 8
    }
    return YaoValue.LAO_YANG;    // 9
  }
}
