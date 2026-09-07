package dev.kraken.journal.journal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.journal.dto.JournalResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final TradingJournalService service;

    public JournalController(TradingJournalService service) {
        this.service = service;
    }

    @GetMapping
    public JournalResponse journal(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return service.buildJournal(days);
    }
}
