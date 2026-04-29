package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sample prompt for agent showcase.
 * Contains a prompt example and expected behavior description.
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SamplePrompt {

    /**
     * The example prompt text that users can click to use
     */
    private String prompt;

    /**
     * Description of expected behavior when this prompt is used
     */
    private String expectedBehavior;
}
