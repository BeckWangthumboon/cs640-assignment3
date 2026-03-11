package edu.wisc.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;
import java.util.Arrays;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
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
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		if (!isValidIpv4Checksum(ipPacket))
		{ return; }

		// Forwarding path: decrement TTL then route; silently drop on errors
		byte ttl = ipPacket.getTtl();
		if (ttl <= 1)
		{ return; }
		ipPacket.setTtl((byte)(ttl - 1));
		ipPacket.resetChecksum();

		// If packet is destined to a router interface, drop
		Iface localIface = getLocalIfaceByIp(ipPacket.getDestinationAddress());
		if (null != localIface)
		{ return; }

		RouteEntry bestMatch = this.routeTable.lookup(ipPacket.getDestinationAddress());
		if (null == bestMatch)
		{ return; }

		Iface outIface = bestMatch.getInterface();
		int nextHopIp = bestMatch.getGatewayAddress();
		if (0 == nextHopIp)
		{ nextHopIp = ipPacket.getDestinationAddress(); }

		ArpEntry arpEntry = this.arpCache.lookup(nextHopIp);
		if (null == arpEntry)
		{
			return;
		}

		MACAddress outIfaceMac = resolveIfaceMac(outIface);
		if (null == outIfaceMac)
		{ return; }
		etherPacket.setSourceMACAddress(outIfaceMac.toBytes());
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
		this.sendPacket(etherPacket, outIface);
		
		/********************************************************************/
	}

	private Iface getLocalIfaceByIp(int ip)
	{
		for (Iface iface : this.interfaces.values())
		{
			if (iface.getIpAddress() == ip)
			{ return iface; }
		}
		return null;
	}

	private boolean isValidIpv4Checksum(IPv4 ipPacket)
	{
		short original = ipPacket.getChecksum();
		ipPacket.setChecksum((short)0);
		ipPacket.serialize();
		short computed = ipPacket.getChecksum();
		ipPacket.setChecksum(original);
		return original == computed;
	}

	private MACAddress resolveIfaceMac(Iface iface)
	{
		if (null == iface)
		{ return null; }

		MACAddress mac = iface.getMacAddress();
		if (null != mac)
		{ return mac; }

		int ifaceIp = iface.getIpAddress();
		if (0 == ifaceIp)
		{ return null; }

		ArpEntry selfEntry = this.arpCache.lookup(ifaceIp);
		if (null == selfEntry)
		{ return null; }
		return selfEntry.getMac();
	}
}
