package br.com.gameshelf.ui;

import br.com.gameshelf.Game;
import javafx.application.Platform;
import javafx.scene.image.Image;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** All file access, decoding and network work runs outside the application thread. */
public final class Artwork implements AutoCloseable {
    public enum Kind { COVER, BANNER }
    private final Path cache;
    private final ExecutorService pool=Executors.newFixedThreadPool(3,Thread.ofPlatform().daemon().name("gameshelf-art-",0).factory());
    private final Map<String,CompletableFuture<Image>> pending=new HashMap<>();
    private final Map<String,Instant> misses=new HashMap<>();
    private final LinkedHashMap<String,Image> images=new LinkedHashMap<>(32,.75f,true);
    private long decodedBytes;
    private boolean background;
    public void background(boolean hidden){
        background=hidden;
        if(hidden){images.clear();decodedBytes=0;}
    }
    private static long bytes(Image image){return (long)image.getWidth()*(long)image.getHeight()*4;}
    private volatile int limitMb=256;
    public Artwork(Path data) { cache=data.resolve("cache"); }
    public void load(Game g,Kind kind,String local,Consumer<Image> show) {
        String key=g.id()+"/"+kind+"/"+local;
        Image hit=images.get(key);
        if(hit!=null){show.accept(hit);return;}
        if(misses.getOrDefault(key,Instant.EPOCH).isAfter(Instant.now()))return;
        CompletableFuture<Image> task=pending.get(key);
        if(task==null) {
            task=CompletableFuture.supplyAsync(()->read(g,kind,local),pool);
            pending.put(key,task);
            task.whenComplete((image,error)->Platform.runLater(()->{
                pending.remove(key);
                if(image==null) misses.put(key,Instant.now().plusSeconds(300));
                else if(!background){
                    Image previous=images.put(key,image);
                    decodedBytes+=bytes(image)-(previous==null?0:bytes(previous));
                    while(decodedBytes>16L*1024*1024)decodedBytes-=bytes(images.pollFirstEntry().getValue());
                }
            }));
        }
        task.thenAccept(image->{if(image!=null)Platform.runLater(()->show.accept(image));});
    }
    private Image read(Game g,Kind kind,String local) {
        try {
            boolean catalog=local.startsWith("https://cdn2.steamgriddb.com/");
            if(!local.isBlank()&&!catalog) try(InputStream in=Files.newInputStream(Path.of(local))){return decode(in.readNBytes(12*1024*1024+1),kind);}
            if(!catalog&&!g.id().matches("steam:[0-9]+"))return null;
            String asset=kind==Kind.COVER?"library_600x900.jpg":"library_hero.jpg";
            String filename=catalog?UUID.nameUUIDFromBytes(local.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString():g.id().substring(6)+"-"+kind;
            Path file=cache.resolve(filename+".jpg");
            if(Files.isRegularFile(file))try(InputStream in=Files.newInputStream(file)){
                Image image=decode(in.readNBytes(12*1024*1024+1),kind);
                if(image!=null)return image;
            }
            String url=catalog?local:"https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/"+g.id().substring(6)+"/"+asset;
            br.com.gameshelf.NetworkPolicy.requireOnline();
            URLConnection connection=URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3500);connection.setReadTimeout(5000);
            if(connection instanceof HttpURLConnection http)http.setInstanceFollowRedirects(false);
            try(InputStream in=connection.getInputStream()){
                String mime=connection.getContentType();if(mime==null||!mime.toLowerCase(Locale.ROOT).startsWith("image/"))return null;
                int limit=12*1024*1024;
                byte[] bytes=in.readNBytes(limit+1);
                if(bytes.length>limit)return null;
                Image image=decode(bytes,kind);
                if(image!=null)store(file,bytes);
                return image;
            }
        } catch(Exception e){return null;}
    }
    private Image decode(byte[] bytes,Kind kind)throws IOException {
        if(bytes.length>12*1024*1024)return null;
        try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())return null;
            var reader=readers.next();try{reader.setInput(input);if(!Set.of("png","jpeg").contains(reader.getFormatName().toLowerCase(Locale.ROOT))||(long)reader.getWidth(0)*reader.getHeight(0)>32_000_000L)return null;}finally{reader.dispose();}
        }
        var in=new ByteArrayInputStream(bytes);
        Image image=new Image(in,kind==Kind.COVER?384:1280,kind==Kind.COVER?576:480,true,true);
        return image.isError()?null:image;
    }
    private synchronized void store(Path file,byte[] bytes)throws IOException{
        Files.createDirectories(cache);
        Path tmp=Files.createTempFile(cache,"art-",".tmp");
        try { Files.write(tmp,bytes);Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
        finally {Files.deleteIfExists(tmp);}
        trim(limitMb);
    }
    public void limit(int mb){limitMb=mb;pool.execute(()->{try{trim(mb);}catch(IOException ignored){}});}
    public synchronized void trim(int mb)throws IOException{
        if(!Files.isDirectory(cache))return;
        try(var entries=Files.list(cache)){
            var files=entries.filter(f->f.getFileName().toString().matches("(?:[0-9]+-(?:COVER|BANNER)|[a-f0-9-]{36})\\.jpg"))
                .map(Path::toFile).sorted(Comparator.comparingLong(File::lastModified).reversed()).toList();
            long size=0;
            for(var file:files){
                size+=file.length();
                if(size>mb*1024L*1024L)Files.deleteIfExists(file.toPath());
            }
        }
    }
    public void retry() { misses.clear();images.clear();decodedBytes=0; }
    public Path cacheDirectory() { return cache; }
    public int limitMb() { return limitMb; }
    public synchronized long cacheSizeBytes() {
        if (!Files.isDirectory(cache)) return 0L;
        try (var entries = Files.walk(cache)) {
            return entries.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0L; }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }
    public void close() { pool.shutdownNow(); }
}
