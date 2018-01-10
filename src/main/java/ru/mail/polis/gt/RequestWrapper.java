package ru.mail.polis.gt;

public class RequestWrapper {

    private final String id;
    private final int ack;
    private final int from;

    public RequestWrapper(String query) {
        int idx = query.indexOf("=");
        int idxLast = query.lastIndexOf("=");

        if(idx == idxLast) {
            //single node api
            id = query.substring(idx+1, query.length());
            ack = -1;
            from = -1;
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
