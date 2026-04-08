package qualitycenter.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

	private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/board/";

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

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

	@GetMapping("/{boardId}")
	public Map<String, Object> getBoardById(@PathVariable String boardId) {
		sqlSessionTemplate.update("board.incrementViewCnt", boardId);
		Map<String, Object> board = sqlSessionTemplate.selectOne("board.selectBoardById", boardId);
		return board != null ? board : new HashMap<>();
	}

	/** 게시글 등록 (파일 포함) */
	@PostMapping("/createWithFile")
	public Response createWithFile(
			@RequestParam("boardType") String boardType,
			@RequestParam("title") String title,
			@RequestParam(value = "content", required = false) String content,
			@RequestParam(value = "pinYn", required = false, defaultValue = "N") String pinYn,
			@RequestParam(value = "files", required = false) List<MultipartFile> files) {
		Response result = new Response();
		try {
			if ("NOTICE".equals(boardType) && !sessionService.isAdmin()) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "공지사항은 관리자만 작성할 수 있습니다.");
				return result;
			}
			String boardId = StringUtils.getUUID();
			Map<String, Object> params = new HashMap<>();
			params.put("boardId", boardId);
			params.put("boardType", boardType);
			params.put("title", title);
			params.put("content", content != null ? content : "");
			params.put("cretUserId", sessionService.getUserId());
			params.put("pinYn", pinYn);
			sqlSessionTemplate.insert("board.insertBoard", params);

			saveFiles(boardId, files);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("createWithFile error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/** 게시글 수정 (파일 포함) */
	@PostMapping("/updateWithFile")
	public Response updateWithFile(
			@RequestParam("boardId") String boardId,
			@RequestParam("title") String title,
			@RequestParam(value = "content", required = false) String content,
			@RequestParam(value = "pinYn", required = false, defaultValue = "N") String pinYn,
			@RequestParam(value = "files", required = false) List<MultipartFile> files) {
		Response result = new Response();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("boardId", boardId);
			params.put("title", title);
			params.put("content", content != null ? content : "");
			params.put("pinYn", pinYn);
			sqlSessionTemplate.update("board.updateBoard", params);

			saveFiles(boardId, files);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("updateWithFile error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	@PostMapping("/delete")
	public Response deleteBoard(@RequestBody Map<String, Object> params) {
		Response result = new Response();
		try {
			String boardId = (String) params.get("boardId");
			// 첨부파일 삭제
			List<Map<String, Object>> files = sqlSessionTemplate.selectList("board.selectBoardFileList", boardId);
			for (Map<String, Object> f : files) {
				try { new File((String) f.get("filePath")).delete(); } catch (Exception ignore) {}
			}
			sqlSessionTemplate.delete("board.deleteBoardFilesByBoardId", boardId);
			sqlSessionTemplate.delete("board.deleteBoardCommentsByBoardId", boardId);
			sqlSessionTemplate.delete("board.deleteBoard", boardId);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("deleteBoard error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	// === 파일 관련 ===

	@GetMapping("/{boardId}/files")
	public List<Map<String, Object>> getBoardFiles(@PathVariable String boardId) {
		return sqlSessionTemplate.selectList("board.selectBoardFileList", boardId);
	}

	@GetMapping("/file/download/{fileId}")
	public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) throws IOException {
		Map<String, Object> fileInfo = sqlSessionTemplate.selectOne("board.selectBoardFileById", fileId);
		if (fileInfo == null) return ResponseEntity.notFound().build();

		Path path = Paths.get((String) fileInfo.get("filePath"));
		Resource resource = new UrlResource(path.toUri());
		if (!resource.exists()) return ResponseEntity.notFound().build();

		String fileName = (String) fileInfo.get("fileNm");
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8") + "\"")
				.body(resource);
	}

	@PostMapping("/file/delete")
	public Response deleteFile(@RequestBody Map<String, Object> params) {
		Response result = new Response();
		try {
			String fileId = (String) params.get("fileId");
			Map<String, Object> fileInfo = sqlSessionTemplate.selectOne("board.selectBoardFileById", fileId);
			if (fileInfo != null) {
				try { new File((String) fileInfo.get("filePath")).delete(); } catch (Exception ignore) {}
			}
			sqlSessionTemplate.delete("board.deleteBoardFile", fileId);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	private void saveFiles(String boardId, List<MultipartFile> files) throws IOException {
		if (files == null || files.isEmpty()) return;
		File dir = new File(UPLOAD_DIR + boardId);
		if (!dir.exists()) dir.mkdirs();

		for (MultipartFile file : files) {
			if (file.isEmpty()) continue;
			String fileId = StringUtils.getUUID();
			String origName = file.getOriginalFilename();
			Path savePath = Paths.get(dir.getAbsolutePath(), fileId + "_" + origName);
			Files.copy(file.getInputStream(), savePath);

			Map<String, Object> fileParams = new HashMap<>();
			fileParams.put("fileId", fileId);
			fileParams.put("boardId", boardId);
			fileParams.put("fileNm", origName);
			fileParams.put("filePath", savePath.toString());
			fileParams.put("fileSize", file.getSize());
			fileParams.put("cretUserId", sessionService.getUserId());
			sqlSessionTemplate.insert("board.insertBoardFile", fileParams);
		}
	}

	// === 댓글 ===

	@GetMapping("/{boardId}/comments")
	public List<Map<String, Object>> getBoardComments(@PathVariable String boardId) {
		return sqlSessionTemplate.selectList("board.selectBoardCommentList", boardId);
	}

	@PostMapping("/{boardId}/comment")
	public Response createBoardComment(@PathVariable String boardId, @RequestBody Map<String, Object> params) {
		Response result = new Response();
		try {
			params.put("commentId", StringUtils.getUUID());
			params.put("boardId", boardId);
			params.put("cretUserId", sessionService.getUserId());
			sqlSessionTemplate.insert("board.insertBoardComment", params);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	@PostMapping("/comment/update")
	public Response updateBoardComment(@RequestBody Map<String, Object> params) {
		Response result = new Response();
		try {
			sqlSessionTemplate.update("board.updateBoardComment", params);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	@PostMapping("/comment/delete")
	public Response deleteBoardComment(@RequestBody Map<String, Object> params) {
		Response result = new Response();
		try {
			sqlSessionTemplate.delete("board.deleteBoardComment", (String) params.get("commentId"));
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}
}
