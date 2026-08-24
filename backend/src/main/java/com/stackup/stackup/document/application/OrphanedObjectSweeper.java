package com.stackup.stackup.document.application;

import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삭제된 자료의 스토리지 객체를 회수한다.
 *
 * <p>두 가지를 같은 경로로 처리한다.
 *
 * <ol>
 *   <li><b>과거 삭제분</b> — #219 이전에 지운 자료는 행만 soft delete 됐고 S3 의 원본 PDF·
 *       분석 마크다운이 그대로 남아 있다. 앞으로 삭제 이벤트가 다시 발생할 일이 없으니
 *       백필하지 않으면 영원히 남는다(V31 의 탈퇴자 토큰과 같은 종류의 누락).</li>
 *   <li><b>실패분</b> — #219 의 파기는 AFTER_COMMIT 이고 <b>실패해도 사용자 요청을 실패시키지
 *       않는다</b>(그 시점엔 이미 커밋됐고 되돌릴 수도 없다). 그래서 스토리지가 잠깐 죽어 있으면
 *       객체가 로그 한 줄만 남기고 새어 나간다. 그 구멍을 여기서 닫는다.</li>
 * </ol>
 *
 * <p><b>파괴적 작업이다.</b> 조회는 반드시 삭제된 행만 잡아야 한다 — 조건이 하나라도 어긋나면
 * 살아있는 사용자의 이력서를 지운다. 그래서 리포지토리 쿼리에 {@code DeletedTrue} 를 이름으로
 * 박아 두고(파생 쿼리라 조건이 시그니처에 드러난다) 테스트로 고정한다.
 *
 * <p>회수에 성공하면 경로를 비운다({@code markContentPurged}). 객체가 없는데 키만 남으면
 * "아직 회수 안 됨"과 구분되지 않아 매 주기마다 다시 지우려 든다.
 */
@Component
@RequiredArgsConstructor
public class OrphanedObjectSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanedObjectSweeper.class);

    private final ResumeRepository resumeRepository;
    private final AnalyzedDocumentRepository documentRepository;
    private final ObjectStorageClient storage;

    @Transactional
    @Scheduled(
        fixedDelayString = "${storage.orphan-sweep-interval-ms:900000}",
        initialDelayString = "${storage.orphan-sweep-initial-delay-ms:60000}")
    public void sweep() {
        int resumes = sweepResumes();
        int documents = sweepDocuments();
        if (resumes + documents > 0) {
            log.info("orphaned object sweep done. resumeFiles={}, analyzedDocs={}", resumes, documents);
        }
    }

    private int sweepResumes() {
        List<Resume> targets = resumeRepository.findTop100ByDeletedTrueAndFilePathIsNotNull();
        int purged = 0;
        for (Resume resume : targets) {
            if (purge(resume.getFilePath())) {
                resume.markContentPurged();
                purged++;
            }
        }
        return purged;
    }

    private int sweepDocuments() {
        List<AnalyzedDocument> targets = documentRepository.findTop100ByDeletedTrueAndDocumentPathIsNotNull();
        int purged = 0;
        for (AnalyzedDocument doc : targets) {
            if (purge(doc.getDocumentPath())) {
                doc.markContentPurged();
                purged++;
            }
        }
        return purged;
    }

    /**
     * 객체 하나를 지운다. 실패하면 경로를 비우지 않아 다음 주기에 다시 시도한다 —
     * 여기서 예외를 전파하면 같은 배치의 나머지 회수까지 롤백된다.
     */
    private boolean purge(String key) {
        try {
            storage.delete(key);
            log.info("orphaned storage object purged. key={}", key);
            return true;
        } catch (Exception e) {
            log.warn("orphaned storage object purge failed — 다음 주기에 재시도. key={}", key, e);
            return false;
        }
    }
}
