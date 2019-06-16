package com.badrabbitstudio.simplesshtransporter.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author XingGu_Liu on 2019-06-15.
 */
public class TcpServerFor1Client {
    private final static Log logger = LogFactory.getLog(TcpServerFor1Client.class);

    public interface ITcpReadHandler {
        void onRead(byte[] receivedBuff, int offset, int len);
    }

    private final String _listenHost;
    private final int _listenPort;
    private final ITcpReadHandler _readHandler;

    private ServerSocket _serverSocket;
    private Socket _workSocket;

    private final Thread _readWorker;
    private final AtomicBoolean _stopFlg = new AtomicBoolean(false);

    public TcpServerFor1Client(String listenHost, int listenPort, ITcpReadHandler readHandler) {
        _listenHost = listenHost;
        _listenPort = listenPort;
        _readHandler = readHandler;

        _readWorker = new Thread(() -> {
            doTcpListenAndRead();
        }, "TcpServerForSingleClient_readWorker"
        );
    }

    public void start() {
        try {
            final int backlog = 1;

            _serverSocket = new ServerSocket(
                    _listenPort,
                    backlog,
                    InetAddress.getByName(_listenHost)
            );
            _readWorker.start();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        try {
            _stopFlg.set(true);
            _readWorker.interrupt();
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }

        try {
            if(_serverSocket != null) {
                _serverSocket.close();
            }
        } catch (Throwable e) {
            logger.error("error in shutdown", e);
        }
    }

    public void replyClient(byte[] buff, int offset, int len) throws IOException {
        if(_workSocket == null || !_workSocket.isConnected()) {
            return;
        }

        _workSocket.getOutputStream().write(buff, offset, len);
    }

    private void doTcpListenAndRead() {
        try {
            byte[] tempBuff = new byte[8192 * 2];

            final Socket workSocket = _serverSocket.accept();
            try {
                _workSocket = workSocket;
                logger.info("client accepted");

                InputStream socketInput = workSocket.getInputStream();
                OutputStream socketOutput = workSocket.getOutputStream();

                while (!_stopFlg.get()) {
                    if(Thread.interrupted()) {
                        logger.info("read loop interrupted");
                        break;
                    }

                    int readLen = socketInput.read(tempBuff);
                    if(readLen > 0) {
                        _readHandler.onRead(tempBuff, 0, readLen);
                    }
                }
            } finally {
                workSocket.close();
            }
        } catch (IOException e) {
            logger.error("", e);
        } catch (Throwable e) {
            logger.error("", e);
        }
    }

}
