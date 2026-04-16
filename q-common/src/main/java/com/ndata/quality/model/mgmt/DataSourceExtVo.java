package com.ndata.quality.model.mgmt;

import com.ndata.model.DataSourceVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceExtVo extends DataSourceVo {
    private String connTestYn;
    private String connTestDt;
}
