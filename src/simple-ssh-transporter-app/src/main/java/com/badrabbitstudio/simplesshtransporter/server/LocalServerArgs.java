package com.badrabbitstudio.simplesshtransporter.server;

import com.badrabbitstudio.simplesshtransporter.util.InteractiveSsh;

/**
 * @author XingGu_Liu on 2019-06-09.
 */
public class LocalServerArgs {

    private String listenHost;
    private int listenPort;

    private InteractiveSsh.SshAuth sshAuth;

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

    public TcpEndpoint getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(TcpEndpoint targetPort) {
        this.targetPort = targetPort;
    }

    public InteractiveSsh.SshAuth getSshAuth() {
        return sshAuth;
    }

    public void setSshAuth(InteractiveSsh.SshAuth sshAuth) {
        this.sshAuth = sshAuth;
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

}
