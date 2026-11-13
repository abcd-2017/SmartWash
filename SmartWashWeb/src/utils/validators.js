// src/utils/validators.js
// 密码规则全站唯一来源：登录校验与创建/修改密码表单共用同一规则，
// 避免「创建时能通过、登录时进不去」的自造锁定（评审 P1）。
// 上限口径与后端登录校验一致；如后端调整须同步此处。
export const PASSWORD_MIN = 6;
export const PASSWORD_MAX = 16;

/** Element Plus 长度校验规则工厂；编辑场景「留空不改密」时 async-validator 对空值自动跳过 min/max */
export const passwordLengthRule = () => ({
  min: PASSWORD_MIN,
  max: PASSWORD_MAX,
  message: `密码长度在${PASSWORD_MIN}到${PASSWORD_MAX}个字符`,
  trigger: 'blur',
});
