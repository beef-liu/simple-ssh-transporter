package com.badrabbitstudio.simplesshtransporter.server;

import com.badrabbitstudio.simplesshtransporter.App;
import com.badrabbitstudio.simplesshtransporter.util.IOUtil;
import com.badrabbitstudio.simplesshtransporter.util.InteractiveSsh;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author XingGu_Liu on 2019-06-09.
 */
public class LocalServer {
    private final static Log logger = LogFactory.getLog(LocalServer.class);

    private final static Random Rand = new Random(System.currentTimeMillis());
    private final static int[] LISTENPORT_RANGE = new int[] {60000, 65535};

    private final App.Args _args;

    private final AtomicBoolean _stopFlg = new AtomicBoolean(false);

    private final ExecutorService _socketWorkerThreads;
    private final Thread _localListener;
    private ServerSocket _serverSocket;
    private Socket _workSocket;

    private final List<InteractiveSsh> _sshAgents = new ArrayList<>();

    public LocalServer(App.Args args) {
        _args = args;

        int listenPort = -1;
        {
            InteractiveSsh.SshSetting sshSetting = new InteractiveSsh.SshSetting();

            InteractiveSsh.SshAuth sshAuth = new InteractiveSsh.SshAuth();
            sshAuth.setHost(args.shost);
            sshAuth.setPort(parseInt(args.sport));
            sshAuth.setUser(args.suser);
            sshAuth.setPrikey(args.sprik);
            sshAuth.setSshconfig(args.sconf);

            sshSetting.setSshAuth(sshAuth);

            if(args.s2host != null && !args.s2host.isEmpty()) {
                InteractiveSsh.PortForward portForward = new InteractiveSsh.PortForward();
                listenPort = Rand.nextInt(LISTENPORT_RANGE[1] - LISTENPORT_RANGE[0] + 1)
                        + LISTENPORT_RANGE[0];
                portForward.setListenPort(listenPort);
                portForward.setToHost(args.s2host);
                portForward.setToPort(parseInt(args.s2port));

                sshSetting.setPortForward(portForward);
            }


            InteractiveSsh ssh = new InteractiveSsh(sshSetting);
            _sshAgents.add(ssh);
        }

        if(listenPort > 0) {
            InteractiveSsh.SshSetting sshSetting = new InteractiveSsh.SshSetting();

            InteractiveSsh.SshAuth sshAuth = new InteractiveSsh.SshAuth();
            sshAuth.setHost("127.0.0.1");
            sshAuth.setPort(listenPort);
            sshAuth.setUser(args.s2user);

            sshSetting.setSshAuth(sshAuth);

            InteractiveSsh ssh = new InteractiveSsh(sshSetting);
            _sshAgents.add(ssh);
        }

        _localListener = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        doWork();
                    }
                },
                "LocalServer"
        );

        _socketWorkerThreads = Executors.newFixedThreadPool(2);
    }

    public void startScanInput() throws InterruptedException {
        final byte[] tempBuff = new byte[8192 * 2];
        final Charset charset = IOUtil.DefaultCharset;

        try {
            InputStream channelInput = _ssh.getInputStream();
            OutputStream channelOutput = _ssh.getOutputStream();
            final String prompt = "$ ";
            while(true) {
                logger.debug("waiting msg from channel");
                while(true) {
                    int readLen = channelInput.read(tempBuff);
                    if(readLen < 0) {
                        break;
                    }

                    if(readLen > 0) {
                        System.out.println(new String(tempBuff, 0, readLen, charset));
                    }
                }

                String inputLine = System.console().readLine(prompt);
                if(inputLine.length() == 0) {
                    continue;
                }

                final String cmd = inputLine.trim();
                if(cmd.equalsIgnoreCase("exit")) {
                    logger.info("exit by user inputing");
                    break;
                }
                channelOutput.write((cmd + "\r\n").getBytes(charset));
                logger.debug("cmd sent:" + cmd);
            }
        } catch (Throwable e) {
            logger.error("startScanInput", e);
        }
    }

    public void openRemoteChannel(String host, int port) {

    }

    public void start() {
        try {
            _sshAgents.get(0).connect();

            if(_sshAgents.size() > 1) {
                _sshAgents.get(1).connect();
            }

            startServerSocket();

            _localListener.start();
        } catch (Throwable e) {
            logger.error("error in start()", e);
            throw new RuntimeException("error in start()");
        }
    }

    public void shutdown() {
        try {
            _stopFlg.set(true);
            _localListener.interrupt();
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }

        try {
            for(InteractiveSsh ssh : _sshAgents) {
                ssh.disconnect();
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
        if(_args.lhost != null && !_args.lhost.isEmpty()) {
            listenHost = _args.lhost;
        }

        int port = parseInt(_args.lport);

        _serverSocket = new ServerSocket(
                port,
                backlog,
                InetAddress.getByName(listenHost)
        );

        _socketWorkerThreads.execute(new Runnable() {
            @Override
            public void run() {
                doSocketListen();
            }
        });

        _socketWorkerThreads.execute(new Runnable() {
            @Override
            public void run() {
                doSocketWrite();
            }
        });
    }

    private void doSocketListen() {
        try {
            byte[] tempBuff = new byte[8192 * 2];


            final Socket workSocket = _serverSocket.accept();
            _workSocket = workSocket;

            try {
                logger.info("client accepted");

                InputStream localInput = workSocket.getInputStream();
                OutputStream localOutput = workSocket.getOutputStream();

                final InteractiveSsh ssh = _sshAgents.get(_sshAgents.size() - 1);
                OutputStream channelOutput = ssh.getOutputStream();
                while (!_stopFlg.get()) {
                    if(!ssh.isConnected()) {
                        logger.info("ssh disconntected");
                        break;
                    }

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

    private void doSocketWrite() {
        try {
            byte[] tempBuff = new byte[8192 * 2];

            final InteractiveSsh ssh = _sshAgents.get(_sshAgents.size() - 1);
            while (true) {
                if(_workSocket == null) {
                    Thread.sleep(100);
                    continue;
                }
                if(!_workSocket.isConnected()) {
                    logger.debug("doSocketWrite() disconnected.");
                    break;
                }

                int readLen = ssh.getInputStream().read(tempBuff);
                if(readLen > 0) {
                    _workSocket.getOutputStream().write(tempBuff, 0, readLen);
                    _workSocket.getOutputStream().flush();

                    logger.debug("socket written:" + readLen);
                }
            }
        } catch (InterruptedException e) {
            logger.info("socket write interrupted");
        } catch (Throwable e) {
            logger.info("socket write error", e);
        }
    }


    private static int parseInt(String str) {
        if(str == null || str.isEmpty()) {
            return 0;
        }

        return Integer.parseInt(str);
    }

}
