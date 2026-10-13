package qualitycenter.model.auth;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class AdminVo {
    private String id;
    /** 요청(사용자 등록·수정)에서만 받는다. 응답에는 싣지 않는다 — 저장된 해시가 사용자 상세로 나가지 않게. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String name;
    private String email;
    private String phone;
    private boolean admin;
    private List<String> roles;
    private int authGrpId;
    private String createDate;
    private String createUserId;
    private String updateDate;
    private String updateUserId;
    private String blockTime;
    /** 104번 WP1 — 부서 (조회 전용, 바꾸는 것은 /api/org/assignUser) */
    private String orgId;
    private String orgNm;
}
