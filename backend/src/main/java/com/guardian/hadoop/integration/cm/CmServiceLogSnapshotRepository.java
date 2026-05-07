package com.guardian.hadoop.integration.cm;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<CmServiceLogSnapshotEntity> findTop50ByClusterNameAndServiceTypeOrderByCollectedAtDescIdDesc(
        String clusterName,
        String serviceType
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.clusterName) = upper(:clusterName) "
            + "and upper(entity.serviceName) = upper(:serviceName) "
            + "and upper(entity.serviceType) = upper(:serviceType) "
            + "and entity.collectedAt >= :collectedAt "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByClusterAndServiceNameAndServiceTypeAfterIgnoreCase(
        @Param("clusterName") String clusterName,
        @Param("serviceName") String serviceName,
        @Param("serviceType") String serviceType,
        @Param("collectedAt") Instant collectedAt,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.clusterName) = upper(:clusterName) "
            + "and upper(entity.serviceName) = upper(:serviceName) "
            + "and upper(entity.serviceType) = upper(:serviceType) "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByClusterAndServiceNameAndServiceTypeIgnoreCase(
        @Param("clusterName") String clusterName,
        @Param("serviceName") String serviceName,
        @Param("serviceType") String serviceType,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.clusterName) = upper(:clusterName) "
            + "and upper(entity.serviceType) in :serviceTypes "
            + "and entity.collectedAt >= :collectedAt "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByClusterAndServiceTypesAfterIgnoreCase(
        @Param("clusterName") String clusterName,
        @Param("serviceTypes") List<String> serviceTypes,
        @Param("collectedAt") Instant collectedAt,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.clusterName) = upper(:clusterName) "
            + "and upper(entity.serviceType) in :serviceTypes "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByClusterAndServiceTypesIgnoreCase(
        @Param("clusterName") String clusterName,
        @Param("serviceTypes") List<String> serviceTypes,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.serviceType) in :serviceTypes "
            + "and entity.collectedAt >= :collectedAt "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByServiceTypesAfterIgnoreCase(
        @Param("serviceTypes") List<String> serviceTypes,
        @Param("collectedAt") Instant collectedAt,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.serviceType) in :serviceTypes "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByServiceTypesIgnoreCase(
        @Param("serviceTypes") List<String> serviceTypes,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.clusterName) = upper(:clusterName) "
            + "and upper(entity.serviceType) = upper(:serviceType) "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByClusterAndServiceTypeIgnoreCase(
        @Param("clusterName") String clusterName,
        @Param("serviceType") String serviceType,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.serviceType) = upper(:serviceType) "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByServiceTypeIgnoreCase(
        @Param("serviceType") String serviceType,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.clusterName) = upper(:clusterName) "
            + "and entity.collectedAt >= :collectedAt "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByClusterAfterIgnoreCase(
        @Param("clusterName") String clusterName,
        @Param("collectedAt") Instant collectedAt,
        Pageable pageable
    );

    @Query(
        "select entity from CmServiceLogSnapshotEntity entity "
            + "where upper(entity.clusterName) = upper(:clusterName) "
            + "order by entity.collectedAt desc, entity.id desc"
    )
    List<CmServiceLogSnapshotEntity> findLatestByClusterIgnoreCase(
        @Param("clusterName") String clusterName,
        Pageable pageable
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
