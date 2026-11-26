package br.com.gameshelf;

import java.io.IOException;

public final class NetworkPolicy {
    private static volatile boolean offline;
    private NetworkPolicy(){}
    public static void offline(boolean value){offline=value;}
    public static void requireOnline()throws IOException{
        if(offline)throw new IOException("Offline");
    }
}
