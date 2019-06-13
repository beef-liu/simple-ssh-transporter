package com.badrabbitstudio.simplesshtransporter;

import com.alibaba.fastjson.JSON;
import com.badrabbitstudio.simplesshtransporter.util.IOUtil;
import com.badrabbitstudio.simplesshtransporter.util.InteractiveSsh;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author XingGu_Liu on 2019-06-13.
 */
public class TestSsh {

    @Test
    public void test_ssh() {
        try {
            File settingFile = new File("temp", "sshAuth.json");
            System.out.println("settingFile:" + settingFile);

            InteractiveSsh.SshAuth sshAuthSetting = JSON.parseObject(
                    IOUtil.readAsString(settingFile, IOUtil.DefaultCharset),
                    InteractiveSsh.SshAuth.class
            );

            final byte[] tempBuff = new byte[8192 * 2];
            final Charset charset = IOUtil.DefaultCharset;

            InteractiveSsh ssh = new InteractiveSsh(sshAuthSetting);
            try {
                ssh.connect();

                InputStream channelInput = ssh.getInputStream();
                OutputStream channelOutput = ssh.getOutputStream();
                final String prompt = "$ ";
                while(true) {
                    String inputLine = System.console().readLine(prompt);
                    if(inputLine.length() == 0) {
                        continue;
                    }

                    if(channelInput.available() > 0) {
                        int readLen = channelInput.read(tempBuff);
                        System.out.println(new String(tempBuff, 0, readLen, charset));
                    }


                    final String cmd = inputLine.trim();
                    /*
                    if(cmd.equalsIgnoreCase("exit")) {
                        System.out.println("bye bye");
                        break;
                    }
                    */
                    channelOutput.write((cmd + "\r\n").getBytes(charset));
                }
            } finally {
                ssh.close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
