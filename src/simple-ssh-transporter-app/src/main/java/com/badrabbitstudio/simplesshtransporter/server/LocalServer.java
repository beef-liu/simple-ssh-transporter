package com.badrabbitstudio.simplesshtransporter.server;

import com.badrabbitstudio.simplesshtransporter.App;
import com.badrabbitstudio.simplesshtransporter.util.InteractiveSsh;
import com.badrabbitstudio.simplesshtransporter.util.TcpServerFor1Client;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private final List<InteractiveSsh> _sshAgents = new ArrayList<>();
    private InteractiveSsh _lastSsh = null;
    private final TcpServerFor1Client _tcpServer;
    private final Thread _sshReadWorker;

    public LocalServer(App.Args args) {
        _args = args;

        initSsh(args);

        _tcpServer = createTcpServer(args);

        _sshReadWorker = new Thread(() -> {

        }, "_sshReadWorker"
        );
    }

    public void start() {
        try {
            for(InteractiveSsh ssh : _sshAgents) {
                try {
                    ssh.connect();
                } catch (Throwable e) {
                    logger.error("", e);
                }
            }

            //open tunnel
            {
                {
                    String cmd = _args.tcmd
                            .replace("$h", _args.thost)
                            .replace("$p", _args.tport)
                            ;
                    byte[] cmdBytes = (cmd + "\r\n").getBytes();
                    writeLastSshChannel(cmdBytes, 0, cmdBytes.length);
                }

                //sleep for a while and try to read some
                Thread.sleep(3000);

                {
                    byte[] tempBuff = new byte[2048];
                    InputStream input = _lastSsh.getInputStream();
                    while (input.available() > 0) {
                        int readLen = input.read(tempBuff);
                        if(readLen > 0) {
                            System.out.write(tempBuff, 0, readLen);
                        }
                    }
                }
            }

            _tcpServer.start();
        } catch (Throwable e) {
            logger.error("error in start()", e);
        }

        //wait for exit
        while (true) {
            String cmd = System.console().readLine( "$ ");
            if("exit,bye,byebye,quit".contains(cmd.toLowerCase())) {
                break;
            } else {
                System.out.println("please input 'exit' when you want to end this process");
            }
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
            for(InteractiveSsh ssh : _sshAgents) {
                ssh.disconnect();
            }
        } catch (Throwable e) {
            logger.error("error in ssh.disconnect()", e);
        }

        try {
            _stopFlg.set(true);
        } catch (Throwable e) {
            logger.error(null, e);
        }
    }
    private void initSsh(App.Args args) {
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

        /*
        if(listenPort > 0) {
            InteractiveSsh.SshSetting sshSetting = new InteractiveSsh.SshSetting();

            InteractiveSsh.SshAuth sshAuth = new InteractiveSsh.SshAuth();
            sshAuth.setHost("127.0.0.1");
            sshAuth.setPort(listenPort);
            sshAuth.setUser(args.s2user);
            sshAuth.setSshconfig("~/.ssh/config");

            sshSetting.setSshAuth(sshAuth);

            InteractiveSsh ssh = new InteractiveSsh(sshSetting);
            _sshAgents.add(ssh);
        }
        */

        _lastSsh = _sshAgents.get(_sshAgents.size() - 1);
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
                (byte[] receivedBuff, int offset, int len) -> {
                    onTcpRead(receivedBuff, offset, len);
                });
    }


    private void onTcpRead(byte[] receivedBuff, int offset, int len) {
        try {
            writeLastSshChannel(receivedBuff, offset, len);
        } catch (Throwable e) {
            logger.error("onTcpRead()", e);
        }
    }

    private void loopSshRead() {
        byte[] tempBuff = new byte[8192 * 2];
        final InteractiveSsh ssh = _lastSsh;

        try {
            while (!_stopFlg.get()) {
                if(Thread.interrupted()) {
                    logger.info("loopSshRead() break by interrupted");
                    break;
                }

                int readLen = ssh.getInputStream().read(tempBuff);
                if(readLen > 0) {
                    _tcpServer.replyClient(tempBuff, 0, readLen);
                    logger.debug("tcp reply to client(bytes):" + readLen);
                }
            }

            logger.info("loopSshRead() end");
        } catch (Throwable e) {
            logger.error("loopSshRead()", e);
        }
    }

    private void writeLastSshChannel(byte[] buff, int offset, int len) throws IOException {
        OutputStream output = _lastSsh.getOutputStream();
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
