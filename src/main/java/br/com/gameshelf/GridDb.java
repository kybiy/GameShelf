package br.com.gameshelf;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Optional image catalog. A search result always needs an explicit user selection. */
final class GridDb {
    record Match(long id,String name,int year) {
        public String toString(){return name+(year==0?"":" ("+year+")")+"  ·  #"+id;}
    }
    record Art(String url,int width,int height) {
        public String toString(){return width+" × "+height;}
    }
    private final String key;
    GridDb(String key){this.key=key;}
    private List<Map<String,Object>> get(String path)throws IOException {
        NetworkPolicy.requireOnline();
        HttpURLConnection request=(HttpURLConnection)URI.create("https://www.steamgriddb.com/api/v2/"+path).toURL().openConnection();
        request.setInstanceFollowRedirects(false);request.setConnectTimeout(4500);request.setReadTimeout(6000);
        request.setRequestProperty("Authorization","Bearer "+key);request.setRequestProperty("Accept","application/json");
        try{
            int status=request.getResponseCode();
            if(status==401||status==403)throw new IOException("Confira a chave do SteamGridDB em Configurações.");
            if(status!=200)throw new IOException("SteamGridDB indisponível (HTTP "+status+").");
            try(InputStream in=request.getInputStream()){
                int max=1024*1024;byte[] bytes=in.readNBytes(max+1);
                if(bytes.length>max)throw new IOException("Resposta muito grande do catálogo.");
                var result=ManifestParser.json(new String(bytes,StandardCharsets.UTF_8));
                if(!(result.get("data") instanceof List<?> rows))throw new IOException("Resposta inesperada do catálogo.");
                return rows.stream().map(ManifestParser::map).toList();
            }
        }finally{request.disconnect();}
    }
    List<Match> search(String title)throws IOException{
        List<Match> matches=new ArrayList<>();
        for(var row:get("search/autocomplete/"+URLEncoder.encode(title,StandardCharsets.UTF_8).replace("+","%20"))){
            if(!(row.get("id") instanceof Number id))continue;
            int year=row.get("release_date") instanceof Number t?Instant.ofEpochSecond(t.longValue()).atZone(ZoneOffset.UTC).getYear():0;
            matches.add(new Match(id.longValue(),ManifestParser.str(row,"name"),year));
        }
        return matches;
    }
    List<Art> images(long id,boolean cover)throws IOException{
        String path=(cover?"grids":"heroes")+"/game/"+id+"?types=static&nsfw=false&epilepsy=false"+(cover?"&dimensions=600x900":"");
        List<Art> images=new ArrayList<>();
        for(var row:get(path)){
            String url=ManifestParser.str(row,"url");
            if(url.startsWith("https://cdn2.steamgriddb.com/")&&row.get("width") instanceof Number w&&row.get("height") instanceof Number h)
                images.add(new Art(url,w.intValue(),h.intValue()));
            if(images.size()==12)break;
        }
        return images;
    }
}
