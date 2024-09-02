package cn.dioxide.infra;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Dioxide.CN
 * @date 2024/8/31
 * @since 1.0
 */
public class Statistic {
    
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    
    private static final TimeZone ZONE = TimeZone.getTimeZone(ZoneId.systemDefault());
    
    private final ConcurrentLinkedQueue<Record> records = new ConcurrentLinkedQueue<>();
    
    public Statistic() {
        Statistic.FORMATTER.setTimeZone(Statistic.ZONE);
    }
    
    public void put(final int sCnt, final int fCnt, final int pCnt) {
        records.offer(new Record(now(), sCnt, fCnt, pCnt));
    }
    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("[");
        while (!records.isEmpty()) {
            Record rec = records.poll();
            if (rec != null) {
                builder.append(rec);
            }
        }
        return builder.append("]").toString();
    }
    
    private String now() {
        return Statistic.FORMATTER.format(new Date(System.currentTimeMillis()));
    }
    
    private record Record(String time, int sCnt, int fCnt, int pCnt) {
        @Override
        public String toString() {
            // time, success count, fail count, pending count
            return STR."""
                    { type: '成功', t: '\{time}', cnt: \{sCnt} },
                    { type: '失败', t: '\{time}', cnt: \{fCnt} },
                    { type: '异常', t: '\{time}', cnt: \{pCnt} },
                    """;
        }
    }
    
}
