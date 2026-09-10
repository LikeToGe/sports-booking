package com.sportsbooking.admin.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredPackageStructureTest {
    private static final Path BASE_SOURCE = Path.of("../sports-booking-base/src/main/java/com/sportsbooking/admin/base");
    private static final Path BIZ_SOURCE = Path.of("../sports-booking-biz/src/main/java/com/sportsbooking/admin/biz");
    private static final Path SERVER_SOURCE = Path.of("src/main/java/com/sportsbooking/admin/server");

    @Test
    void lowerLayersDoNotDependOnUpperLayers() throws IOException {
        assertNoImports(BASE_SOURCE, List.of(
                "com.sportsbooking.admin.biz",
                "com.sportsbooking.admin.server"
        ));
        assertNoImports(BIZ_SOURCE, List.of("com.sportsbooking.admin.server"));
    }

    @Test
    void transportModelsUseBoundarySuffixes() throws IOException {
        assertFilesEndWith(BIZ_SOURCE.resolve("pojo/request"), "Request.java");
        assertFilesEndWith(BIZ_SOURCE.resolve("pojo/vo"), "VO.java");
        assertFilesEndWith(BASE_SOURCE.resolve("bo"), "BO.java");
    }

    @Test
    void dataModelsAreOrdinaryClasses() throws IOException {
        assertNoRecordDeclarations(BASE_SOURCE);
        assertNoRecordDeclarations(BIZ_SOURCE);
        assertNoRecordDeclarations(SERVER_SOURCE);
    }

    private void assertNoImports(Path layer, List<String> forbiddenPackages) throws IOException {
        try (Stream<Path> files = Files.walk(layer)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String forbiddenPackage : forbiddenPackages) {
                    assertFalse(source.contains("import " + forbiddenPackage),
                            () -> file + " must not import " + forbiddenPackage);
                }
            }
        }
    }

    private void assertFilesEndWith(Path directory, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertTrue(file.getFileName().toString().endsWith(suffix),
                        () -> file + " must end with " + suffix);
            }
        }
    }

    private void assertNoRecordDeclarations(Path sourceRoot) throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(file).contains(" record "), () -> file + " must use class");
            }
        }
    }
}
