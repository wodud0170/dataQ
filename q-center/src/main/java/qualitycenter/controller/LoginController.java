package qualitycenter.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.NDConstant;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.messaging.common.StompBrokerProperties;

import io.swagger.v3.oas.annotations.tags.Tag;
import qualitycenter.model.auth.AdminVo;
import qualitycenter.model.auth.UserVo;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "인증/로그인", description = "인증/로그인 API")
@Slf4j
@RestController
@RequestMapping("/api/login")
public class LoginController {

  @Autowired
  private StompBrokerProperties stompBrokerProperties;

  @Autowired
  private SessionService sessionService;

  @RequestMapping(value = "/getUser", method = RequestMethod.GET)
  public UserVo getUser() {
    return sessionService.getUser();
  }

  @RequestMapping(value = "/isAdmin", method = RequestMethod.GET)
  public boolean isAdmin(String user) {
    return sessionService.isAdmin(user);
  }

  @RequestMapping(value = "/checkSession", method = RequestMethod.GET)
  public boolean checkSession() {
    if (NDConstant.SVC_MODE_DEV.equals(stompBrokerProperties.getMode())) {
      return true;
    } else {
      return sessionService.checkSession();
    }
  }

  @RequestMapping(value = "/existAdmin", method = RequestMethod.GET)
  public boolean existAdmin() {
    return sessionService.existAdmin();
  }

  @RequestMapping(value = "/createAdmin", method = RequestMethod.GET)
  public Mono<Response> createAdmin(String password) {
    Response result = new Response();
		try {
			sessionService.createAdmin(password);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.error(">> create admin failed : ", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		
		return Mono.just(result);
  }

  @RequestMapping(value = "/changePassword", method = RequestMethod.GET)
  public Response changePassword(String user, String oldPassword, String newPassword) {
    return sessionService.changePassword(user, oldPassword, newPassword);
  }

  @RequestMapping(value = "/createUser", method = RequestMethod.POST)
  public Mono<Response> createUser(AdminVo user) throws Exception {
    log.info(">> create user={}", user);
    Response result = new Response();
		try {
      if (sessionService.existUser(user.getId())) {
        throw new Exception("이미 존재하는 사용자입니다(" + user.getId() + ")");
      }
			sessionService.createUser(user);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.error(">> create user failed : ", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		
		return Mono.just(result);
  }

  @RequestMapping(value = "/removeUser", method = RequestMethod.POST)
  public boolean removeUser(AdminVo user) throws Exception {
    log.info(">> remove user={}", user);
    return sessionService.removeUser(user);
  }

  @RequestMapping(value = "/updateUser", method = RequestMethod.POST)
  public Mono<Response> updateUser(AdminVo user) throws Exception {
    log.info(">> update user={}", user);
    Response result = new Response();
		try {
			sessionService.updateUser(user);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.error(">> update user failed : ", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		
		return Mono.just(result);
  }

  @RequestMapping(value = "/getUserList", method = RequestMethod.GET)
  public List<AdminVo> getUserList() throws Exception {
    return sessionService.getUserList();
  }
}
