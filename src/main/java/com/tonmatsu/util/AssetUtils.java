package com.tonmatsu.util;

import java.io.*;

public final class AssetUtils {
    private static final ClassLoader CL = AssetUtils.class.getClassLoader();

    private AssetUtils() {
    }

    public static InputStream getStream(String name) {
        final var stream = CL.getResourceAsStream(name);
        if (stream == null)
            throw new RuntimeException("could not get asset as stream! " + name);
        return stream;
    }

    public static BufferedReader getReader(String name) {
        return new BufferedReader(new InputStreamReader(getStream(name)));
    }

    public static String getString(String name) {
        try (final var reader = getReader(name)) {
            final var sb = new StringBuilder();
            while (true) {
                final var line = reader.readLine();
                if (line == null)
                    break;
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("could not get asset as string! " + name, e);
        }
    }
}
