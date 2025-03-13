package com.smartwash.from.roles;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchRolesFrom extends BaseSearchFrom {
    private Integer roleId;

    private String roleName;
}
