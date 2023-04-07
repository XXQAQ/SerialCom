package com.xq.serialcom;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SerialPortChannel {

    private final FileDescriptor fileDescriptor;
    private final FileOutputStream outputStream;

    public SerialPortChannel(FileDescriptor fileDescriptor) {
        this.fileDescriptor = fileDescriptor;
        this.outputStream = new FileOutputStream(fileDescriptor);
    }

    void close(){
        //写
        sendExecutorService.shutdown();
        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //读
        stopReceive();
    }

    private final ExecutorService receiveExecutorService = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    private final AtomicReference<ReceiveRecord> receiveRecordReference = new AtomicReference<>();

    public void startReceive(final int readBufferSize,final OnReceiveListener onReceiveListener){
        final FileInputStream inputStream = new FileInputStream(fileDescriptor);
        synchronized (receiveRecordReference){
            receiveRecordReference.set(new ReceiveRecord(receiveExecutorService.submit(new Runnable() {
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
                    }
                }
            }),inputStream));
        }
    }

    public void stopReceive(){
        if (receiveRecordReference.get() != null){
            synchronized (receiveRecordReference){
                if (receiveRecordReference.get() != null){
                    ReceiveRecord receiveRecord = receiveRecordReference.getAndSet(null);
                    receiveRecord.future.cancel(false);
                    try {
                        receiveRecord.inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void send(byte[] bytes){
        send(bytes,0,bytes.length);
    }

    private final ExecutorService sendExecutorService = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
    public void send(final byte[] bytes, final int offset, final int length){
        sendExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    outputStream.write(bytes,offset,length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private class ReceiveRecord{
        Future<?> future;
        FileInputStream inputStream;
        public ReceiveRecord(Future<?> future, FileInputStream inputStream) {
            this.future = future;
            this.inputStream = inputStream;
        }
    }

    public interface OnReceiveListener{
        void onReceive(byte[] bytes, int offset, int length);
    }
}
