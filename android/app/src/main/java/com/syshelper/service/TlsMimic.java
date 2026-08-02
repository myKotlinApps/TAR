package com.syshelper.service;

public class TlsMimic {
    static { System.loadLibrary("veiltls"); }
    public static native String sendRequest(String host, int port, String path, String payload);
}
