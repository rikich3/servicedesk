package com.servicedesk.reports;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ReportDtos {
    private ReportDtos() {}

    /**
     * @param activeAssigned     tickets currently OPEN or IN_PROGRESS assigned to the technician
     * @param inProgress         subset of the above that are IN_PROGRESS
     * @param resolvedInPeriod   tickets whose latest resolution falls inside [from, to)
     * @param avgResolutionHours mean of (resolved_at - created_at) over resolvedInPeriod, null when none
     */
    public record TechnicianWorkload(Long userId, String fullName, long activeAssigned, long inProgress,
                                     long resolvedInPeriod, Double avgResolutionHours) {}

    public record Summary(Instant from, Instant to,
                          long openTickets,
                          long unassignedTickets,
                          Map<String, Long> byStatus,
                          Map<String, Long> byPriority,
                          long createdInPeriod,
                          long resolvedInPeriod,
                          Double avgResolutionHours,
                          long reopenedTickets,
                          List<TechnicianWorkload> workload) {}
}
