
package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeadLetterEventRepository
        extends JpaRepository<DeadLetterEvent, Long> {

    Optional<DeadLetterEvent> findByEventId(UUID eventId);

    List<DeadLetterEvent> findByStatusOrderByFailedAtAsc(
            DeadLetterEvent.FailureStatus status
    );
}
