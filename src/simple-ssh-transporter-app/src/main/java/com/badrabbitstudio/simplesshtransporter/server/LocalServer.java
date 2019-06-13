package com.badrabbitstudio.simplesshtransporter.server;

import com.badrabbitstudio.simplesshtransporter.util.IOUtil;
import com.badrabbitstudio.simplesshtransporter.util.InteractiveSsh;
import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author XingGu_Liu on 2019-06-09.
 */
public class LocalServer {
    private final static Log logger = LogFactory.getLog(LocalServer.class);

    private final LocalServerArgs _args;

    private final AtomicBoolean _stopFlg = new AtomicBoolean(false);

    private final Thread _worker;
    private ServerSocket _serverSocket;
    private final InteractiveSsh _ssh;

    public LocalServer(LocalServerArgs args) {
        _args = args;

        _ssh = new InteractiveSsh(_args.getSshAuth());

        _worker = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        doWork();
                    }
                },
                "LocalServer"
        );
    }

    public void startScanInput() throws InterruptedException {
        final byte[] tempBuff = new byte[8192 * 2];
        final Charset charset = IOUtil.DefaultCharset;

        /*
        while (!_stopFlg.get()) {
            Thread.sleep(1000);
        }
        */

        try {
            InputStream channelInput = _ssh.getInputStream();
            OutputStream channelOutput = _ssh.getOutputStream();
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
                if(cmd.equalsIgnoreCase("exit")) {
                    logger.info("exit by user inputing");
                    break;
                }
                channelOutput.write((cmd + "\r\n").getBytes(charset));
            }
        } catch (Throwable e) {
            logger.error("startScanInput", e);
        }
    }

    public void start() {
        try {
            _ssh.connect();

            startServerSocket();

            _worker.start();
        } catch (Throwable e) {
            logger.error("error in start()", e);
            throw new RuntimeException("error in start()");
        }
    }

    public void shutdown() {
        try {
            _stopFlg.set(true);
            _worker.interrupt();
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }

        try {
            if(_ssh != null) {
                _ssh.disconnect();
            }
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }

        try {
            if(_serverSocket != null) {
                _serverSocket.close();
            }
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }
    }

    private void startServerSocket() throws IOException {
        final int backlog = 1;
        String listenHost = "127.0.0.1";
        if(_args.getListenHost() != null && !_args.getListenHost().isEmpty()) {
            listenHost = _args.getListenHost();
        }

        int port = _args.getListenPort();

        _serverSocket = new ServerSocket(
                port,
                backlog,
                InetAddress.getByName(listenHost)
        );

        _worker.start();
    }

    private void doWork() {
        try {
            byte[] tempBuff = new byte[8192 * 2];


            InputStream channelInput = null;
            OutputStream channelOutput = null;

            InputStream localInput;
            OutputStream localOutput;
            final Socket workSocket = _serverSocket.accept();
            try {
                logger.info("client accepted");

                localInput = workSocket.getInputStream();
                localOutput = workSocket.getOutputStream();

                int disconnectedCnt = 0;
                while (!_stopFlg.get()) {
                    if(!_ssh.isConnected()) {
                        disconnectedCnt ++;
                        if((disconnectedCnt % 10) == 0) {
                            logger.info("in disconnected state. secnods:" + disconnectedCnt);
                        }
                        Thread.sleep(1000);
                        continue;
                    }

                    if(disconnectedCnt > 0) {
                        channelInput = _ssh.getInputStream();
                        channelOutput = _ssh.getOutputStream();
                    }

                    disconnectedCnt = 0;

                    while(localInput.available() > 0) {
                        logger.debug("localInput.available:" + localInput.available());

                        int readLen = localInput.read(tempBuff);

                        if(readLen < 0) {
                            logger.info("localInput EOF");
                            break;
                        }

                        if(readLen > 0) {
                            channelOutput.write(tempBuff, 0, readLen);
                            logger.info("localInput readLen:" + readLen);
                        }
                    }

                    logger.info("read from channel ...");
                    while (channelInput.available() > 0) {
                        logger.debug("channelInput.available:" + channelInput.available());

                        int readLen = channelInput.read(tempBuff);

                        if(readLen < 0) {
                            logger.info("channelInput EOF");
                            break;
                        }

                        if(readLen > 0) {
                            localOutput.write(tempBuff, 0, readLen);
                            logger.info("channelInput readLen:" + readLen);
                        }
                    }

                    Thread.sleep(1);
                }
            } finally {
                workSocket.close();
            }
        } catch (IOException e) {
            logger.info("IO error.", e);
        } catch (InterruptedException e) {
            logger.info("server interrupted.", e);
        }
    }
}
