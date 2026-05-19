package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

/** iNumbering-resource models — inventory, orders, port-ins. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class INumbering {
    private INumbering() {}

    /**
     * A single entry in {@link OrderCreateRequest#numbers()}. Use
     * {@link #of(String)} for a plain TN string, or {@link #of(String, Integer)}
     * for a `{number, route}` object.
     */
    public static final class OrderNumber {
        private final String plain;
        private final OrderNumberSpec spec;

        private OrderNumber(String plain, OrderNumberSpec spec) {
            this.plain = plain;
            this.spec = spec;
        }

        public static OrderNumber of(String number) {
            return new OrderNumber(number, null);
        }

        public static OrderNumber of(String number, Integer route) {
            return new OrderNumber(null, new OrderNumberSpec(number, route));
        }

        /** Jackson serializes either a string or a `{number, route}` object. */
        @JsonValue
        public Object value() {
            return plain != null ? plain : spec;
        }
    }

    public record OrderNumberSpec(String number, Integer route) {}

    public record OrderCreateRequest(List<OrderNumber> numbers) {}

    public record PortFeatureLidb(String name) {}

    public record PortFeatureRouting(Integer gatewayId) {}

    public record PortFeatureSms(String campaignId) {}

    public record PortFeature(String number, PortFeatureRouting routing, PortFeatureLidb lidb, PortFeatureSms sms) {}

    public record PortSubmitRequest(
        List<String> did,
        String name,
        String nameType,
        String lcBtn,
        String lcAccountNumber,
        String streetNumber,
        String street,
        String streetType,
        String city,
        String state,
        String zip,
        String country,
        String authPerson,
        String streetPrefix,
        String streetSuffix,
        String floor,
        String room,
        String building,
        String unitValue,
        String desiredDueDate,
        String pin,
        List<PortFeature> features) {}

    public record InventoryItem(String number, String rateCenter, String city, String province, String lata) {}

    public record InventoryCoverageItem(
        Integer count,
        String npa,
        String nxx,
        String block,
        String city,
        String rcAbbre,
        String lata,
        String locState) {}

    public record PortSummary(
        String status,
        String id,
        String pid,
        String foc,
        String createdAt,
        String message,
        String supportUrl) {}

    public record PortDetail(
        String status,
        String id,
        String pid,
        String name,
        String email,
        String foc,
        String createdAt,
        List<String> numbers,
        String message) {}

    public record InventorySearchData(List<InventoryItem> numbers) {}

    public record InventoryCoverageData(List<InventoryCoverageItem> coverage) {}

    public record OrderFailedEntry(String number, String reason) {}

    public record OrderCreateData(String orderId, Double amountCharged, List<String> numbersOrdered, List<OrderFailedEntry> failed) {}

    public record PortListData(List<PortSummary> ports) {}

    public record PortDetailData(PortDetail port) {}

    public record PortSubmitData(String pid, Integer ticket, String message, String loaUrl, String portUrl) {}

    /** v2.2.10 added {@code localRoutingNumber} and {@code rateCenterTier}. */
    public record PortAvailabilityData(
        String number,
        Boolean portable,
        String losingCarrier,
        String localRoutingNumber,
        String rateCenterTier,
        String reason) {}
}
