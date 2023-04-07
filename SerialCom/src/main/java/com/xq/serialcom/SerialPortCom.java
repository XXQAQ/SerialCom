package com.xq.serialcom;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import android_serialport_api.SerialPort;

public class SerialPortCom {

    private final Map<String,ConnectRecord> serialPortMap = new HashMap<>();

    public void connect(final String device, int rate, int flags, OnConnectListener onConnectListener){
        if (!serialPortMap.containsKey(device)){
            try {
                SerialPort serialPort = new SerialPort(new File(device),rate,flags);
                SerialPortChannel serialPortChannel = new SerialPortChannel(serialPort.getFileDescriptor());
                synchronized (serialPortMap){
                    serialPortMap.put(device,new ConnectRecord(serialPort, serialPortChannel));
                }
                onConnectListener.onSuccess(serialPortChannel);
            } catch (Exception e) {
                e.printStackTrace();
                onConnectListener.onError("","");
            }
        } else {
            onConnectListener.onError("allreday","");
        }
    }

    public void disconnect(String device){
        if (serialPortMap.containsKey(device)){
            synchronized (serialPortMap){
                if (serialPortMap.containsKey(device)){
                    ConnectRecord connectRecord = serialPortMap.remove(device);
                    connectRecord.serialPortChannel.close();
                    connectRecord.serialPort.close();
                }
            }
        }
    }

    public boolean isConnected(String device){
        return serialPortMap.containsKey(device);
    }

    public String[] getAllConnected(){
        return serialPortMap.keySet().toArray(new String[0]);
    }

    private class ConnectRecord{
        SerialPort serialPort;
        SerialPortChannel serialPortChannel;
        public ConnectRecord(SerialPort serialPort, SerialPortChannel serialPortChannel) {
            this.serialPort = serialPort;
            this.serialPortChannel = serialPortChannel;
        }
    }

    public interface OnConnectListener{
        void onSuccess(SerialPortChannel serialPortChannel);
        void onError(String info,String code);
    }

}
