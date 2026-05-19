package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.INumbering;

import java.util.LinkedHashMap;
import java.util.Map;

/** Inventory searches, orders, and port-ins. */
public final class INumberingService {
    private final Transport t;

    public INumberingService(Transport t) { this.t = t; }

    /** Filters for {@link #searchInventory}. */
    public static final class InventoryQuery {
        public Integer npa;
        public Integer nxx;
        public String state;
        public String rateCenter;
        public String contains;
        public String endsWith;
        public Integer limit;

        public InventoryQuery() {}
        public InventoryQuery npa(int v) { this.npa = v; return this; }
        public InventoryQuery nxx(int v) { this.nxx = v; return this; }
        public InventoryQuery state(String v) { this.state = v; return this; }
        public InventoryQuery rateCenter(String v) { this.rateCenter = v; return this; }
        public InventoryQuery contains(String v) { this.contains = v; return this; }
        public InventoryQuery endsWith(String v) { this.endsWith = v; return this; }
        public InventoryQuery limit(int v) { this.limit = v; return this; }
    }

    /** Filters for {@link #coverage}. */
    public static final class CoverageQuery {
        public String state;
        public String rateCenter;

        public CoverageQuery() {}
        public CoverageQuery state(String v) { this.state = v; return this; }
        public CoverageQuery rateCenter(String v) { this.rateCenter = v; return this; }
    }

    public INumbering.InventorySearchData searchInventory(InventoryQuery q) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (q.npa != null) m.put("npa", q.npa);
        if (q.nxx != null) m.put("nxx", q.nxx);
        if (q.state != null) m.put("state", q.state);
        if (q.rateCenter != null) m.put("ratecenter", q.rateCenter);
        if (q.contains != null) m.put("contains", q.contains);
        if (q.endsWith != null) m.put("endswith", q.endsWith);
        if (q.limit != null) m.put("limit", q.limit);
        return t.request("GET", "/v2.2/inventory", m, INumbering.InventorySearchData.class, true);
    }

    public INumbering.InventoryCoverageData coverage(CoverageQuery q) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (q.state != null) m.put("state", q.state);
        if (q.rateCenter != null) m.put("ratecenter", q.rateCenter);
        return t.request("GET", "/v2.2/inventory/coverage", m, INumbering.InventoryCoverageData.class, true);
    }

    /** Purchase new TNs. */
    public INumbering.OrderCreateData order(INumbering.OrderCreateRequest body) {
        return t.request("POST", "/v2.2/orders", null, body, INumbering.OrderCreateData.class, true);
    }

    public INumbering.PortListData ports() {
        return t.request("GET", "/v2.2/ports", null, INumbering.PortListData.class, true);
    }

    public INumbering.PortDetailData port(int id) {
        return t.request("GET", "/v2.2/ports/" + id, null, INumbering.PortDetailData.class, true);
    }

    public INumbering.PortSubmitData submitPort(INumbering.PortSubmitRequest body) {
        return t.request("POST", "/v2.2/ports", null, body, INumbering.PortSubmitData.class, true);
    }

    public INumbering.PortAvailabilityData portAvailability(String number) {
        return t.request("GET", "/v2.2/ports/availability/" + number, null, INumbering.PortAvailabilityData.class, true);
    }
}
