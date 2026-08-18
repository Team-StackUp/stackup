package com.stackup.stackup.resume.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "resumes",
        indexes = {
                @Index(name = "idx_resumes_user_id", columnList = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    // PDF 는 S3 키, WEB 은 null. (DB CHECK chk_resumes_locator_by_type 로 타입별 필수 강제)
    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ResumeFileType fileType;

    @Column(name = "file_size")
    private Long fileSize;

    // WEB 전용 — 분석 대상 원문 URL. PDF 는 null.
    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ResumeStatus status = ResumeStatus.PENDING;

    private Resume(User user, String originalFilename, String filePath, ResumeFileType fileType,
                   Long fileSize, String sourceUrl) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.filePath = filePath;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.sourceUrl = sourceUrl;
    }

    public static Resume create(User user, String originalFilename, String filePath, ResumeFileType fileType, Long fileSize) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return new Resume(user, originalFilename, filePath, fileType, fileSize, null);
    }

    // 웹 이력서 — S3 업로드 없이 URL 만 보관하고, 본문 추출은 AI 서버가 한다(analyze.web).
    public static Resume createWeb(User user, String displayName, String sourceUrl) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        return new Resume(user, displayName, null, ResumeFileType.WEB, null, sourceUrl);
    }

    public boolean isWeb() {
        return this.fileType == ResumeFileType.WEB;
    }

    public void markAnalyzing() {
        this.status = ResumeStatus.ANALYZING;
    }

    public void markAnalyzed() {
        this.status = ResumeStatus.ANALYZED;
    }

    public void markFailed() {
        this.status = ResumeStatus.FAILED;
    }

    public void markDeleted() {
        this.deleted = true;
    }
}
