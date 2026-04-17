package com.ndata.quality.common;

import lombok.Getter;

public enum NDQualityStdObjectType {
	WORD      ("WORD"),
	TERMS       ("TERMS"),
	DOMAIN       ("DOMAIN"),
	CODE        ("CODE");
	
	@Getter
    private String value;

	NDQualityStdObjectType(String value) {
        this.value = value;
    }    
}