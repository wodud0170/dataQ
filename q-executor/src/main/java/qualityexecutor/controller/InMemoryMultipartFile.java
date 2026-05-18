package qualityexecutor.controller;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.web.multipart.MultipartFile;

/**
 * 88번 fix — 일괄 업로드 multipart 임시파일이 HTTP 요청 종료 시 Tomcat 에 의해 삭제되어,
 * 백그라운드 스레드 (DataStandardService) 가 나중에 파일을 읽을 때 FileNotFoundException 이
 * 발생하던 문제 회피.
 *
 * <p>업로드를 받은 즉시 (요청 스레드에서) 파일 내용을 byte[] 로 메모리에 복사한 뒤,
 * 비동기 스레드는 임시파일이 아닌 이 메모리 복사본으로 엑셀을 읽는다.</p>
 */
public class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    /** 요청 스레드에서 호출 — 이 시점에 src 의 임시파일이 아직 살아있어야 한다. */
    public InMemoryMultipartFile(MultipartFile src) throws IOException {
        this.name = src.getName();
        this.originalFilename = src.getOriginalFilename();
        this.contentType = src.getContentType();
        this.content = src.getBytes();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }

    @Override
    public long getSize() {
        return content == null ? 0 : content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), content);
    }
}
