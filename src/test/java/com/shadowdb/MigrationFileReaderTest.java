package com.shadowdb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationFileReaderTest {

    private final MigrationFileReader reader = new MigrationFileReader();

    @Test
    void readsAndSortsNumerically(@TempDir Path dir) throws IOException {
        createFile(dir, "V10__big.sql");
        createFile(dir, "V2__second.sql");
        createFile(dir, "V1__first.sql");
        createFile(dir, "V9__ninth.sql");

        List<MigrationFileReader.MigrationFile> files = reader.readAll(dir);

        assertEquals(4, files.size());
        assertEquals("1",  files.get(0).version());
        assertEquals("2",  files.get(1).version());
        assertEquals("9",  files.get(2).version());
        assertEquals("10", files.get(3).version());
    }

    @Test
    void ignoresNonMigrationFiles(@TempDir Path dir) throws IOException {
        createFile(dir, "V1__init.sql");
        createFile(dir, "README.md");
        createFile(dir, "afterMigrate.sql");

        List<MigrationFileReader.MigrationFile> files = reader.readAll(dir);

        assertEquals(1, files.size());
        assertEquals("V1__init.sql", files.get(0).filename());
    }

    @Test
    void autoDetectsNewMigrationAsHighestVersion(@TempDir Path dir) throws IOException {
        createFile(dir, "V1__first.sql");
        createFile(dir, "V2__second.sql");
        createFile(dir, "V3__third.sql");

        List<MigrationFileReader.MigrationFile> all = reader.readAll(dir);
        MigrationFileReader.MigrationFile newMig = reader.identifyNewMigration(all);

        assertEquals("V3__third.sql", newMig.filename());
    }

    @Test
    void existingMigrationsExcludesNew(@TempDir Path dir) throws IOException {
        createFile(dir, "V1__first.sql");
        createFile(dir, "V2__second.sql");
        createFile(dir, "V3__third.sql");

        List<MigrationFileReader.MigrationFile> all = reader.readAll(dir);
        MigrationFileReader.MigrationFile newMig = reader.identifyNewMigration(all);
        List<MigrationFileReader.MigrationFile> existing = reader.existingMigrations(all, newMig);

        assertEquals(2, existing.size());
        assertTrue(existing.stream().noneMatch(f -> f.filename().equals("V3__third.sql")));
    }

    @Test
    void throwsWhenDirectoryDoesNotExist() {
        assertThrows(IllegalArgumentException.class,
            () -> reader.readAll(Path.of("/nonexistent/path")));
    }

    @Test
    void throwsWhenNoMigrationFilesFound(@TempDir Path dir) throws IOException {
        createFile(dir, "README.md");
        assertThrows(IllegalStateException.class, () -> reader.readAll(dir));
    }

    @Test
    void parsesMultiSegmentVersions(@TempDir Path dir) throws IOException {
        createFile(dir, "V1.1__patch.sql");
        createFile(dir, "V1.2__patch2.sql");
        createFile(dir, "V1.10__patch10.sql");

        List<MigrationFileReader.MigrationFile> files = reader.readAll(dir);

        assertEquals("1.1",  files.get(0).version());
        assertEquals("1.2",  files.get(1).version());
        assertEquals("1.10", files.get(2).version());
    }

    private void createFile(Path dir, String name) throws IOException {
        Files.createFile(dir.resolve(name));
    }
}
