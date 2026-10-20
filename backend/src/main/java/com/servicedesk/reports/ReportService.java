package com.servicedesk.reports;

import com.servicedesk.organizations.AccessGuard;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.reports.ReportDtos.Summary;
import com.servicedesk.reports.ReportDtos.TechnicianWorkload;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hand-written SQL for the dashboard. These are aggregations over potentially many rows, so
 * they run in PostgreSQL rather than loading entities into memory.
 *
 * <p>Resolution time is measured as {@code resolved_at - created_at}. When a ticket is reopened,
 * {@code resolved_at} is cleared and set again on the next resolution, so a reopened ticket
 * counts once, with the full elapsed time until its final resolution. {@code reopen_count}
 * is reported separately so the reader can judge rework.
 */
@Service
public class ReportService {

    private final JdbcClient jdbc;
    private final AccessGuard guard;

    public ReportService(JdbcClient jdbc, AccessGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Summary summary(Long organizationId, Long userId, Instant from, Instant to) {
        guard.requireRole(organizationId, userId, MemberRole.ADMIN, MemberRole.TECHNICIAN);
        Timestamp fromTs = Timestamp.from(from);
        Timestamp toTs = Timestamp.from(to);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED")) byStatus.put(s, 0L);
        jdbc.sql("""
                SELECT status, COUNT(*) AS total
                FROM tickets
                WHERE organization_id = :org
                GROUP BY status
                """)
            .param("org", organizationId)
            .query((rs, i) -> Map.entry(rs.getString("status"), rs.getLong("total")))
            .list()
            .forEach(e -> byStatus.put(e.getKey(), e.getValue()));

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (String p : List.of("LOW", "MEDIUM", "HIGH", "URGENT")) byPriority.put(p, 0L);
        jdbc.sql("""
                SELECT priority, COUNT(*) AS total
                FROM tickets
                WHERE organization_id = :org AND status IN ('OPEN', 'IN_PROGRESS')
                GROUP BY priority
                """)
            .param("org", organizationId)
            .query((rs, i) -> Map.entry(rs.getString("priority"), rs.getLong("total")))
            .list()
            .forEach(e -> byPriority.put(e.getKey(), e.getValue()));

        long unassigned = jdbc.sql("""
                SELECT COUNT(*) FROM tickets
                WHERE organization_id = :org AND status IN ('OPEN', 'IN_PROGRESS') AND assignee_id IS NULL
                """)
            .param("org", organizationId).query(Long.class).single();

        record PeriodStats(long created, long resolved, Double avgHours, long reopened) {}
        PeriodStats period = jdbc.sql("""
                SELECT
                    COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to)   AS created_in_period,
                    COUNT(*) FILTER (WHERE resolved_at >= :from AND resolved_at < :to) AS resolved_in_period,
                    AVG(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0)
                        FILTER (WHERE resolved_at >= :from AND resolved_at < :to)      AS avg_resolution_hours,
                    COUNT(*) FILTER (WHERE reopen_count > 0)                            AS reopened
                FROM tickets
                WHERE organization_id = :org
                """)
            .param("org", organizationId).param("from", fromTs).param("to", toTs)
            .query((rs, i) -> new PeriodStats(
                    rs.getLong("created_in_period"), rs.getLong("resolved_in_period"),
                    nullableDouble(rs.getObject("avg_resolution_hours")), rs.getLong("reopened")))
            .single();

        // Workload per technician: JOIN memberships → users → tickets, aggregated with FILTER clauses
        // so one pass over the tickets produces every column.
        List<TechnicianWorkload> workload = jdbc.sql("""
                SELECT u.id AS user_id,
                       u.full_name,
                       COUNT(t.id) FILTER (WHERE t.status IN ('OPEN', 'IN_PROGRESS'))                  AS active_assigned,
                       COUNT(t.id) FILTER (WHERE t.status = 'IN_PROGRESS')                             AS in_progress,
                       COUNT(t.id) FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to)       AS resolved_in_period,
                       AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0)
                           FILTER (WHERE t.resolved_at >= :from AND t.resolved_at < :to)               AS avg_resolution_hours
                FROM memberships m
                JOIN users u ON u.id = m.user_id
                LEFT JOIN tickets t ON t.assignee_id = u.id AND t.organization_id = m.organization_id
                WHERE m.organization_id = :org AND m.role IN ('TECHNICIAN', 'ADMIN')
                GROUP BY u.id, u.full_name
                ORDER BY active_assigned DESC, u.full_name
                """)
            .param("org", organizationId).param("from", fromTs).param("to", toTs)
            .query((rs, i) -> new TechnicianWorkload(
                    rs.getLong("user_id"), rs.getString("full_name"), rs.getLong("active_assigned"),
                    rs.getLong("in_progress"), rs.getLong("resolved_in_period"),
                    nullableDouble(rs.getObject("avg_resolution_hours"))))
            .list();

        long open = byStatus.get("OPEN") + byStatus.get("IN_PROGRESS");
        return new Summary(from, to, open, unassigned, byStatus, byPriority, period.created(), period.resolved(),
                period.avgHours(), period.reopened(), workload);
    }

    private static Double nullableDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
