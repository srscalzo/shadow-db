package com.shadowdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MigrationFileReader {

    private static final Pattern MIGRATION_PATTERN =
        Pattern.compile("^V(\\d+(?:\\.\\d+)*)__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);

    public record MigrationFile(String filename, String version, String description, Path path) {
        public List<Integer> versionParts() {
            return Arrays.stream(version.split("\\."))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        }
    }

    public List<MigrationFile> readAll(Path migrationsDir) {
        if (!Files.exists(migrationsDir)) {
            throw new IllegalArgumentException("Migrations directory does not exist: " + migrationsDir.toAbsolutePath());
        }
        if (!Files.isDirectory(migrationsDir)) {
            throw new IllegalArgumentException("Not a directory: " + migrationsDir.toAbsolutePath());
        }

        List<MigrationFile> files;
        try {
            files = Files.list(migrationsDir)
                .filter(Files::isRegularFile)
                .map(p -> parse(p))
                .filter(f -> f != null)
                .sorted(MigrationFileReader::compareByVersion)
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migrations directory: " + e.getMessage(), e);
        }

        if (files.isEmpty()) {
            throw new IllegalStateException("No Flyway migration files found in: " + migrationsDir.toAbsolutePath());
        }
        return files;
    }

    public MigrationFile identifyNewMigration(List<MigrationFile> all) {
        return all.get(all.size() - 1);
    }

    public List<MigrationFile> existingMigrations(List<MigrationFile> all, MigrationFile newMigration) {
        return all.stream()
            .filter(f -> !f.filename().equals(newMigration.filename()))
            .collect(Collectors.toList());
    }

    private static MigrationFile parse(Path path) {
        String filename = path.getFileName().toString();
        Matcher matcher = MIGRATION_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            return null;
        }
        return new MigrationFile(filename, matcher.group(1), matcher.group(2), path);
    }

    private static int compareByVersion(MigrationFile a, MigrationFile b) {
        List<Integer> aParts = a.versionParts();
        List<Integer> bParts = b.versionParts();
        int len = Math.max(aParts.size(), bParts.size());
        for (int i = 0; i < len; i++) {
            int av = i < aParts.size() ? aParts.get(i) : 0;
            int bv = i < bParts.size() ? bParts.get(i) : 0;
            int cmp = Integer.compare(av, bv);
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}
