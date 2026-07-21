package com.skloda.agentscope.blackboard;

/**
 * Outcome of a Supervisor routing pass.
 *
 * <ul>
 *   <li>{@link #KEEP} — continue with the current activeExpert (e.g. consecutive follow-ups).</li>
 *   <li>{@link #SWITCH} — hand off to a different expert; blackboard facts are preserved.</li>
 *   <li>{@link #CLARIFY} — intent or confidence too low; ask the user a clarifying question
 *       rather than routing to an arbitrary expert.</li>
 * </ul>
 *
 * <p>{@code MULTI} (single-turn multi-expert chaining) is declared but NOT used by the
 * initial implementation — the objective explicitly scopes the vertical slice to
 * KEEP/SWITCH/CLARIFY. MULTI is listed so future strategies can extend without breaking
 * the enum contract.
 */
public enum RoutingAction {
    KEEP,
    SWITCH,
    CLARIFY,
    MULTI
}
