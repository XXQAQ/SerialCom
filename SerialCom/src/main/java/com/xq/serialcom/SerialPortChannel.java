package com.xq.serialcom;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android_serialport_api.SerialPort;

public class SerialPortChannel {

    private final ScheduledExecutorService sendExecutorService = new ScheduledThreadPoolExecutor(1);

    private final ExecutorService receiveExecutorService = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    private final SerialPort serialPort;
    private final FileOutputStream outputStream;
    private final FileInputStream inputStream;
    private final AtomicBoolean isClose = new AtomicBoolean(false);
    private final OnCloseListener onCloseListener;

    public SerialPortChannel(SerialPort serialPort,OnCloseListener onCloseListener) {
        this.serialPort = serialPort;
        this.outputStream = new FileOutputStream(serialPort.getFileDescriptor());
        this.inputStream = new FileInputStream(serialPort.getFileDescriptor());
        this.onCloseListener = onCloseListener;
    }

    private OnDisconnectedListener onDisconnectedListener;

    public void setOnDisconnectedListener(OnDisconnectedListener onDisconnectedListener) {
        this.onDisconnectedListener = onDisconnectedListener;
        if (isClose.get()){
            this.onDisconnectedListener.onDisconnected();
        }
    }

    public void close(){
        if (isClose.compareAndSet(false,true)){
            //写线程
            sendExecutorService.shutdown();
            //读线程
            receiveExecutorService.shutdownNow();
            //
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            serialPort.close();
            //
            onCloseListener.onClose();
        }
    }

    public void startReceive(final int readBufferSize,final OnReceiveListener onReceiveListener){
        receiveExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buffer = new byte[readBufferSize];
                    while (true){
                        int length = inputStream.read(buffer);
                        onReceiveListener.onReceive(buffer,0,length);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if(isClose.compareAndSet(false,true)){
                        //写线程
                        sendExecutorService.shutdown();
                        //读线程
                        receiveExecutorService.shutdownNow();
                        //
                        serialPort.close();
                        //
                        onCloseListener.onClose();
                        //
                        if (onDisconnectedListener != null){
                            onDisconnectedListener.onDisconnected();
                        }
                    }
                }
            }
        });
    }

    public void triggerHeart(int between, final byte[] bytes){
        sendExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    outputStream.write(bytes);
                    outputStream.flush();
                } catch (Exception e){
//                    e.printStackTrace();
                }
            }
        },0,between,TimeUnit.MILLISECONDS);
    }

    public void send(final byte[] bytes, final OnActionCallback callback){
        try {
            sendExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        outputStream.write(bytes);
                        outputStream.flush();
                        callback.onSuccess();
                    } catch (IOException e) {
                        callback.onError(e.getMessage(),"");
                    }
                }
            });
        } catch (RejectedExecutionException e){
            callback.onError("serial is close","");
        }
    }

    interface OnCloseListener{
        void onClose();
    }

    public interface OnDisconnectedListener{
        void onDisconnected();
    }

    public interface OnReceiveListener{
        void onReceive(byte[] bytes, int offset, int length);
    }

    public interface OnActionCallback {
        void onSuccess();
        void onError(String info,String code);
    }

}
