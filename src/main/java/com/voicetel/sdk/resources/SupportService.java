package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Support;

/** Support tickets — create, read, update, delete, reply. */
public final class SupportService {
    private final Transport t;

    public SupportService(Transport t) { this.t = t; }

    public Support.ListData list() {
        return t.request("GET", "/v2.2/support/tickets", null, Support.ListData.class, true);
    }

    public Support.TicketData create(Support.CreateRequest body) {
        return t.request("POST", "/v2.2/support/tickets", null, body, Support.TicketData.class, true);
    }

    public Support.TicketData get(int id) {
        return t.request("GET", "/v2.2/support/tickets/" + id, null, Support.TicketData.class, true);
    }

    public Support.UpdateData update(int id, Support.UpdateRequest body) {
        return t.request("PUT", "/v2.2/support/tickets/" + id, null, body, Support.UpdateData.class, true);
    }

    /** Admin only. Returns nothing on 204 No Content. */
    public void delete(int id) {
        t.requestNoBody("DELETE", "/v2.2/support/tickets/" + id, null, true);
    }

    public Support.ThreadsData messages(int id) {
        return t.request("GET", "/v2.2/support/tickets/" + id + "/messages", null, Support.ThreadsData.class, true);
    }

    public Support.ReplyData reply(int id, Support.ReplyRequest body) {
        return t.request("POST", "/v2.2/support/tickets/" + id + "/replies", null, body, Support.ReplyData.class, true);
    }
}
