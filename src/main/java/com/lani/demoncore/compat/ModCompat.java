package com.lani.demoncore.compat;

import com.lani.demoncore.compat.chunk.ChunkModCompat;
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
    private static boolean immediatelyfast;
    private static boolean lithium;
    private static boolean radium;
    private static boolean canary;
    private static boolean c2me;
    private static boolean nvidium;
    private static boolean hasChunkOptimizer;

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
        immediatelyfast = list.isLoaded("immediatelyfast");
        lithium = list.isLoaded("lithium");
        radium = list.isLoaded("radium");
        canary = list.isLoaded("canary");
        c2me = list.isLoaded("c2me");
        nvidium = list.isLoaded("nvidium");
        hasChunkOptimizer = ChunkModCompat.hasChunkOptimizationMod();
    }

    public static boolean hasChunkRenderer() {
        resolve();
        return sodium || embeddium || nvidium;
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

    public static boolean hasImmediatelyFast() {
        resolve();
        return immediatelyfast;
    }

    public static boolean hasChunkOptimizerMod() {
        resolve();
        return hasChunkOptimizer;
    }

    public static boolean hasTickOptimizer() {
        resolve();
        return lithium || radium || canary || c2me;
    }

    public static String getChunkCompatStats() {
        return ChunkModCompat.getStats();
    }

    public static String getStats() {
        resolve();
        StringBuilder sb = new StringBuilder("Detected: ");
        boolean any = false;
        if (sodium) { sb.append("Sodium "); any = true; }
        if (embeddium) { sb.append("Embeddium "); any = true; }
        if (nvidium) { sb.append("Nvidium "); any = true; }
        if (iris) { sb.append("Iris/Oculus "); any = true; }
        if (entityCulling) { sb.append("EntityCulling "); any = true; }
        if (ferriteCore) { sb.append("FerriteCore "); any = true; }
        if (modernFix) { sb.append("ModernFix "); any = true; }
        if (hasTickOptimizer()) { sb.append("TickOpt(Lithium/C2ME/etc) "); any = true; }
        if (!any) {
            sb.append("none");
        }
        return sb.toString().trim();
    }
}
