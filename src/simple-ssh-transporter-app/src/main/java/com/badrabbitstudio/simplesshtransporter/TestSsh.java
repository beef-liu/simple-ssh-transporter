package com.badrabbitstudio.simplesshtransporter;

import com.alibaba.fastjson.JSON;
import com.badrabbitstudio.simplesshtransporter.util.InteractiveSsh;
import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.SecureCacheResponse;

/**
 * @author XingGu_Liu on 2019-06-16.
 */
public class TestSsh {
    private final static Log logger = LogFactory.getLog(TestSsh.class);

    public static void main(String[] args) {
        (new TestSsh()).start();
    }

    public void start() {
        String host = "xxx"; // as a bastion
        String host2 = "xxx";   //the ssh target
        String sshConfig = "~/.ssh/config";
        try {
            final InteractiveSsh.SshAuth sshAuth = new InteractiveSsh.SshAuth();
            final InteractiveSsh.PortForward portForward = new InteractiveSsh.PortForward();

            sshAuth.setHost(host);
            sshAuth.setSshconfig(sshConfig);

            portForward.setListenPort(60001);
            portForward.setToHost(host2);
            portForward.setToPort(22);

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

            session.setUserInfo(new InteractiveSsh.MyUserInfo());
            //session.setUserInfo(new TestUserInfo());

            session.setTimeout(60 * 1000);
            session.connect(3000);
            //_sshSession = session;
            logger.info(getLogPrefix() + "session connected");


            //_workChannel = (ChannelShell) session.openChannel("shell");
            //_workChannel.connect(_connTimeout);

            //session.setDaemonThread(true);
            if(portForward != null) {
                //session.setPortForwardingL(2233, "10.161.150.35", 22);
                session.setPortForwardingL(portForward.getListenPort(), portForward.getToHost(), portForward.getToPort());
                logger.info(getLogPrefix() + "setPortForwardingL:" + JSON.toJSONString(portForward));
            }
            //session.openChannel("direct-tcpip");

            Session secondSession = ssh.getSession("admin0", "127.0.0.1", portForward.getListenPort());
            secondSession.setUserInfo(new InteractiveSsh.MyUserInfo());
            secondSession.setConfig("StrictHostKeyChecking", "no");

            secondSession.setTimeout(60 * 1000);
            secondSession.connect(5000); // now we're connected to the secondary system

            logger.info(getLogPrefix() + "ssh connected:" + session.isConnected());

            Channel channelShell = secondSession.openChannel("shell");
            InputStream channelInput = channelShell.getInputStream();
            OutputStream channelOutput = channelShell.getOutputStream();
            channelShell.connect(5000);

            //wait for exit
            byte[] tempBuff = new byte[8192 * 2];
            while (true) {
                String cmd = System.console().readLine( "$ ");
                if("exit,bye,byebye,quit".contains(cmd.toLowerCase())) {
                    break;
                } else {
                    //System.out.println("please input 'exit' when you want to end this process");
                    channelOutput.write((cmd+"\r\n").getBytes());
                    channelOutput.flush();

                    while (true) {
                        int readLen = channelInput.read(tempBuff);
                        if(readLen < 0) break;

                        if(readLen > 0) {
                            System.out.println(new String(tempBuff, 0, readLen));
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private String getLogPrefix() {
        return "testSsh -> ";
    }

    private static class TestUserInfo implements UserInfo {

        @Override
        public String getPassphrase() {
            return "lxg3382";
        }

        @Override
        public String getPassword() {
            return "";
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return true;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {

        }
    }
}
