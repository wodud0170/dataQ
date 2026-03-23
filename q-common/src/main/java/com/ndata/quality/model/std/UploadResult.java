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

    public void addSuccess() {
        successCount++;
        totalCount++;
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
