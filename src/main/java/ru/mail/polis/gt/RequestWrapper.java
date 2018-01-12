package ru.mail.polis.gt;

import java.util.List;

public class RequestWrapper {

    private String id;
    private int ack;
    private int from;
    private byte[] data;

    public RequestWrapper(String query, List<String> topology) {
        int idx = query.indexOf("=");
        int idxLast = query.lastIndexOf("=");

        if(idx == idxLast) {
            //single node api
            id = query.substring(idx+1, query.length());
            ack = topology.size() / 2 + 1;
            from = topology.size();
        } else {
            //multi node api
            int idx2 = query.indexOf("&");
            id = query.substring(idx+1, idx2);
            int idx3 = query.lastIndexOf("/");
            ack = Integer.valueOf(query.substring(idxLast+1, idx3));
            from = Integer.valueOf(query.substring(idx3));
        }
    }

    public String getId() {
        return id;
    }

    public int getAck() {
        return ack;
    }

    public int getFrom() {
        return from;
    }

    public boolean isIdMissed() {
        return "".equals(id);
    }
}
