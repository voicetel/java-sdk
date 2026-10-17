package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.INumbering;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.voicetel.sdk.Transport.encodePathSegment;

/** Inventory searches, orders, and port-ins. */
public final class INumberingService {
    private final Transport t;

    public INumberingService(Transport t) { this.t = t; }

    /**
     * Filters for {@link #searchInventory}. {@code tnMask} (prefix or
     * wildcard pattern, e.g. {@code "201"}, {@code "201555"},
     * {@code "201555*"}) is required.
     */
    public static final class InventoryQuery {
        public final String tnMask;
        public Integer quantity;
        public String province;
        public String rateCenter;
        public String lata;

        public InventoryQuery(String tnMask) { this.tnMask = tnMask; }
        public InventoryQuery quantity(int v) { this.quantity = v; return this; }
        public InventoryQuery province(String v) { this.province = v; return this; }
        public InventoryQuery rateCenter(String v) { this.rateCenter = v; return this; }
        public InventoryQuery lata(String v) { this.lata = v; return this; }
    }

    /** Aggregation dimension for {@link #coverage}. */
    public enum CountBy {
        STATE("state"),
        CITY("city"),
        RATE_CENTER("rateCenter"),
        NPANXX("npanxx"),
        BLOCK("block");

        private final String wire;
        CountBy(String wire) { this.wire = wire; }
        @Override public String toString() { return wire; }
    }

    /** Filters for {@link #coverage}. {@code countBy} is required. */
    public static final class CoverageQuery {
        public final CountBy countBy;
        public String locState;
        public String city;
        public String rcAbbre;
        public String npa;
        public String lata;

        public CoverageQuery(CountBy countBy) { this.countBy = countBy; }
        public CoverageQuery locState(String v) { this.locState = v; return this; }
        public CoverageQuery city(String v) { this.city = v; return this; }
        public CoverageQuery rcAbbre(String v) { this.rcAbbre = v; return this; }
        public CoverageQuery npa(String v) { this.npa = v; return this; }
        public CoverageQuery lata(String v) { this.lata = v; return this; }
    }

    public INumbering.InventorySearchData searchInventory(InventoryQuery q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tnMask", q.tnMask);
        if (q.quantity != null) m.put("quantity", q.quantity);
        if (q.province != null) m.put("province", q.province);
        if (q.rateCenter != null) m.put("rateCenter", q.rateCenter);
        if (q.lata != null) m.put("lata", q.lata);
        return t.request("GET", "/v2.2/inventory", m, INumbering.InventorySearchData.class, true);
    }

    public INumbering.InventoryCoverageData coverage(CoverageQuery q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("countBy", q.countBy);
        if (q.locState != null) m.put("locState", q.locState);
        if (q.city != null) m.put("city", q.city);
        if (q.rcAbbre != null) m.put("rcAbbre", q.rcAbbre);
        if (q.npa != null) m.put("npa", q.npa);
        if (q.lata != null) m.put("lata", q.lata);
        return t.request("GET", "/v2.2/inventory/coverage", m, INumbering.InventoryCoverageData.class, true);
    }

    /** Purchase new TNs. */
    public INumbering.OrderCreateData order(INumbering.OrderCreateRequest body) {
        return t.request("POST", "/v2.2/orders", null, body, INumbering.OrderCreateData.class, true);
    }

    public INumbering.PortListData ports() {
        return t.request("GET", "/v2.2/ports", null, INumbering.PortListData.class, true);
    }

    /**
     * Get a single port-in record. {@code id} is the 5-character alphanumeric
     * pid from the list response's {@code pid} field.
     */
    public INumbering.PortDetailData port(String id) {
        return t.request("GET", "/v2.2/ports/" + encodePathSegment(id), null, INumbering.PortDetailData.class, true);
    }

    public INumbering.PortSubmitData submitPort(INumbering.PortSubmitRequest body) {
        return t.request("POST", "/v2.2/ports", null, body, INumbering.PortSubmitData.class, true);
    }

    public INumbering.PortAvailabilityData portAvailability(String number) {
        return t.request("GET", "/v2.2/ports/availability/" + encodePathSegment(number), null, INumbering.PortAvailabilityData.class, true);
    }
}
