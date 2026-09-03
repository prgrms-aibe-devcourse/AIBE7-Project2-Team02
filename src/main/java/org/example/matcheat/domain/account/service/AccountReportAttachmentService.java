package org.example.matcheat.domain.account.service;

import org.example.matcheat.domain.account.dto.AccountReportAttachmentResponse;
import org.example.matcheat.domain.account.entity.AccountReportAttachmentEntity;
import org.example.matcheat.domain.account.enums.AccountReportStatus;
import org.example.matcheat.domain.account.repository.AccountReportAttachmentRepository;
import org.example.matcheat.domain.account.repository.AccountReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
public class AccountReportAttachmentService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_FILES = 3;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final AccountReportRepository reports;
    private final AccountReportAttachmentRepository attachments;
    private final Clock clock;
    private final Path directory;

    public AccountReportAttachmentService(AccountReportRepository reports,
            AccountReportAttachmentRepository attachments, Clock accountClock,
            @Value("${app.account.report-upload-dir:uploads/account-reports}") String directory) {
        this.reports = reports;
        this.attachments = attachments;
        this.clock = accountClock;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public AccountReportAttachmentResponse upload(long reporterId, long reportId, MultipartFile file) throws IOException {
        var report = reports.findById(reportId).orElseThrow(() ->
                new AccountApplicationException(AccountErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다."));
        if (!report.getReporterId().equals(reporterId)) {
            throw new AccountApplicationException(AccountErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다.");
        }
        if (report.getStatus() != AccountReportStatus.PENDING || attachments.countByReportId(reportId) >= MAX_FILES) {
            throw new AccountApplicationException(AccountErrorCode.VALIDATION_FAILED, "접수 대기 중인 신고에는 이미지 3개까지 첨부할 수 있습니다.");
        }
        validate(file);
        Files.createDirectories(directory);
        String extension = switch (file.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
        String storedName = UUID.randomUUID() + extension;
        Path destination = directory.resolve(storedName);
        file.transferTo(destination);
        try {
            var saved = attachments.save(AccountReportAttachmentEntity.create(
                    reportId, safeName(file.getOriginalFilename()), storedName,
                    file.getContentType(), file.getSize(), clock.instant()));
            return AccountReportAttachmentResponse.from(saved);
        } catch (RuntimeException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<AccountReportAttachmentResponse> list(long reportId) {
        if (!reports.existsById(reportId)) {
            throw new AccountApplicationException(AccountErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다.");
        }
        return attachments.findByReportIdOrderByCreatedAtAsc(reportId).stream()
                .map(AccountReportAttachmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Download download(long attachmentId) throws IOException {
        var value = attachments.findById(attachmentId).orElseThrow(() ->
                new AccountApplicationException(AccountErrorCode.REPORT_NOT_FOUND, "첨부 이미지를 찾을 수 없습니다."));
        Resource resource = new UrlResource(directory.resolve(value.getStoredName()).toUri());
        if (!resource.exists() || !resource.isReadable()) throw new IOException("Stored report image is missing.");
        return new Download(resource, value.getOriginalName(), value.getContentType());
    }

    @Transactional
    public void removeStoredFiles(long reportId) {
        var stored = attachments.findByReportIdOrderByCreatedAtAsc(reportId);
        for (var value : stored) {
            try {
                Files.deleteIfExists(directory.resolve(value.getStoredName()));
            } catch (IOException ignored) {
                // Submission is already failing; database rollback remains authoritative.
            }
        }
        attachments.deleteAll(stored);
    }

    private static void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE
                || !IMAGE_TYPES.contains(file.getContentType())) {
            throw new AccountApplicationException(
                    AccountErrorCode.VALIDATION_FAILED, "5MB 이하 JPG, PNG, WEBP 이미지만 첨부할 수 있습니다.");
        }
        byte[] header = file.getInputStream().readNBytes(12);
        boolean jpeg = header.length >= 3 && header[0] == (byte) 0xff && header[1] == (byte) 0xd8
                && header[2] == (byte) 0xff;
        boolean png = header.length >= 8 && header[0] == (byte) 0x89 && header[1] == 0x50
                && header[2] == 0x4e && header[3] == 0x47;
        boolean webp = header.length >= 12 && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        boolean matchesDeclaredType = switch (file.getContentType()) {
            case "image/jpeg" -> jpeg;
            case "image/png" -> png;
            case "image/webp" -> webp;
            default -> false;
        };
        if (!matchesDeclaredType) {
            throw new AccountApplicationException(
                    AccountErrorCode.VALIDATION_FAILED, "이미지 형식과 파일 내용이 일치하지 않습니다.");
        }
    }

    private static String safeName(String name) {
        return name == null ? "evidence" : Path.of(name).getFileName().toString();
    }

    public record Download(Resource resource, String fileName, String contentType) {}
}
