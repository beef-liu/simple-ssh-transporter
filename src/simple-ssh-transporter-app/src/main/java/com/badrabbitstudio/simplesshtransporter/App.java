package com.badrabbitstudio.simplesshtransporter;

import com.badrabbitstudio.simplesshtransporter.server.LocalServer;
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
        public String lhost;

        @ArgDesc(desc = "{local port}")
        public String lport;


        @ArgDesc(desc = "{target host}")
        public String thost;

        @ArgDesc(desc = "{target port}")
        public String tport;


        @ArgDesc(desc = "{ssh host}")
        public String shost;

        @ArgDesc(desc = "{ssh port}")
        public String sport;

        @ArgDesc(desc = "{ssh user}")
        public String suser;

        @ArgDesc(desc = "{ssh privatekey. e.g. ~/.ssh/id_rsa}")
        public String sprik;

        @ArgDesc(desc = "{ssh config. e.g. ~/.ssh/config}")
        public String sconf;


        @ArgDesc(desc = "{second ssh host}")
        public String s2host;

        @ArgDesc(desc = "{second ssh port}")
        public String s2port;

        @ArgDesc(desc = "{second ssh user}")
        public String s2user;

        @ArgDesc(desc = "{tunnel command. The stdio will be used as a tunnel. default: telnet $h $p}")
        public String tcmd = "nc $h $p";
    }

    public static void main(String[] args) {
        (new App()).runMain(args);
    }

    private LocalServer _localServer;

    @Override
    protected void process(Map<String, Object> argMap) {
        try {
            Args args = (new com.alibaba.fastjson.JSONObject(argMap)).toJavaObject(Args.class);

            _localServer = new LocalServer(args);
            _localServer.start();

            //(new TestSsh()).start();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void destroy() {
        _localServer.shutdown();
    }

}
