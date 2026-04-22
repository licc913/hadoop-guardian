package com.guardian.hadoop.integration.cm;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmServiceLogSnapshotRepository extends JpaRepository<CmServiceLogSnapshotEntity, Long> {

    List<CmServiceLogSnapshotEntity> findTop20ByOrderByCollectedAtDescIdDesc();

    List<CmServiceLogSnapshotEntity> findTop200ByOrderByCollectedAtDescIdDesc();

    List<CmServiceLogSnapshotEntity> findTop300ByClusterNameAndServiceTypeInAndCollectedAtAfterOrderByCollectedAtDescIdDesc(
        String clusterName,
        List<String> serviceTypes,
        Instant collectedAt
    );

    List<CmServiceLogSnapshotEntity> findTop300ByClusterNameAndServiceTypeInOrderByCollectedAtDescIdDesc(
        String clusterName,
        List<String> serviceTypes
    );

    boolean existsByClusterNameAndServiceTypeAndLogTypeAndLogHashAndCollectedAtAfter(
        String clusterName,
        String serviceType,
        String logType,
        String logHash,
        Instant collectedAt
    );

    void deleteByCollectedAtBefore(Instant cutoff);
}
