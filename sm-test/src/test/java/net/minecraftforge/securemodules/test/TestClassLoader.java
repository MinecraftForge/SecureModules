/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.securemodules.test;

import net.minecraftforge.securemodules.SecureModuleClassLoader;
import net.minecraftforge.securemodules.SecureModuleFinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.module.ModuleFinder;
import java.util.List;

public class TestClassLoader {
    @Test
    void findsResourceFromNonBootLayer() throws Exception {
        var cfg = ModuleLayer.boot().configuration().resolveAndBind(SecureModuleFinder.of(), ModuleFinder.ofSystem(), List.of());
        var cl = new SecureModuleClassLoader("test", null, cfg, List.of(ModuleLayer.boot()));

        var layer = ModuleLayer.boot().defineModules(cfg, m -> cl); // Should we actually find the classloader from the parent layers?
                                                                    // It's used by the service loader to find module layers from classloaders
                                                                    // and other internal JVM stuff. It doesn't actually look like anything breaks
        assertNotNull(layer);

        var cls = Class.forName(cl.getClass().getName(), false, cl);
        assertNotNull(cls);

        var className = cl.getClass().getName().replace('.', '/') + ".class";
        var resource = cl.getResource(className);
        assertNotNull(resource);
    }
}
