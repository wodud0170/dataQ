package qualitycenter.model.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ndata.common.NDConstant;
import com.ndata.model.admin.RoleAuthorityVo;

import lombok.Data;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
public class UserVo implements UserDetails {

    private String id;
    private String name;

    @JsonIgnore
    private String password;
    private boolean admin;
    private String token;
    private List<RoleAuthorityVo> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        ArrayList<GrantedAuthority> auth = new ArrayList<>();
        auth.add(new SimpleGrantedAuthority(this.isAdmin() ? NDConstant.ROLE_ADMIN : NDConstant.ROLE_MEMBER));
        if (authorities != null) {
        	for (RoleAuthorityVo authority : authorities) {
        		auth.add(new SimpleGrantedAuthority(authority.getObjTp() + NDConstant.COLON + authority.getObjId() 
        							+ NDConstant.COLON + authority.getAccessRights()));
        	}
        }
        return auth;
    }

    @Override
    public String getUsername() {
        return id;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
