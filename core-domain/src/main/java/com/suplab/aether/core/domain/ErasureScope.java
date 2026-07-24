package com.suplab.aether.core.domain;

/**
 * The breadth of a GDPR right-to-erasure (Article 17) request against Aether Core.
 *
 * <ul>
 *   <li>{@code MEMORIES} — erase the user's personal memories only (active + archived), leaving
 *       their cognitive sessions and preferences intact.</li>
 *   <li>{@code ACCOUNT} — full account erasure: memories (active + archived), cognitive sessions,
 *       and preferences — every record Core holds for the user.</li>
 * </ul>
 */
public enum ErasureScope {
    MEMORIES,
    ACCOUNT
}
