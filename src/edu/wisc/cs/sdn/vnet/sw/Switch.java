package edu.wisc.cs.sdn.vnet.sw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	private static final long MAC_ENTRY_TTL_MS = 15_000;

	private static class MacEntry
	{
		Iface iface;
		long lastSeenMs;

		MacEntry(Iface iface, long lastSeenMs)
		{
			this.iface = iface;
			this.lastSeenMs = lastSeenMs;
		}
	}

	private final Map<MACAddress, MacEntry> macTable;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.macTable = new ConcurrentHashMap<MACAddress, MacEntry>();
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		long now = System.currentTimeMillis();
		evictStaleEntries(now);

		// Learn source MAC -> ingress interface
		this.macTable.put(etherPacket.getSourceMAC(), new MacEntry(inIface, now));

		MACAddress dstMac = etherPacket.getDestinationMAC();
		if (etherPacket.isBroadcast() || etherPacket.isMulticast())
		{
			flood(etherPacket, inIface);
			return;
		}

		MacEntry outEntry = this.macTable.get(dstMac);
		if (null == outEntry)
		{
			flood(etherPacket, inIface);
			return;
		}

		// Known destination on same ingress interface: drop
		if (outEntry.iface == inIface)
		{ return; }

		this.sendPacket(etherPacket, outEntry.iface);
		/********************************************************************/
	}

	private void flood(Ethernet etherPacket, Iface inIface)
	{
		for (Iface iface : this.interfaces.values())
		{
			if (iface == inIface)
			{ continue; }
			this.sendPacket(etherPacket, iface);
		}
	}

	private void evictStaleEntries(long nowMs)
	{
		for (Map.Entry<MACAddress, MacEntry> entry : this.macTable.entrySet())
		{
			if (nowMs - entry.getValue().lastSeenMs > MAC_ENTRY_TTL_MS)
			{ this.macTable.remove(entry.getKey()); }
		}
	}
}
