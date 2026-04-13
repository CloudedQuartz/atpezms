package com.atpezms.atpezms.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.atpezms.atpezms.ticketing.entity.PassType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.entity.Ticket;
import com.atpezms.atpezms.ticketing.entity.Visit;
import com.atpezms.atpezms.ticketing.entity.VisitStatus;
import com.atpezms.atpezms.ticketing.entity.Visitor;
import com.atpezms.atpezms.ticketing.entity.Wristband;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VisitRepositoryTest {

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private PassTypeRepository passTypeRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private WristbandRepository wristbandRepository;

    @Test
    void shouldResolveActiveVisitByRfidTag() {
        // Arrange
        Visitor visitor = visitorRepository.save(new Visitor("John", "Doe", null, null, LocalDate.of(1990, 1, 1), 180));
        PassType passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();
        LocalDate visitDate = LocalDate.of(2026, 4, 15);
        
        Ticket ticket = ticketRepository.save(new Ticket(visitor, passType, visitDate, visitDate, visitDate, 150000, "LKR", Instant.now()));
        
        Wristband w = new Wristband("RESOLVE-123");
        w.activate();
        Wristband wristband = wristbandRepository.save(w);
        
        Visit visit = new Visit(visitor, wristband, ticket, Instant.now());
        visitRepository.save(visit);
        visitRepository.flush(); // To test real queries
        
        // Act
        Optional<Visit> resolvedOpt = visitRepository.findActiveByRfidTag("RESOLVE-123");
        
        // Assert
        assertThat(resolvedOpt).isPresent();
        Visit resolved = resolvedOpt.get();
        assertThat(resolved.getStatus()).isEqualTo(VisitStatus.ACTIVE);
        assertThat(resolved.getVisitor().getId()).isEqualTo(visitor.getId());
        assertThat(resolved.getWristband().getId()).isEqualTo(wristband.getId());
        assertThat(resolved.getTicket().getId()).isEqualTo(ticket.getId());
    }
}
