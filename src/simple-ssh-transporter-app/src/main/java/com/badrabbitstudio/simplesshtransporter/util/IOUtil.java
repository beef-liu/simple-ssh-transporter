package com.badrabbitstudio.simplesshtransporter.util;

import java.io.*;
import java.nio.charset.Charset;

public class IOUtil {
    public final static Charset DefaultCharset = Charset.forName("utf-8");


    public static byte[] readAsBytes(File inFile) throws IOException {
        FileInputStream input = new FileInputStream(inFile);
        try {
            return readAsBytes(input);
        } finally {
            input.close();
        }
    }

    public static byte[] readAsBytes(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedOutputStream output = new BufferedOutputStream(bytes);
        try {
            int readTotal = readAsBytes(input, output);

            return bytes.toByteArray();
        } finally {
            output.close();
        }
    }

    public static int readAsBytes(InputStream input, BufferedOutputStream output) throws IOException {
        byte[] tempBuff = new byte[4096];
        int readTotal = 0;
        while (true) {
            int readLen = input.read(tempBuff);
            if(readLen < 0) {
                break;
            }
            if(readLen > 0) {
                output.write(tempBuff, 0, readLen);
                readTotal += readLen;
            }
        }
        output.flush();

        return readTotal;
    }

    public static String readAsString(File file, Charset charset) throws IOException {
        return readAsString(new FileInputStream(file), charset);
    }

    public static String readAsString(InputStream input, Charset charset) throws IOException {
        Reader reader = new InputStreamReader(input, charset);
        try {
            return readAsString(reader);
        } finally {
            reader.close();
        }
    }

    public static String readAsString(Reader reader) throws IOException {
        StringBuilder str = new StringBuilder();
        char[] cBuf = new char[1024];

        int readLen;
        while(true) {
            readLen = reader.read(cBuf);

            if(readLen < 0) {
                break;
            }

            if(readLen > 0) {
                str.append(cBuf, 0, readLen);
            }
        }

        return str.toString();
    }

}
