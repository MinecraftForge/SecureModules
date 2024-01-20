/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.securemodules.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class TestClassStuff {
    @Test
    void testClass() throws MalformedURLException {
        var path = Paths.get("forge-1.16.5-36.1.16.jar");
        if (!Files.exists(path))
            return;
        final var cf = ModuleLayer.boot().configuration().resolveAndBind(ModuleFinder.of(path), ModuleFinder.ofSystem(), List.of("forge"));
        final var layer = ModuleLayer.defineModulesWithOneLoader(cf, List.of(ModuleLayer.boot()), new URLClassLoader(new URL[]{Paths.get("ge-1.16.5-36.1.16.jar").toUri().toURL()}));
        var is = layer.layer().configuration().findModule("forge").get();
        var m = layer.layer().findModule(is.name()).get();
        var cl = Class.forName(m, "net.minecraftforge.server.ServerMain");
        Assertions.assertNotNull(cl);
    }

    static class MyClassLoader extends ClassLoader {
        private final Configuration cf;

        MyClassLoader(Configuration cf) {
            this.cf = cf;
        }
        @Override
        protected Class<?> findClass(final String moduleName, final String name) {
            try {
                final var module = cf.findModule(moduleName).orElseThrow();
                final var reader = module.reference().open();
                final var bb = reader.read(name.replace('.','/')+".class").orElseThrow();
                var bytes = new byte[bb.remaining()];
                bb.get(bytes);
                reader.release(bb);
                return defineClass(name, bytes, 0, bytes.length, null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
