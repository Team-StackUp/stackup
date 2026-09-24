package com.stackup.stackup.document.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.application.AnalysisRequestService.AnalysisHandle;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.Resume;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 분석에 실패한 문서를 같은 원본으로 다시 분석한다.
//
// 지금까지 분석 실패는 되돌릴 수 없었다 — 재분석 API 가 없어서 사용자는 자료를 지우고 다시
// 등록해야 했다(PDF 면 파일을 다시 업로드). 그런데 실패의 상당수는 LLM 호출 타임아웃 같은
// 일시 장애다(운영에서 웹 이력서 1건이 `APITimeoutError` 로 사흘째 FAILED 로 남아 있었다).
// 원본(S3 파일·URL·자소서 본문)은 그대로 살아 있으므로 다시 돌리기만 하면 된다.
//
// 음성 답변의 재전사(`VoiceRetranscribeService`)와 같은 성격의 2차 방어선이다.
@Service
@RequiredArgsConstructor
public class DocumentReanalysisService {

    private static final Logger log = LoggerFactory.getLogger(DocumentReanalysisService.class);

    private final AnalyzedDocumentRepository documentRepository;
    private final AnalysisRequestService analysisRequestService;

    @Transactional
    public AnalysisHandle reanalyze(Long userId, Long documentId) {
        AnalyzedDocument doc = documentRepository.findActiveByIdAndOwner(documentId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.DOC_NOT_FOUND));
        if (doc.getAnalysisStatus() != AnalysisStatus.FAILED) {
            throw new DomainException(ApiErrorCode.DOC_REANALYZE_NOT_ALLOWED);
        }

        // 재분석은 새 AnalyzedDocument 를 만든다(기존 요청 경로와 동일). 실패한 행을 남겨 두면
        // 목록에 실패 카드와 진행 카드가 나란히 보이므로 여기서 치운다.
        doc.markDeleted();

        AnalysisHandle handle = dispatch(userId, doc);
        log.info("document reanalysis requested. userId={}, failedDocumentId={}, newDocumentId={}",
            userId, documentId, handle.analyzedDocumentId());
        return handle;
    }

    private AnalysisHandle dispatch(Long userId, AnalyzedDocument doc) {
        Resume resume = doc.getResume();
        if (resume != null) {
            // 웹 이력서와 PDF 는 같은 Resume 행을 쓰고 locator 만 다르다(source_url vs file_path).
            return hasSourceUrl(resume)
                ? analysisRequestService.requestWebResumeAnalysis(userId, resume.getId())
                : analysisRequestService.requestResumeAnalysis(userId, resume.getId());
        }
        if (doc.getRepository() != null) {
            return analysisRequestService.requestRepositoryAnalysis(
                userId, doc.getRepository().getId());
        }
        if (doc.getCoverLetter() != null) {
            return analysisRequestService.requestCoverLetterAnalysis(
                userId, doc.getCoverLetter().getId());
        }
        // single_source CHECK 제약상 도달 불가 — 방어적으로 막는다.
        throw new DomainException(ApiErrorCode.DOC_REANALYZE_NOT_ALLOWED);
    }

    private static boolean hasSourceUrl(Resume resume) {
        return resume.getSourceUrl() != null && !resume.getSourceUrl().isBlank();
    }
}
