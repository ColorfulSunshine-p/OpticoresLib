package com.opticoreslib.boundary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the architectural boundary of the library.
 *
 * <p>The version neutral core must never reach into Minecraft, into Fabric or into
 * OptiCores. These tests inspect the compiled classes, so a violation fails the build even
 * when it is introduced indirectly.
 */
class PackageBoundaryTest {

	private static final List<String> CORE_PACKAGES = List.of(
			"com/opticoreslib/core",
			"com/opticoreslib/task",
			"com/opticoreslib/scheduler",
			"com/opticoreslib/concurrent",
			"com/opticoreslib/metrics",
			"com/opticoreslib/resources",
			"com/opticoreslib/integration");

	private static final List<String> FORBIDDEN_IN_CORE = List.of(
			"net/minecraft",
			"net/fabricmc",
			"org/lwjgl",
			"com/opticores/");

	@Test
	void corePackagesDoNotReferenceMinecraftFabricOrOptiCores() throws Exception {
		List<String> violations = new ArrayList<>();

		for (Path classFile : classFiles()) {
			String packageName = packageOf(classFile);
			if (!CORE_PACKAGES.contains(packageName)) {
				continue;
			}
			byte[] bytes = Files.readAllBytes(classFile);
			for (String forbidden : FORBIDDEN_IN_CORE) {
				if (contains(bytes, forbidden.getBytes(StandardCharsets.UTF_8))) {
					violations.add(packageName + " -> " + forbidden + " (" + classFile.getFileName() + ")");
				}
			}
		}

		assertTrue(violations.isEmpty(), "core packages must stay version neutral: " + violations);
	}

	@Test
	void noPackageReferencesOptiCoresItself() throws Exception {
		List<String> violations = new ArrayList<>();

		for (Path classFile : classFiles()) {
			byte[] bytes = Files.readAllBytes(classFile);
			if (contains(bytes, "com/opticores/".getBytes(StandardCharsets.UTF_8))) {
				violations.add(classFile.getFileName().toString());
			}
		}

		assertTrue(violations.isEmpty(), "the library must not depend on OptiCores: " + violations);
	}

	@Test
	void theLibraryDoesNotShipMixins() throws Exception {
		try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
			assertNotNull(stream, "fabric.mod.json must be present");
			String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			assertFalse(json.contains("\"mixins\""), "the library must not declare mixins");
			assertFalse(json.contains("ExampleMixin"), "the library must not ship the template mixin");
		}
	}

	@Test
	void theLibraryDoesNotDependOnOptionalMods() throws Exception {
		try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
			assertNotNull(stream);
			String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			for (String optionalMod : List.of("distant_horizons", "voxy", "chunky", "sodium", "iris")) {
				assertFalse(json.contains(optionalMod), "the library must not depend on " + optionalMod);
			}
		}
	}

	private static List<Path> classFiles() throws Exception {
		URL resource = PackageBoundaryTest.class.getClassLoader()
				.getResource("com/opticoreslib/core/Clock.class");
		assertNotNull(resource, "the compiled main classes must be on the test classpath");
		Path classFile = Path.of(resource.toURI());
		Path root = classFile.getParent().getParent().getParent().getParent();

		List<Path> files = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(root)) {
			stream.filter(path -> path.toString().endsWith(".class")).forEach(files::add);
		}
		assertFalse(files.isEmpty(), "no compiled classes were found");
		return files;
	}

	private static String packageOf(Path classFile) {
		Path root = classFile.getParent().getParent().getParent().getParent();
		return root.relativize(classFile.getParent()).toString().replace('\\', '/');
	}

	private static boolean contains(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}
}
