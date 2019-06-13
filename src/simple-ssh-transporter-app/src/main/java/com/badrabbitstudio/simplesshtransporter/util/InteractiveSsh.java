package com.badrabbitstudio.simplesshtransporter.util;

import com.badrabbitstudio.simplesshtransporter.server.LocalServerArgs;
import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author XingGu_Liu on 2019-06-10.
 */
public class InteractiveSsh implements Closeable {
    private static final Log logger = LogFactory.getLog(InteractiveSsh.class);

    public static class SshAuth {
        private String host;
        private int port;
        private String user;
        private String password;
        private String prikey;
        private String sshconfig;
//        private String passphrase;

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

    private final SshAuth _sshAuth;
    private final int _connTimeout = 5000;

    private Session _sshSession;
    private ChannelShell _workChannel;

    public InteractiveSsh(SshAuth sshAuth) {
        _sshAuth = sshAuth;
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
            logger.error("", e);
        }
        _workChannel = null;

        try {
            if(_sshSession != null) {
                _sshSession.disconnect();
            }
        } catch (Throwable e) {
            logger.error("", e);
        }
        _sshSession = null;
    }

    public void connect() {
        try {
            JSch ssh = new JSch();
            //ssh.setKnownHosts();
            if(_sshAuth.getSshconfig() != null && !_sshAuth.getSshconfig().isEmpty()) {
                ssh.setConfigRepository(OpenSSHConfig.parseFile(_sshAuth.getSshconfig()));
                logger.info("ssh config:" + _sshAuth.getSshconfig());
            }

            final Session session;
            logger.info("ssh user:" + _sshAuth.getUser());
            logger.info("ssh host:" + _sshAuth.getHost());
            logger.info("ssh port:" + _sshAuth.getPort());
            if(_sshAuth.getUser() == null || _sshAuth.getUser().isEmpty()) {
                session = ssh.getSession(_sshAuth.getHost());
            } else {
                if(_sshAuth.getPort() <= 0) {
                    session = ssh.getSession(_sshAuth.getUser(), _sshAuth.getHost());
                } else {
                    session = ssh.getSession(_sshAuth.getUser(), _sshAuth.getHost(), _sshAuth.getPort());
                }
            }

            if(_sshAuth.getPrikey() != null && !_sshAuth.getPrikey().isEmpty()) {
                //ssh.addIdentity(_sshAuth.getPrikey(), _sshAuth.getPassphrase());
                ssh.addIdentity(_sshAuth.getPrikey());
                logger.info("ssh prikey:" + _sshAuth.getSshconfig());
            } else if(_sshAuth.getPassword() != null && !_sshAuth.getPassword().isEmpty()) {
                session.setPassword(_sshAuth.getPassword());
                logger.info("ssh auth with password.");
            }

            session.setConfig("StrictHostKeyChecking", "no");
            session.setUserInfo(new UserInfo() {
                private String _passphrase;
                private String _password;
                private String _yesno;

                private String scanInput(String prompt) {
                    return scanInput(prompt, false);
                }

                private String scanInput(String prompt, boolean isPassword) {
                /*
                System.out.print(prompt + ": ");
                final Scanner scanner = new Scanner(System.in);
                String inputLine = scanner.nextLine();
                scanner.close();
                return inputLine;
                */

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
                    return _password.isEmpty();
                }

                @Override
                public boolean promptPassphrase(String s) {
                    _passphrase = scanInput(s, true);
                    return _passphrase.isEmpty();
                }

                @Override
                public boolean promptYesNo(String s) {
                    return true;
                }

                @Override
                public void showMessage(String s) {
                    System.out.println(s);
                }
            });

            session.connect(_connTimeout);
            _sshSession = session;
            logger.info("ssh session connected");


            _workChannel = (ChannelShell) session.openChannel("shell");
            _workChannel.connect(_connTimeout);

            logger.info("ssh channel connected");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
