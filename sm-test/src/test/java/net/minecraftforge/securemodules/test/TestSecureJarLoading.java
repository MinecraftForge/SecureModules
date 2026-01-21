/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.securemodules.test;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.Jar;
import cpw.mods.jarhandling.impl.SecureJarVerifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TestSecureJarLoading {
    static final String FINGERPRINT = "c565ada57942d5be47f926fceeae539dbc8c1f4b";

    @Test // All files are signed
    void testSecureJar() throws Exception {
        var seen = processEntries("signed", (jar, zip, name) -> {
            var cs = ((Jar)jar).verifyAndGetSigners(name, zip.readAllBytes());
            var manifest = jar.getTrustedManifestEntries(name);

            assertNotNull(cs, "Missing code signers array for " + name);
            assertEquals(cs.length, 1, "Expected only one code signer for " + name);
            var cert = cs[0].getSignerCertPath().getCertificates().get(0);
            assertEquals(FINGERPRINT, hash(cert.getEncoded()), "Unexpected Certificate fingerprint");
            assertNotNull(manifest, "Missing trusted manifest entries");
        });
        assertEquals(List.of("test/Signed.class"), seen, "Mising Signed class");
    }

    @Test // Nothing is signed
    void testUnsignedJar() throws Exception {
        var seen = processEntries("unsigned", (jar, zip, name) -> {
            var cs = ((Jar)jar).verifyAndGetSigners(name, zip.readAllBytes());
            assertNull(cs, "Unexpected Code Signers");
        });
        assertEquals(List.of("test/Signed.class", "test/UnSigned.class"), seen, "Mising Expected classes");
    }

    @Test
    void testNotJar(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "TEST_ MARKER", StandardCharsets.UTF_8);
        var jar = SecureJar.from(tempDir);
        assertFalse(jar.hasSecurityData(), "Jar is marked secure");
        assertTrue(jar.moduleDataProvider().getManifest().getMainAttributes().isEmpty(), "Non-Empty manifest returned");
    }

    @Test
    void testNonExistent() throws Exception {
        final var path = Paths.get("thisdoesnotexist");
        assertThrows(UncheckedIOException.class, ()->SecureJar.from(path), "File does not exist");
    }

    @Test // Has a file that is signed, but modified
    void testTampered() throws Exception {
        var seen = processEntries("invalid", (jar, zip, name) -> {
            var cs = ((Jar)jar).verifyAndGetSigners(name, zip.readAllBytes());
            assertNull(cs, "Unexpected Code Signers");
        });
        assertEquals(List.of("test/Signed.class"), seen, "Mising Signed class");
    }

    @Test // Contained a signed file, as well as a unsigned file.
    void testPartial() throws Exception {
        var seen = processEntries("partial", (jar, zip, name) -> {
            var cs = ((Jar)jar).verifyAndGetSigners(name, zip.readAllBytes());
            var manifest = jar.getTrustedManifestEntries(name);

            if ("test/Signed.class".equals(name)) {
                assertNotNull(cs, "Missing code signers array for " + name);
                assertEquals(cs.length, 1, "Expected only one code signer for " + name);
                var cert = cs[0].getSignerCertPath().getCertificates().get(0);
                assertEquals(FINGERPRINT, hash(cert.getEncoded()), "Unexpected Certificate fingerprint");
                assertNotNull(manifest, "Missing trusted manifest entries");
            } else {
                assertNull(cs, "Unexpected Code Signers");
            }
        });
        assertEquals(List.of("test/Signed.class", "test/UnSigned.class"), seen, "Mising Expected classes");
    }

    @Test // Has a jar with only a manifest
    void testEmptyJar() throws Exception {
        var seen = processEntries("empty", (jar, zip, name) -> {
            var cs = ((Jar)jar).verifyAndGetSigners(name, zip.readAllBytes());
            assertNull(cs, "Unexpected Code Signers");
        });
        assertEquals(List.of(), seen, "Unexpected contents in empty jar");
    }

    @Test // Jar has invalid URL characters, Java doesn't escape []'s properly so we need to force it to
    void testInvalidUri() throws Exception {
        var path = Paths.get("src/test/resources/invalid url[]%20.jar");
        var fileUri = path.toUri();
        var jar = (Jar)SecureJar.from(path);
        var uri = jar.getURI();
        assertEquals("jar:" + fileUri.toString() + "!/", uri.toString());
        var fs = FileSystems.getFileSystem(uri);
        assertEquals(jar.getRootPath().getFileSystem(), fs, "Non-matching file systems after URI cycle");
    }

    @Test // Test opening the same file multiple times.
    void testSameJar() throws Exception {
        var path = Paths.get("src/test/resources/empty.jar");
        var jar1 = SecureJar.from(path);
        assertNotNull(jar1);
        var jar2 = SecureJar.from(path);
        assertNotNull(jar2);
    }


    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R sneak(Throwable e) throws E {
        throw (E)e;
    }

    static String hash(byte[] data) {
        try {
            var sha = MessageDigest.getInstance("SHA-1");
            var digest = sha.digest(data);
            return SecureJarVerifier.toHexString(digest);
        } catch (NoSuchAlgorithmException e) {
            return sneak(e);
        }
    }

    private interface EntryConsumer {
        void accept(SecureJar jar, ZipInputStream zip, String name) throws Exception;
    }

    private List<String> processEntries(String jarName, EntryConsumer consumer) throws Exception {
        var seen = new ArrayList<String>();
        var path = Paths.get("src/test/resources/" + jarName + ".jar");
        var jar = SecureJar.from(path);
        try (var in = Files.newInputStream(path);
             var zin = new ZipInputStream(in)) {

            for (var entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                var name = entry.getName();
                if (entry.isDirectory() || SecureJarVerifier.isSigningRelated(name))
                    continue;

                seen.add(name);
                consumer.accept(jar, zin, name);
            }
        }
        return seen;
    }
}
