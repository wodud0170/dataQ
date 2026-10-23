package com.ndata.quality.model.std;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class UploadResult {
    private int totalCount;
    private int successCount;
    private int skipCount;
    private int failCount;
    private List<String> failDetails = new ArrayList<>();
    /** 110번 3.12-7-e — 등록된 행 {id, 이름} (일괄등록 변경 이력의 상세 건) */
    private List<String[]> successItems = new ArrayList<>();

    public void addSuccess() {
        successCount++;
        totalCount++;
    }

    public void addSuccess(String id, String nm) {
        addSuccess();
        successItems.add(new String[] { id, nm });
    }

    public void addSkip() {
        skipCount++;
        totalCount++;
    }

    private static final int MAX_FAIL_DETAILS = 100;

    public void addFail(String detail) {
        failCount++;
        totalCount++;
        if (failDetails.size() < MAX_FAIL_DETAILS) {
            failDetails.add(detail);
        }
    }
}
