package com.badrabbitstudio.simplesshtransporter.util;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AppBase {

    public final static Charset DefaultCharset = Charset.forName("utf-8");

    protected final static char ARG_START = '-';

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public static @interface ArgDesc {
        String desc() default "";
    }

    private final AtomicBoolean _destroyed = new AtomicBoolean(false);

    protected abstract Class<?> argType();
    //protected abstract Class<?> myAppType();

    protected abstract void process(Map<String, Object> argMap);

    protected abstract void destroy();

    public void printHelp() {
        final Class<?> argType = argType();
        final StringBuilder help = new StringBuilder();

        for (Field f : argType.getFields()) {
            String name = f.getName();
            ArgDesc desc = f.getAnnotation(ArgDesc.class);

            help.append("-").append(name).append(" ").append(desc.desc()).append("\n");
        }

        System.out.println(help);
    }

    protected void runMain(String[] args) {
        final Map<String, Object> argMap = parseArgs(args);
        /*
        if (argMap == null || argMap.size() == 0) {
            System.out.println(PrintColor.newBuilder()
                    .beginColor(PrintColor.RED)
                    .append("invalid args!")
                    .endColor()
                    .build());
            System.out.println();
            printHelp();
            System.exit(1);
            return;
        }
        */

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                /*
                System.out.println(PrintColor.newBuilder()
                        .beginColor(PrintColor.GREEN)
                        .append(App.class.getName() + " -> received kill signal. --------")
                        .endColor()
                        .build());
                */
                if (_destroyed.compareAndSet(false, true)) {
                    AppBase.this.destroy();
                }
            }
        });

        try {
            process(argMap);

            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            printHelp();
            System.exit(1);
        }
    }

    public static String readAsString(File file) throws IOException {
        byte[] bytes = readAsBytes(file);
        return new String(bytes, DefaultCharset);
    }

    public static byte[] readAsBytes(File file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InputStream input = new FileInputStream(file);
        try {
            byte[] buf = new byte[256];

            int readLen;
            while (true) {
                readLen = input.read(buf);

                if (readLen < 0) {
                    break;
                } else if (readLen > 0) {
                    bytes.write(buf, 0, readLen);
                }
            }

            return bytes.toByteArray();
        } finally {
            input.close();
        }
    }

    private static Map<String, Object> parseArgs(String[] args) {
        if (args == null || args.length == 0) {
            return new HashMap<>();
        }

        /*
        for(String arg : args) {
            logger.debug("arg:" + arg);
        }
        */

        HashMap<String, Object> argNameValMap = new HashMap<String, Object>();

        int i = args.length;
        while (i > 0) {
            i--;
            if (i == 0) {
                break;
            }

            String token = args[i];
            if (args[i].charAt(0) == ARG_START) {
                String name = token.substring(1);
                argNameValMap.put(name, "");
            } else {
                String name = args[--i];
                if (name.charAt(0) != ARG_START) {
                    throw new RuntimeException("Incorrect arg name:" + name);
                }

                name = name.substring(1);
                argNameValMap.put(name, unwrapQuote(token));
            }
        }

        return argNameValMap;
    }

    private static String unwrapQuote(String arg) {
        char c0 = arg.charAt(0);
        if (c0 == '\'' || c0 == '\"') {
            char c1 = arg.charAt(arg.length() - 1);

            if (c0 == c1) {
                return arg.substring(1, arg.length() - 1);
            }
        }

        return arg;
    }

}
