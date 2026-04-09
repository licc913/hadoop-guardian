# Hadoop Guardian 处置知识库来源

以下来源用于初始化系统内置的运行手册。内容已经整理为结构化步骤、验证项和注意事项后落入数据库，不直接存储长段原文。

## HDFS

- HDFS 容量压力与副本不足处置
  - 来源：Apache Hadoop HDFS Commands Guide
  - 链接：https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html

- HDFS 小文件与 NameNode 元数据压力处置
  - 来源：Cloudera Runtime 文档，优化 NameNode 命名空间资源
  - 链接：https://docs.cloudera.com/runtime/latest/scaling-namespaces/topics/hdfs-optimizing-namenode-disk-space-with-hadoop-archives.html

- HDFS 坏块与缺块排查处置
  - 来源：Apache Hadoop HDFS Users Guide
  - 链接：https://hadoop.apache.org/docs/current3/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html

- HDFS 数据倾斜与热点节点处置
  - 来源：Apache Hadoop HDFS Commands Guide
  - 链接：https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html

## YARN

- YARN 队列拥塞与资源争抢处置
  - 来源：Apache Hadoop Capacity Scheduler
  - 链接：https://hadoop.apache.org/docs/stable/hadoop-yarn/hadoop-yarn-site/CapacityScheduler.html

- YARN NodeManager 异常与优雅退役处置
  - 来源：Apache Hadoop Graceful Decommission of YARN Nodes
  - 链接：https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/GracefulDecommission.html

## Hive on Tez

- Hive on Tez 会话池与 AM 资源等待处置
  - 来源：Cloudera 文档，Hive on Tez configurations
  - 链接：https://docs.cloudera.com/cdp-private-cloud-base/7.1.8/configuring-apache-hive/topics/hive-on-tez-configurations.html

- Hive Metastore 模式与连接异常处置
  - 来源：Apache Hive Metastore Administration
  - 链接：https://hive.apache.org/docs/latest/admin/adminmanual-metastore-administration/

## Impala

- Impala Admission Control 排队与内存争抢处置
  - 来源：Cloudera 文档，Admission Control and Query Queuing
  - 链接：https://docs.cloudera.com/cdp-private-cloud-base/7.1.8/impala-manage/topics/impala-admission.html

- Impala 元数据不一致处置
  - 来源：Cloudera 文档，REFRESH
  - 链接：https://docs.cloudera.com/cdp-private-cloud-base/7.1.8/impala-sql-reference/topics/impala-refresh.html
