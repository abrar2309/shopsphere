
package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.EventClaim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventClaimRepository
        extends JpaRepository<EventClaim, UUID> {
}
