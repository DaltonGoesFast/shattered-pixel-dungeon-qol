/*
 * Scroll of Debug
 * Copyright (C) Zrp200
 *
 * https://github.com/Zrp200/ScrollOfDebug
 *
 * Local Android helper: read class descriptors out of a dex file so short
 * command names can resolve when the classpath cannot be listed.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zrp200.scrollofdebug;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/** Pulls binary class names for one package out of a dex image. */
public final class DexClassNames {

    private DexClassNames() {}

    public static void collect(byte[] dex, String packageName, Collection<String> out) {
        if (dex == null || packageName == null || dex.length < 0x70) return;
        if (dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x') return;

        int count = u32(dex, 0x38);
        int table = u32(dex, 0x3c);
        if (count <= 0 || table < 0) return;

        byte[] prefix = ("L" + packageName.replace('.', '/') + "/").getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < count; i++) {
            int idPos = table + i * 4;
            if (idPos < 0 || idPos + 4 > dex.length) return;
            int dataOff = u32(dex, idPos);
            if (dataOff < 0 || dataOff >= dex.length) continue;
            int start = skipUleb(dex, dataOff);
            if (start < 0 || !startsWith(dex, start, prefix)) continue;

            int end = start;
            boolean ok = true;
            while (end < dex.length && dex[end] != 0) {
                if (dex[end] == ';') break;
                int b = dex[end] & 0xff;
                if (b >= 0x80 || dex[end] == '[' || dex[end] == '(') {
                    ok = false;
                    break;
                }
                end++;
            }
            if (!ok || end >= dex.length || dex[end] != ';') continue;

            StringBuilder name = new StringBuilder(end - (start + 1));
            for (int c = start + 1; c < end; c++) {
                name.append(dex[c] == '/' ? '.' : (char) dex[c]);
            }
            out.add(name.toString());
        }
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset < 0 || offset + prefix.length > data.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    private static int skipUleb(byte[] dex, int p) {
        for (int guard = 0; p < dex.length && guard < 5; guard++) {
            int v = dex[p++] & 0xff;
            if ((v & 0x80) == 0) return p;
        }
        return -1;
    }

    private static int u32(byte[] dex, int offset) {
        return (dex[offset] & 0xff)
                | ((dex[offset + 1] & 0xff) << 8)
                | ((dex[offset + 2] & 0xff) << 16)
                | ((dex[offset + 3] & 0xff) << 24);
    }
}
