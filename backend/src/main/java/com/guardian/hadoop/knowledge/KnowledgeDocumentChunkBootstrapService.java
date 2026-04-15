package com.guardian.hadoop.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentChunkBootstrapService implements ApplicationRunner {

    private static final int TARGET_CHUNK_SIZE = 1600;
    private static final int MIN_FLUSH_SIZE = 480;
    private static final String IMPALA_TOPICS_BASE_URL = "https://impala.apache.org/docs/build/plain-html/topics/";

    private final KnowledgeDocumentChunkRepository repository;

    public KnowledgeDocumentChunkBootstrapService(KnowledgeDocumentChunkRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (DocumentSpec spec : documentSpecs()) {
            if (repository.countByDocumentKey(spec.documentKey) > 0) {
                continue;
            }
            importDocument(spec, new ClassPathResource(spec.resourcePath));
        }
        for (DirectorySpec spec : directorySpecs()) {
            importDirectory(spec);
        }
    }

    private void importDirectory(DirectorySpec spec) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:" + spec.resourcePattern);
        Arrays.sort(resources, Comparator.comparing(resource -> {
            String filename = resource.getFilename();
            return filename == null ? "" : filename;
        }));

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".html")) {
                continue;
            }
            String basename = filename.substring(0, filename.length() - 5);
            DocumentSpec documentSpec = new DocumentSpec(
                spec.domain,
                spec.documentKeyPrefix + "-" + basename,
                spec.sourceName,
                spec.sourceUrlPrefix + filename,
                spec.resourcePattern.replace("*", filename)
            );
            if (repository.countByDocumentKey(documentSpec.documentKey) > 0) {
                continue;
            }
            importDocument(documentSpec, resource);
        }
    }

    private void importDocument(DocumentSpec spec, Resource resource) throws IOException {
        if (!resource.exists()) {
            return;
        }

        Document document;
        try (InputStream input = resource.getInputStream()) {
            document = Jsoup.parse(input, "UTF-8", spec.sourceUrl);
        }

        Element root = findRoot(document);
        cleanup(root);

        String documentTitle = normalized(document.title());
        if (documentTitle.isEmpty()) {
            documentTitle = spec.sourceName;
        }

        Elements contentElements = root.select("h1, h2, h3, h4, h5, h6, p, pre, li, td");
        String currentSection = documentTitle;
        StringBuilder chunk = new StringBuilder();
        int chunkIndex = 0;

        for (Element element : contentElements) {
            String tag = element.tagName().toLowerCase();
            String text = normalized(element.text());
            if (text.isEmpty()) {
                continue;
            }

            if (tag.startsWith("h")) {
                chunkIndex = flushChunk(spec, documentTitle, currentSection, chunk, chunkIndex);
                currentSection = text;
                continue;
            }

            if (chunk.length() > TARGET_CHUNK_SIZE && chunk.length() >= MIN_FLUSH_SIZE) {
                chunkIndex = flushChunk(spec, documentTitle, currentSection, chunk, chunkIndex);
            }

            if (chunk.length() > 0) {
                chunk.append('\n');
            }
            chunk.append(text);
        }

        flushChunk(spec, documentTitle, currentSection, chunk, chunkIndex);
    }

    private int flushChunk(DocumentSpec spec, String documentTitle, String currentSection,
                           StringBuilder chunk, int chunkIndex) {
        String content = normalized(chunk.toString());
        if (content.isEmpty()) {
            chunk.setLength(0);
            return chunkIndex;
        }

        KnowledgeDocumentChunkEntity entity = new KnowledgeDocumentChunkEntity();
        entity.setDomain(spec.domain);
        entity.setDocumentKey(spec.documentKey);
        entity.setDocumentTitle(documentTitle);
        entity.setSectionTitle(currentSection);
        entity.setChunkIndex(chunkIndex);
        entity.setChunkKey(spec.documentKey + "-" + chunkIndex);
        entity.setSourceName(spec.sourceName);
        entity.setSourceUrl(spec.sourceUrl);
        entity.setContent(content);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        chunk.setLength(0);
        return chunkIndex + 1;
    }

    private Element findRoot(Document document) {
        List<String> selectors = Arrays.asList(
            "article",
            ".docs-article",
            ".docs-content",
            "#content",
            ".content",
            "body"
        );
        for (String selector : selectors) {
            Element root = document.selectFirst(selector);
            if (root != null) {
                return root;
            }
        }
        return document.body();
    }

    private void cleanup(Element root) {
        root.select("script, style, noscript, header, footer, nav, .docs-toc, .toc, .breadcrumb, .navbar, .main-menu, .sidebar, .search-form, .edit-page, .page-meta").remove();
    }

    private String normalized(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private List<DocumentSpec> documentSpecs() {
        List<DocumentSpec> specs = new ArrayList<DocumentSpec>();
        specs.add(new DocumentSpec(
            "HDFS",
            "apache-hdfs-userguide",
            "Apache Hadoop HdfsUserGuide",
            "https://hadoop.apache.org/docs/current3/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html",
            "knowledge-docs/hdfs-userguide.html"
        ));
        specs.add(new DocumentSpec(
            "HDFS",
            "apache-hdfs-commands",
            "Apache Hadoop HDFS Commands Guide",
            "https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html",
            "knowledge-docs/hdfs-commands.html"
        ));
        specs.add(new DocumentSpec(
            "YARN",
            "apache-yarn-capacity-scheduler",
            "Apache Hadoop Capacity Scheduler",
            "https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/CapacityScheduler.html",
            "knowledge-docs/yarn-capacity-scheduler.html"
        ));
        specs.add(new DocumentSpec(
            "YARN",
            "apache-yarn-graceful-decommission",
            "Apache Hadoop Graceful Decommission of YARN Nodes",
            "https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/GracefulDecommission.html",
            "knowledge-docs/yarn-graceful-decommission.html"
        ));
        specs.add(new DocumentSpec(
            "HIVE_ON_TEZ",
            "apache-hive-metastore-admin",
            "Apache Hive Metastore Administration",
            "https://hive.apache.org/docs/latest/admin/adminmanual-metastore-administration/",
            "knowledge-docs/hive-metastore-admin.html"
        ));
        specs.add(new DocumentSpec(
            "HIVE_ON_TEZ",
            "apache-hive-ddl",
            "Apache Hive LanguageManual DDL",
            "https://hive.apache.org/docs/latest/language/languagemanual-ddl/",
            "knowledge-docs/hive-ddl.html"
        ));
        return specs;
    }

    private List<DirectorySpec> directorySpecs() {
        List<DirectorySpec> specs = new ArrayList<DirectorySpec>();
        specs.add(new DirectorySpec(
            "IMPALA",
            "apache-impala-topic",
            "Apache Impala Official Docs",
            IMPALA_TOPICS_BASE_URL,
            "knowledge-docs/impala-topics/*.html"
        ));
        return specs;
    }

    private static class DocumentSpec {
        private final String domain;
        private final String documentKey;
        private final String sourceName;
        private final String sourceUrl;
        private final String resourcePath;

        private DocumentSpec(String domain, String documentKey, String sourceName,
                             String sourceUrl, String resourcePath) {
            this.domain = domain;
            this.documentKey = documentKey;
            this.sourceName = sourceName;
            this.sourceUrl = sourceUrl;
            this.resourcePath = resourcePath;
        }
    }

    private static class DirectorySpec {
        private final String domain;
        private final String documentKeyPrefix;
        private final String sourceName;
        private final String sourceUrlPrefix;
        private final String resourcePattern;

        private DirectorySpec(String domain, String documentKeyPrefix, String sourceName,
                              String sourceUrlPrefix, String resourcePattern) {
            this.domain = domain;
            this.documentKeyPrefix = documentKeyPrefix;
            this.sourceName = sourceName;
            this.sourceUrlPrefix = sourceUrlPrefix;
            this.resourcePattern = resourcePattern;
        }
    }
}
