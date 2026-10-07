package com.example.llmrouter.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {

    @Query("""
            SELECT new com.example.llmrouter.usage.ClientUsageAggregate(
                u.apiKeyId, u.clientName, COUNT(u),
                SUM(CASE WHEN u.cacheHit = true THEN 1L ELSE 0L END),
                COALESCE(SUM(u.virtualCost), 0.0), COALESCE(SUM(u.baselineCost), 0.0))
            FROM UsageLog u
            WHERE u.apiKeyId = :apiKeyId
            GROUP BY u.apiKeyId, u.clientName
            """)
    Optional<ClientUsageAggregate> summarizeByClient(@Param("apiKeyId") Long apiKeyId);

    @Query("""
            SELECT new com.example.llmrouter.usage.ModelUsageAggregate(
                u.provider, u.model, COUNT(u),
                COALESCE(SUM(u.virtualCost), 0.0), COALESCE(AVG(u.latencyMs), 0.0))
            FROM UsageLog u
            GROUP BY u.provider, u.model
            ORDER BY u.provider, u.model
            """)
    List<ModelUsageAggregate> summarizeByModel();
}
