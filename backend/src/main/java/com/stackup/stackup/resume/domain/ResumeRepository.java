package com.stackup.stackup.resume.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUser_IdAndDeletedFalse(Long userId);

    Optional<Resume> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);

    boolean existsByUser_IdAndSourceUrlAndDeletedFalse(Long userId, String sourceUrl);

    // 스토리지 객체가 아직 남아 있는 **삭제된** 이력서. OrphanedObjectSweeper 전용이고
    // 객체를 실제로 지우는 데 쓰이므로 deleted=true 조건을 절대 빼면 안 된다.
    List<Resume> findTop100ByDeletedTrueAndFilePathIsNotNull();
}
