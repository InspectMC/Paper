package dev.systemlink.spigot.packet;

/**
 * Direction in which a packet travels relative to the server.
 */
public enum PacketDirection {
    /** A packet sent by the server to a player. */
    SENT,
    /** A packet received by the server from a player. */
    RECEIVED
}
