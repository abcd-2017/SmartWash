package com.smartwash.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class BaseSearchFrom {
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Integer size = 10;
}
