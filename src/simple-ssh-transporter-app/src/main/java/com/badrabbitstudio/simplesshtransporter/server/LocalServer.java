package com.badrabbitstudio.simplesshtransporter.server;

import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
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
    private ChannelDirectTCPIP _transChannel;

    public LocalServer(LocalServerArgs args) {
        _args = args;

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

    public void awaitToShutdown() throws InterruptedException {
        while (!_stopFlg.get()) {
            Thread.sleep(1000);
        }
    }

    public void start() {
        try {
            startSsh();

            startServerSocket();

        } catch (Throwable e) {
            throw new RuntimeException("");
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
            _transChannel.disconnect();
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }

        try {
            _serverSocket.close();
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }
    }

    private void startSsh() throws JSchException {
        LocalServerArgs.SshSetting sshSetting = _args.getSshSetting();
        final int timeout = 5000;

        JSch ssh = new JSch();
        //ssh.setKnownHosts();

        final Session session = ssh.getSession(sshSetting.getUser(), sshSetting.getHost(), sshSetting.getPort());
        if(sshSetting.getPrikey() != null && !sshSetting.getPrikey().isEmpty()) {
            //ssh.addIdentity(sshSetting.getPrikey(), sshSetting.getPassphrase());
            ssh.addIdentity(sshSetting.getPrikey());
            logger.info("ssh auth with prikey:" + sshSetting.getPrikey());
        } else {
            //session.setPassword(sshSetting.getPassword());
            logger.info("ssh auth with password.");
        }

        session.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new UserInfo() {
            private String _passphrase;
            private String _password;
            private String _yesno;

            private String scanInput(String prompt) {
                final Scanner scanner = new Scanner(System.in);

                System.out.println(prompt);
                String inputLine = scanner.nextLine();

                scanner.close();
                return inputLine;
            }

            @Override
            public String getPassphrase() {
                return _passphrase;
            }

            @Override
            public String getPassword() {
                return _password;
            }

            @Override
            public boolean promptPassword(String s) {
                _password = scanInput(s);
                return _password.isEmpty();
            }

            @Override
            public boolean promptPassphrase(String s) {
                _passphrase = scanInput(s);
                return _passphrase.isEmpty();
            }

            @Override
            public boolean promptYesNo(String s) {
                _yesno = scanInput(s);
                return _yesno.isEmpty();
            }

            @Override
            public void showMessage(String s) {
                System.out.println(s);
            }
        });

        session.connect(timeout);


        _transChannel = (ChannelDirectTCPIP) session.openChannel("direct-tcpip");

        LocalServerArgs.TcpEndpoint targetPort = _args.getTargetPort();
        _transChannel.setHost(targetPort.getHost());
        _transChannel.setPort(targetPort.getPort());

        _transChannel.connect(timeout);
        logger.info("tcp channel openned");
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

            InputStream channelInput = _transChannel.getInputStream();
            OutputStream channelOutput = _transChannel.getOutputStream();

            InputStream localInput;
            OutputStream localOutput;
            final Socket workSocket = _serverSocket.accept();
            try {
                logger.info("client accepted");

                localInput = workSocket.getInputStream();
                localOutput = workSocket.getOutputStream();

                while (!_stopFlg.get()) {
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
