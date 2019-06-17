package com.badrabbitstudio.simplesshtransporter.server;

import com.alibaba.fastjson.JSON;
import com.badrabbitstudio.simplesshtransporter.App;
import com.badrabbitstudio.simplesshtransporter.util.InteractiveSsh;
import com.badrabbitstudio.simplesshtransporter.util.TcpServerFor1Client;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

//    private final Thread _localListener;
//    private final ExecutorService _socketWorkerThreads;
//    private ServerSocket _serverSocket;
//    private Socket _workSocket;

    private InteractiveSsh _sshAgent = null;
    private final TcpServerFor1Client _tcpServer;
    private final Thread _sshReadWorker;

    private InputStream _channelInput;
    private OutputStream _channelOutput;
    private ByteBuffer _bufferForInitResponse;

    public LocalServer(App.Args args) {
        _args = args;
        _bufferForInitResponse = ByteBuffer.allocate(8192 * 2);
        logger.debug(""
                + " buffer.pos:" + _bufferForInitResponse.position()
                + " limit:" + _bufferForInitResponse.limit()
                + " remaining:" + _bufferForInitResponse.remaining()
        );

        _sshAgent = new InteractiveSsh();

        _tcpServer = createTcpServer(args);

        _sshReadWorker = new Thread(() -> {
            loopSshRead();
        }, "_sshReadWorker"
        );
    }

    public void start() {
        try {
            startSsh();

            _tcpServer.start();

            _sshReadWorker.start();

            //wait for exit
            while (true) {
                String cmd = System.console().readLine( "$ ");
                if("exit,bye,byebye,quit".contains(cmd.toLowerCase())) {
                    break;
                } else {
                    System.out.println("please input 'exit' when you want to end this process");
                }
            }
        } catch (Throwable e) {
            logger.error("error in start()", e);
        }
    }

    public void shutdown() {

        try {
            if(_tcpServer != null) {
                _tcpServer.shutdown();
            }
        } catch (Throwable e) {
            logger.error("error in _tcpServer.shutdown()", e);
        }

        try {
            _sshAgent.close();
        } catch (Throwable e) {
            logger.error("error in ssh.disconnect()", e);
        }

        try {
            _stopFlg.set(true);
        } catch (Throwable e) {
            logger.error(null, e);
        }
    }

    private void startSsh() throws IOException, JSchException, InterruptedException {
        App.Args args = _args;

        // the 1st session
        Session session1 = null;
        Session session2 = null;

        {
            InteractiveSsh.SshAuth sshAuth = new InteractiveSsh.SshAuth();
            sshAuth.setHost(args.shost);
            sshAuth.setPort(parseInt(args.sport));
            sshAuth.setUser(args.suser);
            sshAuth.setPrikey(args.sprik);
            sshAuth.setSshconfig(args.sconf);

            session1 = _sshAgent.connect(sshAuth);
        }

        // the 2nd session
        if(args.s2host != null && !args.s2host.isEmpty()) {
            InteractiveSsh.PortForward portForward = new InteractiveSsh.PortForward();
            //random port
            final int listenPort = Rand.nextInt(LISTENPORT_RANGE[1] - LISTENPORT_RANGE[0] + 1)
                    + LISTENPORT_RANGE[0];
            portForward.setListenPort(listenPort);
            portForward.setToHost(args.s2host);
            portForward.setToPort(parseInt(args.s2port));

            // portforwardL: a server socket will listen at listenPort
            _sshAgent.portForwardL(session1, portForward);

            //session2
            InteractiveSsh.SshAuth sshAuth = new InteractiveSsh.SshAuth();
            sshAuth.setHost("127.0.0.1");
            sshAuth.setPort(listenPort);
            sshAuth.setUser(args.s2user);
            sshAuth.setSshconfig("~/.ssh/config");

            session2 = _sshAgent.connect(sshAuth);
        }

        //open channel
        {
            String cmd = _args.tcmd
                    .replace("$h", _args.thost)
                    .replace("$p", _args.tport)
                    ;

            Session session = session2 == null ? session1 : session2;
            //Channel channel = session.openChannel("shell");
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(cmd);
            channel.setInputStream(null);

            InputStream channelInput = channel.getInputStream();
            OutputStream channelOutput = channel.getOutputStream();

            _channelInput = channelInput;
            _channelOutput = channelOutput;

            channel.connect(5000);
        }
    }

    private TcpServerFor1Client createTcpServer(App.Args args) {
        final int backlog = 1;
        String listenHost = "127.0.0.1";
        if(args.lhost != null && !args.lhost.isEmpty()) {
            listenHost = args.lhost;
        }

        int port = parseInt(args.lport);

        return new TcpServerFor1Client(
                listenHost, port,
                new TcpServerFor1Client.ITcpListenHandler() {
                    @Override
                    public void onAccept() {
                        synchronized (_bufferForInitResponse) {
                            try {
                                _bufferForInitResponse.flip();
                                if(_bufferForInitResponse.hasRemaining()) {
                                    _tcpServer.sendToClient(
                                            _bufferForInitResponse.array(),
                                            _bufferForInitResponse.position(),
                                            _bufferForInitResponse.limit()
                                    );
                                    logger.info("onAccept()"
                                                    + " sendToClient:\n" + new String(
                                                    _bufferForInitResponse.array(),
                                                    _bufferForInitResponse.position(),
                                                    _bufferForInitResponse.limit()
                                            )
                                    );
                                }
                            } catch (Throwable e) {
                                logger.error("onAccept()", e);
                            }

                            _bufferForInitResponse.clear();
                        }
                    }

                    @Override
                    public void onDisconnected() {
                    }
                },
                (byte[] receivedBuff, int offset, int len) -> {
                    onTcpRead(receivedBuff, offset, len);
                });
    }


    private void onTcpRead(byte[] receivedBuff, int offset, int len) {
        try {
            writeSshChannel(receivedBuff, offset, len);
        } catch (Throwable e) {
            logger.error("onTcpRead()", e);
        }
    }

    private void loopSshRead() {
        byte[] tempBuff = new byte[8192 * 2];
        synchronized (_bufferForInitResponse) {
            _bufferForInitResponse.clear();
            logger.debug("loopSshRead() - 0"
                    + " buffer.pos:" + _bufferForInitResponse.position()
                    + " limit:" + _bufferForInitResponse.limit()
                    + " remaining:" + _bufferForInitResponse.remaining()
            );
        }

        try {
            while (!_stopFlg.get()) {
                if(Thread.interrupted()) {
                    logger.info("loopSshRead() break by interrupted");
                    break;
                }

                int readLen = _channelInput.read(tempBuff);
                if(readLen < 0) {
                    logger.debug("loopSshRead() _channelInput read EOF");
                    break;
                }

                //logger.debug("loopSshRead() readLen(bytes):" + readLen);
                if(readLen > 0) {
                    if(_tcpServer.isClientConnected()) {
                        _tcpServer.sendToClient(tempBuff, 0, readLen);
                        logger.debug("loopSshRead() sendToClient(bytes):" + readLen);
                    } else {
                        System.out.write(tempBuff, 0, readLen);
                        synchronized (_bufferForInitResponse) {
                            logger.debug("loopSshRead() - 1"
                                    + " buffer.pos:" + _bufferForInitResponse.position()
                                    + " limit:" + _bufferForInitResponse.limit()
                                    + " remaining:" + _bufferForInitResponse.remaining()
                            );
                            _bufferForInitResponse.put(tempBuff, 0, readLen);
                            logger.debug("loopSshRead()"
                                    + " saveToBuffer(bytes):" + readLen
                                    + " buffer.pos:" + _bufferForInitResponse.position()
                                    + " limit:" + _bufferForInitResponse.limit()
                                    + " remaining:" + _bufferForInitResponse.remaining()
                            );
                        }
                    }
                }
            }

            logger.info("loopSshRead() end");
        } catch (Throwable e) {
            logger.error("loopSshRead()", e);
        }
    }

    private void writeSshChannel(byte[] buff, int offset, int len) throws IOException {
        OutputStream output = _channelOutput;
        output.write(buff, offset, len);
        output.flush();
    }

    private static int parseInt(String str) {
        if(str == null || str.isEmpty()) {
            return 0;
        }

        return Integer.parseInt(str);
    }

}
