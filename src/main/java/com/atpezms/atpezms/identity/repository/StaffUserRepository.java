package com.atpezms.atpezms.identity.repository;

import com.atpezms.atpezms.identity.entity.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    Optional<StaffUser> findByUsername(String username);
    boolean existsByUsername(String username);
    List<StaffUser> findAllByOrderByUsernameAsc();
}
