package com.stackup.stackup.coverletter.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "cover_letters",
        indexes = {
                @Index(name = "idx_cover_letters_user_id", columnList = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetter extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 200)
    private String title;

    // 문항 배열 JSON: [{"question":"...","answer":"..."}]. AnalyzedDocument.techStack 와 동일 매핑 패턴.
    @Column(name = "items", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String items;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CoverLetterStatus status = CoverLetterStatus.PENDING;

    private CoverLetter(User user, String title, String items) {
        this.user = user;
        this.title = title;
        this.items = items;
    }

    public static CoverLetter create(User user, String title, String itemsJson) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return new CoverLetter(user, title, itemsJson == null ? "[]" : itemsJson);
    }

    public void markAnalyzing() {
        this.status = CoverLetterStatus.ANALYZING;
    }

    public void markAnalyzed() {
        this.status = CoverLetterStatus.ANALYZED;
    }

    public void markFailed() {
        this.status = CoverLetterStatus.FAILED;
    }

    /**
     * 문항 원문을 파기한다(빈 배열로).
     *
     * <p>자소서 본문은 지원동기·성장과정 같은 지극히 개인적인 서술이고, 다른 자료와 달리
     * S3 가 아니라 <b>이 행 안에</b> 산다. 삭제 시 분석 마크다운·임베딩은 cascade 가
     * 파기하지만(#219) 원문은 여기서 비워야 한다 — 삭제 후에는 아무도 읽지 않는다
     * (분석은 생성 시점에 본문을 메시지에 인라인한다).
     *
     * <p>컬럼이 NOT NULL DEFAULT '[]' 라 제약 변경 없이 빈 배열로 비운다.
     */
    public void purgeItems() {
        this.items = "[]";
    }

    public void markDeleted() {
        this.deleted = true;
    }
}
