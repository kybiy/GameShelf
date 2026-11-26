package br.com.gameshelf;

import java.util.*;
import java.util.stream.Stream;

/** A contagem é por jogo, não por processo: dois processos do mesmo jogo não dobram as horas. */
public final class Playtime {
    private final Library library;
    private final Map<String,Long> remainder=new HashMap<>();
    private Map<String,Long> sessions=new HashMap<>();
    private final Map<String,Session> observed = new HashMap<>();
    private long lastNano=-1;
    public Playtime(Library library){this.library=library;}
    public synchronized Map<String,Long> sessions(){return Map.copyOf(sessions);}
    public void poll(){List<String> paths=new ArrayList<>();try(Stream<ProcessHandle> processes=ProcessHandle.allProcesses()){processes.forEach(p->{try{p.info().command().ifPresent(paths::add);}catch(SecurityException ignored){}});}tick(paths,System.nanoTime());}
    synchronized void tick(List<String> commands,long now){
        Set<String> current=new HashSet<>();for(Game g:library.snapshot())if(g.installed()&&commands.stream().anyMatch(c->matches(g,c)))current.add(g.id());
        long delta=lastNano==-1?0:now-lastNano;lastNano=now;
        // Longas lacunas (suspensão, travamento) não são creditadas.
        if(delta<0||delta>10_000_000_000L){observed.clear();remainder.clear();delta=0;}
        long wall = java.time.Instant.now().getEpochSecond();
        Map<String,Long> next = new HashMap<>();
        for(String id:current) {
            Session active = observed.get(id);
            long seconds = 0;
            if(active==null) {
                active = new Session(UUID.randomUUID().toString(),id,wall,wall,0);
            } else {
                long total = remainder.getOrDefault(id,0L)+delta;
                seconds = total/1_000_000_000L;
                remainder.put(id,total%1_000_000_000L);
                active = new Session(active.id(),id,active.start(),active.end()+seconds,active.seconds()+seconds);
            }
            library.checkpoint(active,seconds);
            observed.put(id,active);
            next.put(id,active.seconds());
        }
        observed.keySet().retainAll(current);
        remainder.keySet().retainAll(current);
        sessions=next;
    }
    private static String normalize(String s){return s.replace('\\','/').toLowerCase(Locale.ROOT);}
    static boolean matches(Game g,String command){
        String p=normalize(command),root=normalize(g.folder()),exe=normalize(g.executable());String name=p.substring(p.lastIndexOf('/')+1);
        if(!name.endsWith(".exe"))return false;
        if(!g.monitorOverride().isBlank())return p.equals(normalize(g.monitorOverride()));
        if(g.robloxPlayer())return !root.isBlank()&&p.startsWith(root+"/")&&name.equals("robloxplayerbeta.exe");
        if(g.manual()||g.platform().equals("Riot Games"))return !exe.isBlank()&&p.equals(exe);
        if(name.matches(".*(crash|reporter|unins|uninstall|setup|installer|redist|launcher|bootstrap|anticheat|anti-cheat|easyanticheat|battleye|cefsubprocess|updater|prereq).*"))return false;
        if(!exe.isBlank()&&p.equals(exe))return true;
        // Steam e Epic: aproximação por pasta, com opção de substituir por EXE exato.
        return !root.isBlank()&&p.startsWith(root+"/");
    }
    public static String format(long seconds){return String.format("%dh %02dm %02ds",seconds/3600,(seconds%3600)/60,seconds%60);}
}
