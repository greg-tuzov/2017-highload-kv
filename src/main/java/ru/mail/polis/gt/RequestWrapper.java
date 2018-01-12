package ru.mail.polis.gt;

import java.util.List;

public class RequestWrapper {

    private String id;
    private int ack;
    private int from;
    private byte[] data;
    private boolean wrongFormatFlag = false;
    private boolean oldAPIFlag = false;

    public boolean isWrongFormat() {
        return wrongFormatFlag;
    }

    public boolean isOldAPI() {
        return oldAPIFlag;
    }

    public RequestWrapper(String query, List<String> topology) {
        int idx = query.indexOf("=");
        int idxLast = query.lastIndexOf("=");

        if(idx == idxLast) {
            //single node api
            oldAPIFlag = true;

            id = query.substring(idx+1, query.length());
            ack = topology.size() / 2 + 1;
            from = topology.size();
        } else {
            //multi node api
            int idx2 = query.indexOf("&");
            id = query.substring(idx+1, idx2);
            int idx3 = query.lastIndexOf("/");
            ack = Integer.valueOf(query.substring(idxLast+1, idx3));
            from = Integer.valueOf(query.substring(idx3+1));
        }

        if (ack <= 0 || from <= 0 || ack > from || ack > topology.size() || from > topology.size()) {
            wrongFormatFlag = true;
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
