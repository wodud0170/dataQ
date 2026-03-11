package com.ndata.quality.common;

import lombok.Getter;

public enum NDQualityApproveStat {
	REQUESTED      (0),
	CHECKING       (1),
	APPROVED       (2), 
	REJECTED       (3);
	
	@Getter
    private int value;

	NDQualityApproveStat(int value) {
        this.value = value;
    }    
}
