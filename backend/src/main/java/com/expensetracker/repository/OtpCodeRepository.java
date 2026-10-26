package com.expensetracker.repository;

import com.expensetracker.model.OtpCode;
import com.expensetracker.model.OtpPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OtpCode o WHERE o.email = :email AND o.purpose = :purpose")
    Optional<OtpCode> findForUpdate(@Param("email") String email, @Param("purpose") OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.windowStart < :cutoff")
    int deleteByWindowStartBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.email = :email")
    int deleteByEmail(@Param("email") String email);
}
