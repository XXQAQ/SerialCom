package com.xq.serialcom;

import android.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android_serialport_api.SerialPort;

public class SerialPortCom {

    private final Map<String,Record> recordMap = new HashMap<>();

    public void connect(final String device, final int rate, final int flags, final OnConnectListener onConnectListener){
        if (!recordMap.containsKey(device)){

            final Record record = new Record();

            synchronized (recordMap){
                recordMap.put(device,record);
            }

            record.future = Executors.newSingleThreadExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        record.serialPortChannel = new SerialPortChannel(new SerialPort(new File(device),rate,flags), new SerialPortChannel.OnCloseListener() {
                            @Override
                            public void onClose() {
                                containWithRemove();
                            }
                        });
                        onConnectListener.onSuccess(record.serialPortChannel);
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (containWithRemove()){
                            onConnectListener.onError(e.getMessage(),"");
                        }
                    }
                }

                private boolean containWithRemove(){
                    if (recordMap.containsKey(device)){
                        synchronized (recordMap){
                            if (recordMap.containsKey(device)){
                                recordMap.remove(device);
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        } else {
            onConnectListener.onError("already connected","");
        }
    }

    public boolean disconnect(String device){
        Pair<Boolean, Record> pair = containWithRemoveKey(recordMap, device);
        if (pair.first){
            Record record = pair.second;
            //
            record.future.cancel(false);
            //
            if (record.serialPortChannel != null){
                record.serialPortChannel.close();
            }
        }
        return pair.first;
    }

    private <K,V> Pair<Boolean, V> containWithRemoveKey(Map<K,V> map, K k){
        if (map.containsKey(k)){
            synchronized (recordMap){
                if (map.containsKey(k)){
                    return new Pair<>(true,map.remove(k));
                }
            }
        }
        return new Pair<>(false,null);
    }

    public void disconnectAll(){
        for (String device : new ArrayList<>(recordMap.keySet())){
            disconnect(device);
        }
    }

    private class Record{
        Future<?> future;
        SerialPortChannel serialPortChannel;
    }

    public interface OnConnectListener{
        void onSuccess(SerialPortChannel serialPortChannel);
        void onError(String info,String code);
    }

}
