package com.platform.analyzer.service;

import com.platform.analyzer.domain.model.EnrichedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Applies size-based truncation to prompt content following a strict priority:
 * <ol>
 *   <li>Truncate log lines from oldest (top) first</li>
 *   <li>If still over limit, truncate events from oldest (end of array) first</li>
 *   <li>Pod description is preserved in full (never truncated unless it alone exceeds the limit)</li>
 *   <li>If pod description alone exceeds the limit, truncate at boundary and omit events/logs</li>
 * </ol>
 */
public class PromptTruncator {

    private static final Logger log = LoggerFactory.getLogger(PromptTruncator.class);

    private final int maxPromptBytes;

    public PromptTruncator(int maxPromptBytes) {
        this.maxPromptBytes = maxPromptBytes;
    }

    /**
     * Truncates the MCP context sections to fit within the remaining byte budget.
     *
     * @param basePromptSize the byte size of the prompt before MCP context is appended
     * @param context        the enriched MCP context with pod description, events, and logs
     * @return a new EnrichedContext with truncated content, or the original if within budget
     */
    public EnrichedContext truncateIfNeeded(int basePromptSize, EnrichedContext context) {
        if (context == null || !context.hasContent()) {
            return context;
        }

        int remainingBudget = maxPromptBytes - basePromptSize;

        if (remainingBudget <= 0) {
            log.warn("Base prompt already exceeds max size ({} bytes). Omitting MCP context entirely.",
                    basePromptSize);
            return EnrichedContext.EMPTY;
        }

        String podDescription = context.podDescription();
        String podEvents = context.podEvents();
        String podLogs = context.podLogs();

        int descriptionBytes = byteSize(podDescription);
        int eventsBytes = byteSize(podEvents);
        int logsBytes = byteSize(podLogs);

        int headerOverhead = computeHeaderOverhead(podDescription, podEvents, podLogs);
        int totalMcpBytes = descriptionBytes + eventsBytes + logsBytes + headerOverhead;

        if (totalMcpBytes <= remainingBudget) {
            return context;
        }

        log.debug("MCP context ({} bytes) exceeds remaining budget ({} bytes). Applying truncation.",
                totalMcpBytes, remainingBudget);

        // Pod description alone exceeds remaining budget — truncate it, omit events/logs
        int descHeaderOverhead = computeHeaderOverhead(podDescription, null, null);
        if (descriptionBytes + descHeaderOverhead > remainingBudget) {
            if (podDescription != null) {
                log.warn("Pod description alone ({} bytes) exceeds budget ({} bytes). Truncating description, omitting events/logs.",
                        descriptionBytes + descHeaderOverhead, remainingBudget);
                String truncatedDesc = truncateToBytes(podDescription, remainingBudget - descHeaderOverhead);
                return new EnrichedContext(truncatedDesc, null, null, context.toolsUsed());
            }
            return EnrichedContext.EMPTY;
        }

        int budgetAfterDescription = remainingBudget - descriptionBytes - descHeaderOverhead;

        int eventsHeaderBytes = podEvents != null ? sectionHeaderBytes("--- EVENTS ---") : 0;
        int logsHeaderBytes = podLogs != null ? sectionHeaderBytes("--- LOGS ---") : 0;

        int availableForEventsAndLogs = budgetAfterDescription - eventsHeaderBytes - logsHeaderBytes;

        // Truncate logs first (oldest lines removed from top), then events if needed
        String truncatedLogs = podLogs;
        String truncatedEvents = podEvents;

        if (logsBytes + eventsBytes > availableForEventsAndLogs) {
            // Try to keep all events, truncate logs only
            int budgetForLogs = availableForEventsAndLogs - eventsBytes;

            if (budgetForLogs > 0 && truncatedLogs != null) {
                truncatedLogs = truncateLogsFromOldest(truncatedLogs, budgetForLogs);
            } else if (budgetForLogs <= 0) {
                truncatedLogs = null;
                int revisedAvailable = budgetAfterDescription - eventsHeaderBytes;

                if (eventsBytes > revisedAvailable && truncatedEvents != null) {
                    truncatedEvents = truncateEventsFromOldest(truncatedEvents, revisedAvailable);
                }
            }

            // After log truncation, check if we're still over
            int currentEventsBytes = byteSize(truncatedEvents);
            int currentLogsBytes = byteSize(truncatedLogs);
            int actualLogsHeader = truncatedLogs != null ? logsHeaderBytes : 0;
            int actualEventsHeader = truncatedEvents != null ? eventsHeaderBytes : 0;

            if (currentEventsBytes + currentLogsBytes + actualEventsHeader + actualLogsHeader > budgetAfterDescription) {
                // Logs truncation wasn't enough — remove logs entirely and truncate events
                truncatedLogs = null;
                int revisedAvailable = budgetAfterDescription - actualEventsHeader;
                if (currentEventsBytes > revisedAvailable && truncatedEvents != null) {
                    truncatedEvents = truncateEventsFromOldest(truncatedEvents, revisedAvailable);
                }
            }
        }

        return new EnrichedContext(podDescription, truncatedEvents, truncatedLogs, context.toolsUsed());
    }

