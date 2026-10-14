package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.RegistrationOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, UUID> {
}
