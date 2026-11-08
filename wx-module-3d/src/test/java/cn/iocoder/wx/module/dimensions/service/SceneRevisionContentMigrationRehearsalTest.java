package cn.iocoder.wx.module.dimensions.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用内存行演练 DATA-08 兼容期回填，不创建数据库、不连接148。
 * 验证同租户共享、跨租户隔离，以及从共享正文恢复旧四包的字节一致性。
 */
class SceneRevisionContentMigrationRehearsalTest {
    @Test
    void sameTenantSharesContentButDifferentTenantsDoNot() {
        List<LegacyRow> revisions = List.of(
                row(100L, 1, "园区A"), row(100L, 2, "园区A"), row(100L, 3, "园区B"),
                row(100L, 4, "园区B"), row(200L, 1, "园区A"), row(200L, 2, "园区A"));
        Map<ContentKey, ContentRow> contents = backfill(revisions);

        assertEquals(3, contents.size());
        assertEquals(2, contents.keySet().stream().map(ContentKey::tenantId).distinct().count());
        assertEquals(2, revisions.stream().filter(row -> row.tenantId() == 100L)
                .map(row -> row.contentId()).distinct().count());
        assertEquals(1, revisions.stream().filter(row -> row.tenantId() == 200L)
                .map(row -> row.contentId()).distinct().count());
        assertNotEquals(revisions.get(0).contentId(), revisions.get(4).contentId(), "跨租户不得共享正文行");
    }

    @Test
    void backfillPreservesFourPackagesAndDigestForRollback() {
        List<LegacyRow> revisions = List.of(row(100L, 1, "含中文正文"), row(100L, 2, "含中文正文"));
        Map<ContentKey, ContentRow> contents = backfill(revisions);

        for (LegacyRow revision : revisions) {
            ContentRow content = contents.values().stream()
                    .filter(candidate -> candidate.id() == revision.contentId()).findFirst().orElseThrow();
            assertEquals(revision.packages(), content.packages());
            assertEquals(revision.contentHash(), content.contentHash());
            assertEquals(revision.contentBytes(), content.contentBytes());
            assertEquals(SceneRevisionContentHasher.sha256(revision.schemaVersion(), revision.packages().get(0),
                    revision.packages().get(1), revision.packages().get(2), revision.packages().get(3)), content.contentHash());
        }
    }

    private static Map<ContentKey, ContentRow> backfill(List<LegacyRow> revisions) {
        Map<ContentKey, ContentRow> contents = new LinkedHashMap<>();
        long nextId = 9000L;
        for (LegacyRow revision : revisions) {
            String hash = SceneRevisionContentHasher.sha256(revision.schemaVersion(), revision.packages().get(0),
                    revision.packages().get(1), revision.packages().get(2), revision.packages().get(3));
            long bytes = SceneRevisionContentHasher.contentBytes(revision.packages().get(0), revision.packages().get(1),
                    revision.packages().get(2), revision.packages().get(3));
            ContentKey key = new ContentKey(revision.tenantId(), hash, revision.schemaVersion());
            ContentRow content = contents.get(key);
            if (content == null) {
                content = new ContentRow(nextId++, revision.packages(), hash, bytes);
                contents.put(key, content);
            }
            revision.attach(content.id(), hash, bytes);
        }
        return contents;
    }

    private static LegacyRow row(long tenantId, int revisionNo, String name) {
        List<String> packages = new ArrayList<>(List.of(
                "{\"sceneType\":\"gis\",\"name\":\"" + name + "\"}",
                "{\"nodes\":[{\"id\":\"root\"}]}", "{\"layers\":[]}", "{\"resources\":[]}"));
        return new LegacyRow(tenantId, revisionNo, 1, packages);
    }

    private record ContentKey(long tenantId, String hash, int schemaVersion) {
    }

    private record ContentRow(long id, List<String> packages, String contentHash, long contentBytes) {
    }

    private static final class LegacyRow {
        private final long tenantId;
        private final int revisionNo;
        private final int schemaVersion;
        private final List<String> packages;
        private long contentId;
        private String contentHash;
        private long contentBytes;

        private LegacyRow(long tenantId, int revisionNo, int schemaVersion, List<String> packages) {
            this.tenantId = tenantId;
            this.revisionNo = revisionNo;
            this.schemaVersion = schemaVersion;
            this.packages = List.copyOf(packages);
        }

        long tenantId() { return tenantId; }
        int revisionNo() { return revisionNo; }
        int schemaVersion() { return schemaVersion; }
        List<String> packages() { return packages; }
        long contentId() { return contentId; }
        String contentHash() { return contentHash; }
        long contentBytes() { return contentBytes; }

        void attach(long contentId, String contentHash, long contentBytes) {
            this.contentId = contentId;
            this.contentHash = contentHash;
            this.contentBytes = contentBytes;
        }
    }
}