    /**
     * Truncates log content by removing lines from the oldest (top) first.
     */
    String truncateLogsFromOldest(String logs, int maxBytes) {
        if (logs == null || byteSize(logs) <= maxBytes) {
            return logs;
        }

        String[] lines = logs.split("\n");
        StringBuilder result = new StringBuilder();
        int currentBytes = 0;

        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];
            int lineBytes = byteSize(line) + 1; // +1 for newline
            if (currentBytes + lineBytes > maxBytes) {
                break;
            }
            if (result.isEmpty()) {
                result.insert(0, line);
            } else {
                result.insert(0, line + "\n");
            }
            currentBytes += lineBytes;
        }

        String truncated = result.toString();
        if (truncated.isEmpty()) {
            return null;
        }
        return truncated;
    }

    /**
     * Truncates events content by removing entries from oldest (end of text) first.
     * Events are assumed to be ordered newest-first (descending by lastTimestamp),
     * so we remove from the end of the string.
     */
    String truncateEventsFromOldest(String events, int maxBytes) {
        if (events == null || byteSize(events) <= maxBytes) {
            return events;
        }

        String[] lines = events.split("\n");
        // Events are ordered newest-first, so keep from the top
        StringBuilder result = new StringBuilder();
        int currentBytes = 0;

        for (String line : lines) {
            int lineBytes = byteSize(line) + 1; // +1 for newline
            if (currentBytes + lineBytes > maxBytes) {
                break;
            }
            if (!result.isEmpty()) {
                result.append("\n");
            }
            result.append(line);
            currentBytes += lineBytes;
        }

        String truncated = result.toString();
        if (truncated.isEmpty()) {
            return null;
        }
        return truncated;
    }

    /**
     * Truncates a string to fit within the given byte budget, cutting at a safe boundary.
     */
    String truncateToBytes(String content, int maxBytes) {
        if (content == null || maxBytes <= 0) {
            return null;
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return content;
        }
        // Find a safe UTF-8 boundary by decoding up to maxBytes
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8)
                .substring(0, new String(bytes, 0, maxBytes, StandardCharsets.UTF_8).length());
    }

    private int computeHeaderOverhead(String podDescription, String podEvents, String podLogs) {
        int overhead = byteSize("=== CLUSTER CONTEXT (MCP) ===\n");
        if (podDescription != null) {
            overhead += sectionHeaderBytes("--- POD DESCRIPTION ---");
        }
        if (podEvents != null) {
            overhead += sectionHeaderBytes("--- EVENTS ---");
        }
        if (podLogs != null) {
            overhead += sectionHeaderBytes("--- LOGS ---");
        }
        return overhead;
    }

    private int sectionHeaderBytes(String headerLabel) {
        return byteSize("\n" + headerLabel + "\n");
    }

    private static int byteSize(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }
}
