package com.stackup.stackup.user.domain.consent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUser_Id(Long userId);
}
