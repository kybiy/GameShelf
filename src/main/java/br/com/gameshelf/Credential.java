package br.com.gameshelf;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class Credential {
    static final String PREFIX="dpapi:v1:";
    static String protect(String plain)throws IOException{
        if(plain.isEmpty())return "";
        try{return PREFIX+Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(plain.getBytes(StandardCharsets.UTF_8)));}
        catch(RuntimeException | LinkageError e){throw new IOException("DPAPI unavailable");}
    }
    static String unlock(String sealed){
        try{return new String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(sealed.substring(PREFIX.length()))),StandardCharsets.UTF_8);}
        catch(RuntimeException | LinkageError e){return "";}
    }
}
