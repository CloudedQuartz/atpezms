package com.atpezms.atpezms.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.atpezms.atpezms.ticketing.entity.PassType;
import com.atpezms.atpezms.ticketing.entity.PassTypeCode;
import com.atpezms.atpezms.ticketing.entity.Ticket;
import com.atpezms.atpezms.ticketing.entity.Visitor;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketRepositoryTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private PassTypeRepository passTypeRepository;

    @Test
    void shouldSaveAndLoadTicket() {
        // Arrange
        Visitor visitor = visitorRepository.save(new Visitor("John", "Doe", null, null, LocalDate.of(1990, 1, 1), 180));
        PassType passType = passTypeRepository.findByCode(PassTypeCode.SINGLE_DAY).orElseThrow();
        LocalDate visitDate = LocalDate.of(2026, 4, 15);
        
        Ticket ticket = new Ticket(visitor, passType, visitDate, visitDate, visitDate, 150000, "LKR", Instant.now());
        
        // Act
        Ticket saved = ticketRepository.save(ticket);
        ticketRepository.flush(); // ensure it hits DB
        
        // Assert
        Ticket loaded = ticketRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getVisitor().getId()).isEqualTo(visitor.getId());
        assertThat(loaded.getPassType().getId()).isEqualTo(passType.getId());
        assertThat(loaded.getPricePaidCents()).isEqualTo(150000);
        assertThat(loaded.getCurrency()).isEqualTo("LKR");
        assertThat(loaded.getValidFrom()).isEqualTo(visitDate);
        assertThat(loaded.getValidTo()).isEqualTo(visitDate);
    }
}
