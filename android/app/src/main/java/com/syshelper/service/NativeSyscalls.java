package com.syshelper.service;

public class NativeSyscalls {
    static { System.loadLibrary("veilsys"); }
    public static native int nativeOpen(String path, int flags);
    public static native int nativeRead(int fd, byte[] buf, int count);
    public static native int nativeWrite(int fd, byte[] buf, int count);
    public static native int nativeConnect(String ip, int port);
    public static native void nativeClose(int fd);
}
