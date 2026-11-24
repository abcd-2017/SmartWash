package com.smartwash.dining.from;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 定向邀请入参（POST /web/auth/dining/orders/{id}/invites）。
 * inviteeIds 为被邀请人用户 ID 列表（协作过的用户中勾选）；单次上限 20 人。
 */
@Data
public class InviteDiningOrderFrom {

    @NotEmpty(message = "请至少选择一位要邀请的用户")
    @Size(max = 20, message = "单次最多邀请 20 人")
    private List<Long> inviteeIds;
}
