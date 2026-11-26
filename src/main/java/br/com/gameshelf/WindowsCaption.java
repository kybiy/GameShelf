package br.com.gameshelf;

import com.sun.jna.*;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

final class WindowsCaption {
    public interface Dwm extends StdCallLibrary {
        int DwmSetWindowAttribute(HWND window,int attribute,IntByReference value,int bytes);
    }
    private static void set(Dwm dwm,HWND window,int attr,int value){
        dwm.DwmSetWindowAttribute(window,attr,new IntByReference(value),4);
    }
    static void apply(String title,boolean light,String accent){
        if(!Platform.isWindows())return;
        try{
            User32 user=User32.INSTANCE;
            user.EnumWindows((window,arg)->{
                var pid=new IntByReference();user.GetWindowThreadProcessId(window,pid);
                if(pid.getValue()!=ProcessHandle.current().pid())return true;
                char[] text=new char[256];user.GetWindowText(window,text,text.length);
                if(!title.equals(Native.toString(text)))return true;
                Dwm dwm=Native.load("dwmapi",Dwm.class);
                set(dwm,window,20,light?0:1);
                set(dwm,window,35,light?0xf8f3f2:0x221916);
                set(dwm,window,36,light?0x302620:0xf7f1f0);
                set(dwm,window,34,Integer.reverseBytes(Integer.parseInt(accent.substring(1),16))>>>8);
                return false;
            },null);
        }catch(LinkageError | RuntimeException unavailable){/* Keep system decoration on older Windows. */}
    }
}
