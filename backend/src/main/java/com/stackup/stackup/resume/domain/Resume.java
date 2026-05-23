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

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ResumeFileType fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ResumeStatus status = ResumeStatus.PENDING;

    private Resume(User user, String originalFilename, String filePath, ResumeFileType fileType, Long fileSize) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.filePath = filePath;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    public static Resume create(User user, String originalFilename, String filePath, ResumeFileType fileType, Long fileSize) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return new Resume(user, originalFilename, filePath, fileType, fileSize);
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
}
