package com.badrabbitstudio.simplesshtransporter.server;

/**
 * @author XingGu_Liu on 2019-06-09.
 */
public class LocalServerArgs {

    private String listenHost;
    private int listenPort;

    private SshSetting sshSetting;

    private TcpEndpoint targetPort;

    public String getListenHost() {
        return listenHost;
    }

    public void setListenHost(String listenHost) {
        this.listenHost = listenHost;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public SshSetting getSshSetting() {
        return sshSetting;
    }

    public void setSshSetting(SshSetting sshSetting) {
        this.sshSetting = sshSetting;
    }

    public TcpEndpoint getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(TcpEndpoint targetPort) {
        this.targetPort = targetPort;
    }

    public static class TcpEndpoint {
        private String host;
        private int port;

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
    }

    public static class SshSetting {
        private String host;
        private int port;
        private String user;
        private String password;
        private String prikey;
        private String passphrase;

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

        public String getPassphrase() {
            return passphrase;
        }

        public void setPassphrase(String passphrase) {
            this.passphrase = passphrase;
        }
    }

}
