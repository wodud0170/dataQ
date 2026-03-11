package qualitycenter.model.auth;

import java.util.List;

import lombok.Data;

@Data
public class AdminVo {
    private String id;
    private String password;
    private String name;
    private String email;
    private boolean admin;
    private List<String> roles;
    private int authGrpId;
    private String createDate;
    private String createUserId;
    private String updateDate;
    private String updateUserId;
    private String blockTime;
}
