package com.badrabbitstudio.simplesshtransporter.util;

import com.alibaba.fastjson.JSON;
import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author XingGu_Liu on 2019-06-10.
 */
/*
public class InteractiveSshOld implements Closeable {
    private static final Log logger = LogFactory.getLog(InteractiveSshOld.class);

    private final static AtomicInteger SshSeq = new AtomicInteger(0);

    public static class SshSetting {
        private SshAuth sshAuth = null;
        private PortForward portForward = null;

        public SshAuth getSshAuth() {
            return sshAuth;
        }

        public void setSshAuth(SshAuth sshAuth) {
            this.sshAuth = sshAuth;
        }

        public PortForward getPortForward() {
            return portForward;
        }

        public void setPortForward(PortForward portForward) {
            this.portForward = portForward;
        }
    }

    public static class PortForward {
        private int listenPort;
        private String toHost;
        private int toPort;


        public int getListenPort() {
            return listenPort;
        }

        public void setListenPort(int listenPort) {
            this.listenPort = listenPort;
        }

        public String getToHost() {
            return toHost;
        }

        public void setToHost(String toHost) {
            this.toHost = toHost;
        }

        public int getToPort() {
            return toPort;
        }

        public void setToPort(int toPort) {
            this.toPort = toPort;
        }
    }

    public static class SshAuth {
        private String host;
        private int port;
        private String user;
        private String password;
        private String prikey;
        private String sshconfig;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPrikey() {
            return prikey;
        }

        public void setPrikey(String prikey) {
            this.prikey = prikey;
        }


        public String getSshconfig() {
            return sshconfig;
        }

        public void setSshconfig(String sshconfig) {
            this.sshconfig = sshconfig;
        }
    }

    private final int _id;
    private final SshSetting _sshSetting;
    private final int _timeout = 60 * 1000;
    private final int _connTimeout = 5000;

    private Session _sshSession;
    private Channel _workChannel;

    public InteractiveSshOld(SshSetting sshSetting) {
        _id = SshSeq.incrementAndGet();
        _sshSetting = sshSetting;
    }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    public boolean isConnected() {
        return _workChannel != null && _workChannel.isConnected()
                && _sshSession != null && _sshSession.isConnected()
                ;
    }

    public OutputStream getOutputStream() throws IOException {
        return _workChannel.getOutputStream();
    }

    public InputStream getInputStream() throws IOException {
        return _workChannel.getInputStream();
    }

    public void disconnect() {
        try {
            if(_workChannel != null) {
                _workChannel.disconnect();
            }
        } catch (Throwable e) {
            logger.error(getLogPrefix(), e);
        }
        _workChannel = null;

        try {
            if(_sshSession != null) {
                _sshSession.disconnect();
            }
        } catch (Throwable e) {
            logger.error(getLogPrefix(), e);
        }
        _sshSession = null;
    }

    public void connect() {
        try {
            final SshAuth sshAuth = _sshSetting.getSshAuth();
            final PortForward portForward = _sshSetting.getPortForward();

            logger.info(getLogPrefix() + "setting:" + JSON.toJSONString(_sshSetting));

            JSch ssh = new JSch();
            //ssh.setKnownHosts();

            if(sshAuth.getSshconfig() != null && !sshAuth.getSshconfig().isEmpty()) {
                ssh.setConfigRepository(OpenSSHConfig.parseFile(sshAuth.getSshconfig()));
            }

            final Session session;
            if(sshAuth.getUser() == null || sshAuth.getUser().isEmpty()) {
                session = ssh.getSession(sshAuth.getHost());
            } else {
                if(sshAuth.getPort() <= 0) {
                    session = ssh.getSession(sshAuth.getUser(), sshAuth.getHost());
                } else {
                    session = ssh.getSession(sshAuth.getUser(), sshAuth.getHost(), sshAuth.getPort());
                }
            }

            if(sshAuth.getPrikey() != null && !sshAuth.getPrikey().isEmpty()) {
                //ssh.addIdentity(_sshAuth.getPrikey(), _sshAuth.getPassphrase());
                ssh.addIdentity(sshAuth.getPrikey());
                logger.info(getLogPrefix() + "set identity with prikey:" + sshAuth.getSshconfig());
            } else if(sshAuth.getPassword() != null && !sshAuth.getPassword().isEmpty()) {
                session.setPassword(sshAuth.getPassword());
                logger.info(getLogPrefix() + "set identity with password.");
            } else {
                logger.info(getLogPrefix() + "did not set identity.");
            }

            session.setConfig("StrictHostKeyChecking", "no");
            logger.info(getLogPrefix() + "set StrictHostKeyChecking: no");

            session.setUserInfo(new MyUserInfo());

            session.connect(_connTimeout);

            if(portForward != null) {
                //session.setPortForwardingL(2233, "10.161.150.35", 22);
                int assignedPort = session.setPortForwardingL(portForward.listenPort, portForward.toHost, portForward.toPort);
                logger.info(getLogPrefix() + "setPortForwardingL"
                        + " assignedPort:" + assignedPort
                        + " setting:" + JSON.toJSONString(portForward)
                );
            }

            _sshSession = session;
            logger.info(getLogPrefix() + "session connected");


            //_workChannel = (ChannelShell) session.openChannel("shell");
            //_workChannel.connect(_connTimeout);
            logger.info(getLogPrefix() + "shellChannel connected");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private String getLogPrefix() {
        return "ssh[" + _id + "] -> ";
    }

    public static class MyUserInfo implements UserInfo {
        private String _passphrase;
        private String _password;

        private String scanInput(String prompt) {
            return scanInput(prompt, false);
        }

        private String scanInput(String prompt, boolean isPassword) {
            if(isPassword) {
                return new String(System.console().readPassword(prompt + ": "));
            } else {
                return System.console().readLine(prompt + ": ");
            }
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
            _password = scanInput(s, true);
            return !_password.isEmpty();
        }

        @Override
        public boolean promptPassphrase(String s) {
            _passphrase = scanInput(s, true);
            return !_passphrase.isEmpty();
        }

        @Override
        public boolean promptYesNo(String s) {
            String yesNo = scanInput(s, true);
            return yesNo.equalsIgnoreCase("yes");
        }

        @Override
        public void showMessage(String s) {
            System.out.println(s);
        }
    }
}
*
