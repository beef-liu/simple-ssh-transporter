package com.badrabbitstudio.simplesshtransporter.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author XingGu_Liu on 2019-06-15.
 */
public class TcpServerFor1Client {
    private final static Log logger = LogFactory.getLog(TcpServerFor1Client.class);

    public interface ITcpListenHandler {
        void onAccept();

        void onDisconnected();
    }

    public interface ITcpReadHandler {
        void onRead(byte[] receivedBuff, int offset, int len);
    }

    private final String _listenHost;
    private final int _listenPort;
    private final ITcpListenHandler _listenHandler;
    private final ITcpReadHandler _readHandler;

    private ServerSocket _serverSocket;
    private Socket _workSocket = null;

    private final Thread _readWorker;
    private final AtomicBoolean _stopFlg = new AtomicBoolean(false);

    public TcpServerFor1Client(
            String listenHost, int listenPort,
            ITcpListenHandler listenHandler,
            ITcpReadHandler readHandler
    ) {
        _listenHost = listenHost;
        _listenPort = listenPort;

        _listenHandler = listenHandler;
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

    public boolean isClientConnected() {
        return _workSocket != null && _workSocket.isConnected();
    }

    public void sendToClient(byte[] buff, int offset, int len) throws IOException {
        if(!isClientConnected()) {
            return;
        }

        _workSocket.getOutputStream().write(buff, offset, len);
    }

    private void doTcpListenAndRead() {
        try {
            byte[] tempBuff = new byte[8192 * 2];

            while (true) {
                if(Thread.interrupted()) {
                    logger.info("server accept() interrupted");
                    break;
                }

                logger.info("server listening ...");
                final Socket workSocket = _serverSocket.accept();
                try {
                    _workSocket = workSocket;
                    logger.info("client accepted");

                    InputStream socketInput = workSocket.getInputStream();
                    OutputStream socketOutput = workSocket.getOutputStream();

                    if(_listenHandler != null) {
                        _listenHandler.onAccept();
                    }

                    while (!_stopFlg.get()) {
                        if(Thread.interrupted()) {
                            logger.info("_workSocket read loop interrupted");
                            break;
                        }
                        if(!workSocket.isConnected()) {
                            logger.info("_workSocket disconnected");
                            break;
                        }

                        int readLen = socketInput.read(tempBuff);
                        if(readLen < 0) {
                            logger.debug("_workSocket read EOF");
                            break;
                        }
                        logger.debug("_workSocket readLen(bytes):" + readLen);
                        if(readLen > 0) {
                            if(_readHandler != null) {
                                _readHandler.onRead(tempBuff, 0, readLen);
                            }
                        }
                    }
                } catch (SocketException e){
                    logger.error("_workSocket", e);
                } finally {
                    try {
                        workSocket.close();
                    } catch (Throwable e2) {
                        logger.error("_workSocket.close()", e2);
                    }

                    _workSocket = null;
                }

                if(_listenHandler != null) {
                    _listenHandler.onDisconnected();
                }
            }
        } catch (IOException e) {
            logger.error("", e);
        } catch (Throwable e) {
            logger.error("", e);
        }
    }

}
