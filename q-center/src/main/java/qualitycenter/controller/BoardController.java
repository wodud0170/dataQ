package qualitycenter.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ndata.common.message.Response;
import com.ndata.quality.common.PermCode;
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

	/** 110 10.3-2c — 게시판 유형 */
	private static final List<String> BOARD_TYPES = Arrays.asList("NOTICE", "QNA");

	/** 110 10.3-4 — 첨부 허용 확장자·글당 개수 (크기 상한은 application.properties multipart 설정) */
	private static final Set<String> FILE_EXTS = new HashSet<>(Arrays.asList(
			"pdf", "hwp", "hwpx", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "png", "jpg", "jpeg", "gif", "zip"));
	private static final int MAX_FILES = 10;

	/** 110 10.3-9 — 이 세션에서 연 글 (조회수는 세션당 한 번) */
	private static final String VIEWED_ATTR = "BOARD_VIEWED";

	private static final String MSG_NOT_FOUND = "게시글을 찾을 수 없습니다.";
	private static final String MSG_NOT_OWNER = "작성자나 공지 관리 권한이 있는 사용자만 수정·삭제할 수 있습니다.";

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

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
	public Map<String, Object> getBoardById(@PathVariable String boardId, HttpSession session) {
		// 110 10.3-9 — 조회수는 세션당 한 번 (같은 사용자가 다시 열어도 그대로, 다시 로그인하면 새로 센다)
		@SuppressWarnings("unchecked")
		Set<String> viewed = (Set<String>) session.getAttribute(VIEWED_ATTR);
		if (viewed == null) {
			viewed = ConcurrentHashMap.newKeySet();
			session.setAttribute(VIEWED_ATTR, viewed);
		}
		if (viewed.add(boardId)) sqlSessionTemplate.update("board.incrementViewCnt", boardId);
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
			if (!BOARD_TYPES.contains(boardType)) {
				result.setResultInfo(400, "게시판 유형이 올바르지 않습니다.");
				return result;
			}
			boolean boardAdmin = sessionService.hasPerm(PermCode.BOARD_ADMIN);
			if ("NOTICE".equals(boardType) && !boardAdmin) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "공지사항을 쓸 권한이 없습니다 (공지 관리 권한 필요).");
				return result;
			}
			String fileErr = checkFiles(files, 0);
			if (fileErr != null) {
				result.setResultInfo(400, fileErr);
				return result;
			}
			String boardId = StringUtils.getUUID();
			Map<String, Object> params = new HashMap<>();
			params.put("boardId", boardId);
			params.put("boardType", boardType);
			params.put("title", title);
			params.put("content", content != null ? content : "");
			params.put("cretUserId", sessionService.getUserId());
			// 110 10.3-2a — 상단 고정은 공지 관리 권한만 (화면은 체크박스를 그 사용자에게만 보인다)
			params.put("pinYn", boardAdmin ? pinYn : "N");
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
			@RequestParam(value = "files", required = false) List<MultipartFile> files,
			@RequestParam(value = "deleteFileIds", required = false) List<String> deleteFileIds) {
		Response result = new Response();
		try {
			Map<String, Object> board = sqlSessionTemplate.selectOne("board.selectBoardById", boardId);
			if (board == null) {
				result.setResultInfo(404, MSG_NOT_FOUND);
				return result;
			}
			boolean boardAdmin = sessionService.hasPerm(PermCode.BOARD_ADMIN);
			// 110 10.3-2b — 공지는 작성자여도 공지 관리 권한이 있어야 고친다
			if ("NOTICE".equals(board.get("boardType")) && !boardAdmin) {
				result.setResultInfo(403, "공지사항을 수정할 권한이 없습니다 (공지 관리 권한 필요).");
				return result;
			}
			// 110 10.3-1 — 작성자 본인 또는 공지 관리 권한
			if (!isOwner(board) && !boardAdmin) {
				result.setResultInfo(403, MSG_NOT_OWNER);
				return result;
			}
			// 110 10.3-6 — 수정 화면에서 X 로 뺀 기존 첨부. 저장할 때 한꺼번에 지운다 (그 글의 첨부만)
			Map<String, String> pathById = new HashMap<>();
			for (Map<String, Object> f : sqlSessionTemplate.<Map<String, Object>>selectList("board.selectBoardFilePathList", boardId)) {
				pathById.put((String) f.get("fileId"), (String) f.get("filePath"));
			}
			Set<String> delIds = deleteFileIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(deleteFileIds);
			for (String id : delIds) {
				if (!pathById.containsKey(id)) {
					result.setResultInfo(400, "이 글의 첨부 파일이 아닙니다: " + id);
					return result;
				}
			}
			String fileErr = checkFiles(files, pathById.size() - delIds.size());
			if (fileErr != null) {
				result.setResultInfo(400, fileErr);
				return result;
			}

			Map<String, Object> params = new HashMap<>();
			params.put("boardId", boardId);
			params.put("title", title);
			params.put("content", content != null ? content : "");
			// 110 10.3-2a — 공지 관리 권한이 없으면 상단 고정은 지금 값 그대로 (작성자가 고쳐도 풀리지 않게)
			params.put("pinYn", boardAdmin ? pinYn : null);
			try (SqlSession s = sqlSessionFactory.openSession()) {
				s.update("board.updateBoard", params);
				if (!delIds.isEmpty()) {
					Map<String, Object> dp = new HashMap<>();
					dp.put("boardId", boardId);
					dp.put("fileIds", delIds);
					s.delete("board.deleteBoardFilesByIds", dp);
				}
				s.commit();
			}
			for (String id : delIds) deleteDiskFile(pathById.get(id));

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
			Map<String, Object> board = sqlSessionTemplate.selectOne("board.selectBoardById", boardId);
			if (board == null) {
				result.setResultInfo(404, MSG_NOT_FOUND);
				return result;
			}
			// 110 10.3-1 — 작성자 본인 또는 공지 관리 권한
			if (!isOwner(board) && !sessionService.hasPerm(PermCode.BOARD_ADMIN)) {
				result.setResultInfo(403, MSG_NOT_OWNER);
				return result;
			}
			// 110 10.3-11 — 첨부·댓글·글 DELETE 를 한 트랜잭션으로, 디스크 파일은 커밋한 뒤에 지운다
			List<Map<String, Object>> files = sqlSessionTemplate.selectList("board.selectBoardFilePathList", boardId);
			try (SqlSession s = sqlSessionFactory.openSession()) {
				s.delete("board.deleteBoardFilesByBoardId", boardId);
				s.delete("board.deleteBoardCommentsByBoardId", boardId);
				s.delete("board.deleteBoard", boardId);
				s.commit();
			}
			for (Map<String, Object> f : files) deleteDiskFile((String) f.get("filePath"));
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error("deleteBoard error: {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/** 그 글·댓글을 세션 사용자가 썼는지 */
	private boolean isOwner(Map<String, Object> row) {
		return Objects.equals(row.get("cretUserId"), sessionService.getUserId());
	}

	/** DB 행을 지운 뒤 디스크 파일 삭제 — 실패는 로그만 (행은 이미 없다) */
	private void deleteDiskFile(String filePath) {
		try {
			Files.deleteIfExists(Paths.get(filePath));
		} catch (Exception e) {
			log.warn("board file delete failed: {} ({})", filePath, e.getMessage());
		}
	}

	/**
	 * 110 10.3-4 — 첨부 확장자·글당 개수 검사 (DB·디스크 쓰기 전). 통과하면 null.
	 * @param keepCount 이 글에 남는 기존 첨부 수 (등록은 0, 수정은 기존 − 삭제 표시)
	 */
	private String checkFiles(List<MultipartFile> files, int keepCount) {
		int count = keepCount;
		if (files != null) {
			for (MultipartFile f : files) {
				if (f.isEmpty()) continue;
				String nm = f.getOriginalFilename();
				int dot = nm == null ? -1 : nm.lastIndexOf('.');
				String ext = dot < 0 ? "" : nm.substring(dot + 1).toLowerCase();
				if (!FILE_EXTS.contains(ext)) return "첨부할 수 없는 파일 형식입니다: " + nm;
				count++;
			}
		}
		return count > MAX_FILES ? "첨부 파일은 글당 " + MAX_FILES + "개까지입니다." : null;
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
		// 110 10.3-7 — URLEncoder 는 공백을 + 로 바꾼다 → %20. filename* 는 RFC 5987 (브라우저가 이 값을 쓴다)
		String encoded = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
				.body(resource);
	}

	private void saveFiles(String boardId, List<MultipartFile> files) throws IOException {
		if (files == null || files.isEmpty()) return;
		// 86번 #21 — boardId 자체에 '*' 가 들어갈 수 있어 폴더 경로도 sanitize 필요
		File dir = new File(UPLOAD_DIR + sanitizeForFile(boardId));
		if (!dir.exists()) dir.mkdirs();

		for (MultipartFile file : files) {
			if (file.isEmpty()) continue;
			String fileId = StringUtils.getUUID();
			String origName = file.getOriginalFilename();
			// 디스크 저장명만 sanitize — DB 의 fileId / fileNm 는 그대로 보관 (다운로드 시 원본명 노출 유지)
			String diskName = sanitizeForFile(fileId) + "_" + sanitizeForFile(origName);
			Path savePath = Paths.get(dir.getAbsolutePath(), diskName);
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

	/**
	 * 파일명 sanitize — Windows/Linux/macOS 공통 안전 문자만 남김.
	 * - Windows reserved: < > : " / \ | ? *
	 * - 제어문자 (0x00-0x1F)
	 * - 끝의 . / 공백 (Windows 가 자동 제거)
	 * - 빈 문자열은 "_" 로 대체
	 *
	 * 86번 #21: StringUtils.getUUID() 는 1.56% 확률로 '*' 포함 → Windows 저장 실패.
	 * 사용자가 업로드한 origName 도 OS 마다 허용 문자가 달라 함께 필터.
	 */
	private static String sanitizeForFile(String s) {
		if (s == null || s.isEmpty()) return "_";
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x20 || c == '<' || c == '>' || c == ':' || c == '"'
			    || c == '/' || c == '\\' || c == '|' || c == '?' || c == '*') {
				sb.append('_');
			} else {
				sb.append(c);
			}
		}
		// trailing . / 공백 제거 (Windows 호환)
		int end = sb.length();
		while (end > 0) {
			char c = sb.charAt(end - 1);
			if (c == '.' || c == ' ') end--;
			else break;
		}
		if (end == 0) return "_";
		return end == sb.length() ? sb.toString() : sb.substring(0, end);
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
			Response denied = checkCommentOwner((String) params.get("commentId"));
			if (denied != null) return denied;
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
			String commentId = (String) params.get("commentId");
			Response denied = checkCommentOwner(commentId);
			if (denied != null) return denied;
			sqlSessionTemplate.delete("board.deleteBoardComment", commentId);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/** 110 10.3-1 — 댓글 수정·삭제는 작성자 본인 또는 공지 관리 권한. 통과하면 null */
	private Response checkCommentOwner(String commentId) {
		Map<String, Object> comment = sqlSessionTemplate.selectOne("board.selectBoardCommentById", commentId);
		Response r = new Response();
		if (comment == null) {
			r.setResultInfo(404, "댓글을 찾을 수 없습니다.");
			return r;
		}
		if (!isOwner(comment) && !sessionService.hasPerm(PermCode.BOARD_ADMIN)) {
			r.setResultInfo(403, MSG_NOT_OWNER);
			return r;
		}
		return null;
	}
}
