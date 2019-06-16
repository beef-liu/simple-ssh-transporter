package com.badrabbitstudio.simplesshtransporter.util;

import com.alibaba.fastjson.JSON;
import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author XingGu_Liu on 2019-06-16.
 */
public class InteractiveSsh implements Closeable {
    private final static Log logger = LogFactory.getLog(InteractiveSsh.class);

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


    private final JSch _jsch = new JSch();
    private final List<Session> _sessions = new ArrayList<>();

    private final int _timeout = 60 * 1000;
    private final int _connTimeout = 5000;

    private final String _id;

    public InteractiveSsh() {
        _id = (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date());
    }

    @Override
    public void close() throws IOException {
        for(Session session : _sessions) {
            session.disconnect();
        }
    }

    public Session getLastSession() {
        return _sessions.get(_sessions.size() - 1);
    }

    public Session connect(SshAuth sshAuth) throws JSchException, IOException {
        logger.info(getLogPrefix() + "sshAuth:" + JSON.toJSONString(sshAuth));

        JSch ssh = _jsch;

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

        session.setTimeout(_timeout);
        session.connect(_connTimeout);
        logger.info(getLogPrefix() + "session connected");

        _sessions.add(session);

        return session;
    }

    public void portForwardL(Session session, PortForward portForward) throws JSchException {
        session.setPortForwardingL(portForward.getListenPort(), portForward.getToHost(), portForward.getToPort());
        logger.info(getLogPrefix() + "setPortForwardingL:" + JSON.toJSONString(portForward));
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
