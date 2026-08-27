package com.lani.demoncore.compat;

import net.neoforged.fml.ModList;

public final class ModCompat {

    private ModCompat() {
    }

    private static boolean resolved;

    private static boolean sodium;
    private static boolean embeddium;
    private static boolean iris;
    private static boolean entityCulling;
    private static boolean ferriteCore;
    private static boolean modernFix;

    private static void resolve() {
        if (resolved) {
            return;
        }
        ModList list = ModList.get();
        if (list == null) {
            return;
        }
        resolved = true;

        sodium = list.isLoaded("sodium");
        embeddium = list.isLoaded("embeddium");
        iris = list.isLoaded("iris") || list.isLoaded("oculus");
        entityCulling = list.isLoaded("entityculling");
        ferriteCore = list.isLoaded("ferritecore");
        modernFix = list.isLoaded("modernfix");
    }

    public static boolean hasChunkRenderer() {
        resolve();
        return sodium || embeddium;
    }

    public static boolean hasShaders() {
        resolve();
        return iris;
    }

    public static boolean hasEntityCulling() {
        resolve();
        return entityCulling;
    }

    public static boolean hasMemoryMod() {
        resolve();
        return ferriteCore || modernFix;
    }

    public static String getStats() {
        resolve();
        StringBuilder sb = new StringBuilder("Detected: ");
        boolean any = false;
        if (sodium) {
            sb.append("Sodium ");
            any = true;
        }
        if (embeddium) {
            sb.append("Embeddium ");
            any = true;
        }
        if (iris) {
            sb.append("Iris/Oculus ");
            any = true;
        }
        if (entityCulling) {
            sb.append("EntityCulling ");
            any = true;
        }
        if (ferriteCore) {
            sb.append("FerriteCore ");
            any = true;
        }
        if (modernFix) {
            sb.append("ModernFix ");
            any = true;
        }
        if (!any) {
            sb.append("none");
        }
        return sb.toString().trim();
    }
}
