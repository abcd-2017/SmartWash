package com.smartwash.from.admin_users;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchAdminUserFrom extends BaseSearchFrom {
    private Long adminId;

    private String username;

    private Long roleId;
}
