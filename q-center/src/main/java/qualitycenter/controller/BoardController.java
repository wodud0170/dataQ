package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

@Tag(name = "게시판", description = "커뮤니티 게시판 API")
@Slf4j
@RestController
@RequestMapping("/api/board")
public class BoardController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	/**
	 * 게시판 목록 조회
	 */
	@PostMapping("/list")
	public Map<String, Object> getBoardList(@RequestBody Map<String, Object> params) {
		log.info(">> getBoardList : {}", params);
		Map<String, Object> result = new HashMap<>();

		if (params.get("limit") == null) params.put("limit", 20);
		if (params.get("offset") == null) params.put("offset", 0);

		List<Map<String, Object>> list = sqlSessionTemplate.selectList("board.selectBoardList", params);
		int totalCount = sqlSessionTemplate.selectOne("board.selectBoardCount", params);

		result.put("list", list);
		result.put("totalCount", totalCount);
		return result;
	}

	/**
	 * 게시글 상세 조회
	 */
	@GetMapping("/{boardId}")
	public Map<String, Object> getBoardById(@PathVariable String boardId) {
		log.info(">> getBoardById : {}", boardId);
		// 조회수 증가
		sqlSessionTemplate.update("board.incrementViewCnt", boardId);
		Map<String, Object> board = sqlSessionTemplate.selectOne("board.selectBoardById", boardId);
		return board != null ? board : new HashMap<>();
	}

	/**
	 * 게시글 등록
	 */
	@PostMapping("/create")
	public Response createBoard(@RequestBody Map<String, Object> params) {
		log.info(">> createBoard : {}", params);
		Response result = new Response();
		try {
			String boardType = (String) params.get("boardType");
			// 공지사항은 관리자만 작성 가능
			if ("NOTICE".equals(boardType) && !sessionService.isAdmin()) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "공지사항은 관리자만 작성할 수 있습니다.");
				return result;
			}
			params.put("boardId", StringUtils.getUUID());
			params.put("cretUserId", sessionService.getUserId());
			sqlSessionTemplate.insert("board.insertBoard", params);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("createBoard error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/**
	 * 게시글 수정
	 */
	@PostMapping("/update")
	public Response updateBoard(@RequestBody Map<String, Object> params) {
		log.info(">> updateBoard : {}", params);
		Response result = new Response();
		try {
			sqlSessionTemplate.update("board.updateBoard", params);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("updateBoard error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/**
	 * 게시글 삭제
	 */
	@PostMapping("/delete")
	public Response deleteBoard(@RequestBody Map<String, Object> params) {
		log.info(">> deleteBoard : {}", params);
		Response result = new Response();
		try {
			String boardId = (String) params.get("boardId");
			// 댓글도 함께 삭제
			sqlSessionTemplate.delete("board.deleteBoardCommentsByBoardId", boardId);
			sqlSessionTemplate.delete("board.deleteBoard", boardId);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("deleteBoard error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/**
	 * 댓글 목록 조회
	 */
	@GetMapping("/{boardId}/comments")
	public List<Map<String, Object>> getBoardComments(@PathVariable String boardId) {
		log.info(">> getBoardComments : {}", boardId);
		return sqlSessionTemplate.selectList("board.selectBoardCommentList", boardId);
	}

	/**
	 * 댓글 등록
	 */
	@PostMapping("/{boardId}/comment")
	public Response createBoardComment(@PathVariable String boardId, @RequestBody Map<String, Object> params) {
		log.info(">> createBoardComment : {}", params);
		Response result = new Response();
		try {
			params.put("commentId", StringUtils.getUUID());
			params.put("boardId", boardId);
			params.put("cretUserId", sessionService.getUserId());
			sqlSessionTemplate.insert("board.insertBoardComment", params);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("createBoardComment error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/**
	 * 댓글 삭제
	 */
	@PostMapping("/comment/delete")
	public Response deleteBoardComment(@RequestBody Map<String, Object> params) {
		log.info(">> deleteBoardComment : {}", params);
		Response result = new Response();
		try {
			String commentId = (String) params.get("commentId");
			sqlSessionTemplate.delete("board.deleteBoardComment", commentId);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("deleteBoardComment error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}
}
