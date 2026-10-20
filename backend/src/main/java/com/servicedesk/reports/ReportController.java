package com.servicedesk.reports;

import com.servicedesk.identity.AppUserPrincipal;
import com.servicedesk.reports.ReportDtos.Summary;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{orgId}/reports")
public class ReportController {

    private final ReportService service;
    private final Clock clock;

    public ReportController(ReportService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** Defaults to the last 30 days. Range is half-open: [from, to). */
    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(30, ChronoUnit.DAYS);
        return service.summary(orgId, me.getId(), start, end);
    }
}
