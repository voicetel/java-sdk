package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Support-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Support {
    private Support() {}

    public record CreateRequest(String subject, String message, String email) {
        public CreateRequest(String subject, String message) { this(subject, message, null); }
    }

    public record UpdateRequest(String status) {}

    public record ReplyRequest(String message) {}

    public record Source(String via, String type) {}
    public record Action(String text, String type) {}

    public record Actor(Integer id, String type, String email, String firstName, String lastName, String photoUrl) {}

    public record CustomFieldValue(Integer id, String value, String text) {}

    public record CustomerContactEntry(Integer id, String value, String type) {}
    public record CustomerWebsiteEntry(Integer id, String value) {}

    public record CustomerAddress(String street, String city, String state, String country, String zip) {}

    public record CustomerEmbedded(
        CustomerAddress address,
        List<CustomerContactEntry> emails,
        List<CustomerContactEntry> phones,
        List<CustomerContactEntry> socialProfiles,
        List<CustomerWebsiteEntry> websites) {}

    public record Attachment(Integer id, String mimeType, String fileName, String fileUrl, Integer size) {}

    public record ThreadEmbedded(List<Attachment> attachments) {}

    public record Customer(
        Integer id,
        String firstName,
        String lastName,
        String email,
        String company,
        String jobTitle,
        String photoType,
        String photoUrl,
        String notes,
        String type,
        String createdAt,
        String updatedAt,
        CustomerEmbedded embedded) {}

    public record Thread(
        Integer id,
        String status,
        String state,
        String type,
        String body,
        Integer rating,
        String ratingComment,
        String openedAt,
        String createdAt,
        Source source,
        Action action,
        Actor createdBy,
        Actor assignedTo,
        Customer customer,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        ThreadEmbedded embedded) {}

    public record ConversationEmbedded(List<Thread> threads) {}

    /**
     * A support ticket.
     *
     * <p>Note: the wire field {@code number} is a ticket sequence number (e.g.
     * 1015), NOT a phone number. We expose it as {@code ticketNumber} and use
     * a Jackson alias so deserialization picks up the {@code number} key.
     */
    public record Conversation(
        Integer id,
        @JsonProperty("number") @JsonAlias("ticketNumber") Integer ticketNumber,
        String status,
        String state,
        String subject,
        String preview,
        String type,
        Integer mailboxId,
        Integer folderId,
        Integer threadsCount,
        Integer closedBy,
        String closedAt,
        String createdAt,
        String updatedAt,
        String userUpdatedAt,
        Map<String, Object> customerWaitingSince,
        Source source,
        Actor createdBy,
        Actor assignee,
        Actor closedByUser,
        Customer customer,
        List<String> cc,
        List<String> bcc,
        List<CustomFieldValue> customFields,
        ConversationEmbedded embedded) {}

    public record TicketData(Conversation ticket) {}

    public record ListData(List<Conversation> tickets) {}

    public record ThreadsData(List<Thread> messages) {}

    public record ReplyData(String message) {}

    public record UpdateData(Integer id, String status) {}
}
