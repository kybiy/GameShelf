package br.com.gameshelf;

import java.util.*;

/** Leitores pequenos de JSON e KeyValues de texto, sem dependências externas. */
final class ManifestParser {
    private final String s; private int p;
    private ManifestParser(String s) { this.s = s.startsWith("\uFEFF") ? s.substring(1) : s; }
    static Map<String,Object> json(String s) { return map(new ManifestParser(s).jsonValue()); }
    static Map<String,Object> vdf(String s) { return new ManifestParser(s).vdfObject(false); }
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object v) { return v instanceof Map<?,?> ? (Map<String,Object>)v : Map.of(); }
    static String str(Map<String,Object> m,String k) { Object v=m.get(k); return v instanceof String ? (String)v : ""; }
    private void ws() { while(p<s.length() && Character.isWhitespace(s.charAt(p))) p++; }
    private boolean eat(char c) { ws(); if(p<s.length() && s.charAt(p)==c){p++;return true;}return false; }
    private void need(char c) { if(!eat(c)) throw new IllegalArgumentException("Esperado: "+c+" em "+p); }
    private String quoted(boolean json) {
        ws(); need('"'); StringBuilder b=new StringBuilder();
        while(p<s.length()) { char c=s.charAt(p++); if(c=='"') return b.toString();
            if(c=='\\' && p<s.length()) { char e=s.charAt(p++);
                if(!json) { if(e=='\\'||e=='"') b.append(e); else b.append('\\').append(e); }
                else switch(e) { case 'n'->b.append('\n'); case 'r'->b.append('\r'); case 't'->b.append('\t'); case 'b'->b.append('\b'); case 'f'->b.append('\f'); case 'u'->{if(p+4>s.length())throw new IllegalArgumentException("Unicode incompleto"); b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;} case '"','\\','/'->b.append(e); default->throw new IllegalArgumentException("Escape JSON inválido"); }
            } else b.append(c);
        } throw new IllegalArgumentException("String incompleta");
    }
    private Object jsonValue() { ws(); if(p>=s.length())throw new IllegalArgumentException("JSON incompleto"); char c=s.charAt(p);
        if(c=='"')return quoted(true);
        if(eat('{')) { Map<String,Object> m=new LinkedHashMap<>(); if(eat('}'))return m; do {String k=quoted(true);need(':');m.put(k,jsonValue());}while(eat(','));need('}');return m; }
        if(eat('[')) { List<Object> a=new ArrayList<>();if(eat(']'))return a;do{a.add(jsonValue());}while(eat(','));need(']');return a; }
        int start=p;while(p<s.length() && ",]} \r\n\t".indexOf(s.charAt(p))<0)p++;if(start==p)throw new IllegalArgumentException("JSON inválido");String t=s.substring(start,p);return switch(t){case "true"->true;case "false"->false;case "null"->null;default->Double.parseDouble(t);};
    }
    private void vws(){while(true){ws();if(s.startsWith("//",p)){while(p<s.length()&&s.charAt(p)!='\n')p++;}else return;}}
    private String token(){vws();if(p>=s.length())throw new IllegalArgumentException("VDF incompleto");if(s.charAt(p)=='"')return quoted(false);int a=p;while(p<s.length()&&!Character.isWhitespace(s.charAt(p))&&"{}".indexOf(s.charAt(p))<0)p++;if(a==p)throw new IllegalArgumentException("VDF inválido");return s.substring(a,p);}
    private Map<String,Object> vdfObject(boolean nested){Map<String,Object> m=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);while(true){vws();if(p>=s.length()){if(nested)throw new IllegalArgumentException("VDF incompleto");return m;}if(nested&&eat('}'))return m;String k=token();vws();m.put(k,eat('{')?vdfObject(true):token());}}
}
