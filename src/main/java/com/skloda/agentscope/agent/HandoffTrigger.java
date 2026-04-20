package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HandoffTrigger {
    private TriggerType type;
    private List<String> keywords;
    private String target;

    public boolean matches(String text) {
        if (type != TriggerType.INTENT || keywords == null || text == null) {
            return false;
        }
        String lowerText = text.toLowerCase();
        return keywords.stream().anyMatch(kw -> lowerText.contains(kw.toLowerCase()));
    }
}
