package com.guardian.hadoop.integration.datasource;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JmxEndpointRepository extends JpaRepository<JmxEndpointEntity, Long> {

    List<JmxEndpointEntity> findAllByOrderByServiceTypeAscRoleTypeAscTargetHostAsc();
}
