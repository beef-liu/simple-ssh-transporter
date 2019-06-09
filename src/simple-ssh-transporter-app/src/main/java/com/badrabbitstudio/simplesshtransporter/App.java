package com.badrabbitstudio.simplesshtransporter;

import com.alibaba.fastjson.JSON;
import com.badrabbitstudio.simplesshtransporter.server.LocalServer;
import com.badrabbitstudio.simplesshtransporter.server.LocalServerArgs;
import com.badrabbitstudio.simplesshtransporter.util.AppBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;

/**
 * @author XingGu_Liu on 2019-06-09.
 */
public class App extends AppBase {
    private final static Log logger = LogFactory.getLog(App.class);

    @Override
    protected Class<?> argType() {
        return Args.class;
    }

    public static class Args {

        @ArgDesc(desc = "{local host}")
        public String lh;

        @ArgDesc(desc = "{local port}")
        public String lp;

        @ArgDesc(desc = "{ssh host}")
        public String sh;

        @ArgDesc(desc = "{ssh port}")
        public String sp;

        @ArgDesc(desc = "{ssh user}")
        public String su;

        @ArgDesc(desc = "{target host}")
        public String th;

        @ArgDesc(desc = "{target port}")
        public String tp;
    }

    public static void main(String[] args) {
        (new App()).runMain(args);
    }

    private LocalServer _localServer;

    @Override
    protected void process(Map<String, Object> argMap) {
        try {
            Args args = (new com.alibaba.fastjson.JSONObject(argMap)).toJavaObject(Args.class);

            LocalServerArgs serverArgs = new LocalServerArgs();
            serverArgs.setListenHost(args.lh);
            serverArgs.setListenPort(Integer.parseInt(args.lp));

            LocalServerArgs.TcpEndpoint targetPort = new LocalServerArgs.TcpEndpoint();
            targetPort.setHost(args.th);
            targetPort.setPort(Integer.parseInt(args.tp));
            serverArgs.setTargetPort(targetPort);

            LocalServerArgs.SshSetting sshSetting = new LocalServerArgs.SshSetting();
            sshSetting.setHost(args.sh);
            sshSetting.setPort(Integer.parseInt(args.sp));
            sshSetting.setUser(args.su);

            serverArgs.setSshSetting(sshSetting);

            _localServer = new LocalServer(serverArgs);
            _localServer.start();

            _localServer.awaitToShutdown();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void destroy() {
        _localServer.shutdown();
    }

}
