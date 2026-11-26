package br.com.gameshelf;

import java.time.Instant;
import java.util.*;

/** Prefix sums over observed intervals. Concurrent games count once in the library calendar. */
final class Activity {
    private record Event(long time,int delta) {}
    private final long[] times,totals;
    private final int[] rates;

    Activity(List<Session> sessions,boolean mergeOverlaps) {
        List<Event> events=new ArrayList<>(2*sessions.size());
        for(Session s:sessions){
            if(s.end()<=s.start()||s.seconds()==0)continue;
            events.add(new Event(s.start(),1));events.add(new Event(s.end(),-1));
        }
        events.sort(Comparator.comparingLong(Event::time));
        times=new long[events.size()];totals=new long[times.length];rates=new int[times.length];
        int active=0;
        for(int i=0;i<events.size();i++){
            var e=events.get(i);times[i]=e.time();
            if(i>0)totals[i]=totals[i-1]+(times[i]-times[i-1])*rates[i-1];
            active+=e.delta();rates[i]=mergeOverlaps?Math.min(1,active):active;
        }
    }
    private long until(long time) {
        int low=0,high=times.length;
        while(low<high){int mid=(low+high)>>>1;if(times[mid]<=time)low=mid+1;else high=mid;}
        int i=low-1;
        return i<0?0:totals[i]+(time-times[i])*rates[i];
    }
    long between(Instant from,Instant to){return until(to.getEpochSecond())-until(from.getEpochSecond());}
}
