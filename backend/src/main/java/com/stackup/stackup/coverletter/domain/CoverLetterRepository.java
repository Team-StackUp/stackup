package com.stackup.stackup.coverletter.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    List<CoverLetter> findByUser_IdAndDeletedFalseOrderByIdDesc(Long userId);

    Optional<CoverLetter> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);
}
