package edu.wisc.cs.sdn.vnet.rt;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	private static final int RIP_MULTICAST_IP = IPv4.toIPv4Address("224.0.0.9");
	private static final String ETHER_BROADCAST = "ff:ff:ff:ff:ff:ff";
	private static final byte RIP_TTL = 64;
	private static final int RIP_INFINITY = 16;
	private static final long RIP_UPDATE_INTERVAL_MS = 10000;
	private static final long RIP_ROUTE_TIMEOUT_MS = 30000;
	private static final long RIP_EXPIRE_CHECK_INTERVAL_MS = 1000;

	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	/** True if router is running RIP mode */
	private boolean ripEnabled;

	/** Timer for unsolicited RIP responses */
	private Timer ripUpdateTimer;

	/** Timer for dynamic-route expiry checks */
	private Timer ripExpireTimer;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.ripEnabled = false;
		this.ripUpdateTimer = null;
		this.ripExpireTimer = null;
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

		// RIP control packets are consumed locally
		if (this.ripEnabled && isRipPacket(ipPacket))
		{
			handleRipPacket(etherPacket, ipPacket, inIface);
			return;
		}

		// If packet is destined to a router interface, drop
		Iface localIface = getLocalIfaceByIp(ipPacket.getDestinationAddress());
		if (null != localIface)
		{ return; }

		// Forwarding path: decrement TTL then route; silently drop on errors
		byte ttl = ipPacket.getTtl();
		if (ttl <= 1)
		{ return; }
		ipPacket.setTtl((byte)(ttl - 1));
		ipPacket.resetChecksum();

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

	public synchronized void startRip()
	{
		if (this.ripEnabled)
		{ return; }

		this.ripEnabled = true;
		addDirectlyConnectedRoutes();
		sendRipRequestOnAllInterfaces();
		sendUnsolicitedRipResponses();

		this.ripUpdateTimer = new Timer(true);
		this.ripUpdateTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{ sendUnsolicitedRipResponses(); }
		}, RIP_UPDATE_INTERVAL_MS, RIP_UPDATE_INTERVAL_MS);

		this.ripExpireTimer = new Timer(true);
		this.ripExpireTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{ expireDynamicRoutes(); }
		}, RIP_EXPIRE_CHECK_INTERVAL_MS, RIP_EXPIRE_CHECK_INTERVAL_MS);
	}

	@Override
	public synchronized void destroy()
	{
		if (null != this.ripUpdateTimer)
		{ this.ripUpdateTimer.cancel(); }
		if (null != this.ripExpireTimer)
		{ this.ripExpireTimer.cancel(); }
		super.destroy();
	}

	private void addDirectlyConnectedRoutes()
	{
		long now = System.currentTimeMillis();
		for (Iface iface : this.interfaces.values())
		{
			if (0 == iface.getIpAddress() || 0 == iface.getSubnetMask())
			{ continue; }

			int subnet = iface.getIpAddress() & iface.getSubnetMask();
			RouteEntry route = this.routeTable.lookupExact(subnet, iface.getSubnetMask());
			if (null == route)
			{ route = this.routeTable.insert(subnet, 0, iface.getSubnetMask(), iface); }
			else
			{
				route.setGatewayAddress(0);
				route.setInterface(iface);
			}

			route.setMetric(1);
			route.setDirectlyConnected(true);
			route.setRipLearned(false);
			route.setLastUpdated(now);
		}
	}

	private boolean isRipPacket(IPv4 ipPacket)
	{
		if (ipPacket.getProtocol() != IPv4.PROTOCOL_UDP)
		{ return false; }
		if (!(ipPacket.getPayload() instanceof UDP))
		{ return false; }

		UDP udpPacket = (UDP) ipPacket.getPayload();
		return udpPacket.getDestinationPort() == UDP.RIP_PORT;
	}

	private void handleRipPacket(Ethernet etherPacket, IPv4 ipPacket, Iface inIface)
	{
		UDP udpPacket = (UDP) ipPacket.getPayload();
		if (!(udpPacket.getPayload() instanceof RIPv2))
		{ return; }

		RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
		if (ripPacket.getCommand() == RIPv2.COMMAND_REQUEST)
		{
			sendRipResponse(inIface, ipPacket.getSourceAddress(),
					etherPacket.getSourceMACAddress());
		}
		else if (ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE)
		{
			processRipResponse(ripPacket, ipPacket.getSourceAddress(), inIface);
		}
	}

	private void processRipResponse(RIPv2 ripPacket, int neighborIp, Iface inIface)
	{
		long now = System.currentTimeMillis();
		for (RIPv2Entry entry : ripPacket.getEntries())
		{
			if (entry.getAddressFamily() != RIPv2Entry.ADDRESS_FAMILY_IPv4)
			{ continue; }

			int destIp = entry.getAddress() & entry.getSubnetMask();
			int maskIp = entry.getSubnetMask();
			int metric = Math.min(RIP_INFINITY, entry.getMetric() + 1);

			RouteEntry existing = this.routeTable.lookupExact(destIp, maskIp);
			if (null != existing && existing.isDirectlyConnected())
			{ continue; }

			if (null == existing)
			{
				if (metric >= RIP_INFINITY)
				{ continue; }

				RouteEntry learned = this.routeTable.insert(destIp, neighborIp, maskIp, inIface);
				learned.setMetric(metric);
				learned.setRipLearned(true);
				learned.setDirectlyConnected(false);
				learned.setLastUpdated(now);
				continue;
			}

			if (!existing.isRipLearned())
			{ continue; }

			boolean sameNextHop = (existing.getGatewayAddress() == neighborIp)
					&& (existing.getInterface() == inIface);
			if (sameNextHop)
			{
				if (metric >= RIP_INFINITY)
				{ this.routeTable.remove(destIp, maskIp); }
				else
				{
					existing.setMetric(metric);
					existing.setLastUpdated(now);
					existing.setGatewayAddress(neighborIp);
					existing.setInterface(inIface);
				}
			}
			else if (metric < existing.getMetric() && metric < RIP_INFINITY)
			{
				existing.setMetric(metric);
				existing.setLastUpdated(now);
				existing.setGatewayAddress(neighborIp);
				existing.setInterface(inIface);
			}
		}
	}

	private void expireDynamicRoutes()
	{
		long now = System.currentTimeMillis();
		List<RouteEntry> routes = this.routeTable.getEntriesSnapshot();
		for (RouteEntry route : routes)
		{
			if (!route.isRipLearned())
			{ continue; }
			if (route.isDirectlyConnected())
			{ continue; }
			if (now - route.getLastUpdated() <= RIP_ROUTE_TIMEOUT_MS)
			{ continue; }
			this.routeTable.remove(route.getDestinationAddress(), route.getMaskAddress());
		}
	}

	private void sendRipRequestOnAllInterfaces()
	{
		for (Iface iface : this.interfaces.values())
		{
			sendRipRequest(iface);
		}
	}

	private void sendRipRequest(Iface iface)
	{
		RIPv2 request = new RIPv2();
		request.setCommand(RIPv2.COMMAND_REQUEST);
		RIPv2Entry wildcard = new RIPv2Entry();
		wildcard.setAddressFamily((short)0);
		wildcard.setRouteTag((short)0);
		wildcard.setAddress(0);
		wildcard.setSubnetMask(0);
		wildcard.setNextHopAddress(0);
		wildcard.setMetric(RIP_INFINITY);
		request.addEntry(wildcard);

		sendRipPacket(iface, RIP_MULTICAST_IP,
				Ethernet.toMACAddress(ETHER_BROADCAST), request);
	}

	private void sendUnsolicitedRipResponses()
	{
		for (Iface iface : this.interfaces.values())
		{
			sendRipResponse(iface, RIP_MULTICAST_IP,
					Ethernet.toMACAddress(ETHER_BROADCAST));
		}
	}

	private void sendRipResponse(Iface outIface, int dstIp, byte[] dstMac)
	{
		RIPv2 response = new RIPv2();
		response.setCommand(RIPv2.COMMAND_RESPONSE);
		for (RouteEntry route : this.routeTable.getEntriesSnapshot())
		{
			RIPv2Entry ripEntry = new RIPv2Entry();
			ripEntry.setAddressFamily(RIPv2Entry.ADDRESS_FAMILY_IPv4);
			ripEntry.setRouteTag((short)0);
			ripEntry.setAddress(route.getDestinationAddress());
			ripEntry.setSubnetMask(route.getMaskAddress());
			ripEntry.setNextHopAddress(0);

			int metric = route.getMetric();
			if (metric < 1)
			{ metric = 1; }
			if (metric > RIP_INFINITY)
			{ metric = RIP_INFINITY; }
			ripEntry.setMetric(metric);
			response.addEntry(ripEntry);
		}

		sendRipPacket(outIface, dstIp, dstMac, response);
	}

	private void sendRipPacket(Iface outIface, int dstIp, byte[] dstMac, RIPv2 ripPayload)
	{
		if (null == outIface || null == outIface.getMacAddress())
		{ return; }

		Ethernet etherPacket = new Ethernet();
		etherPacket.setEtherType(Ethernet.TYPE_IPv4);
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());
		etherPacket.setDestinationMACAddress(dstMac);

		IPv4 ipPacket = new IPv4();
		ipPacket.setTtl(RIP_TTL);
		ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
		ipPacket.setSourceAddress(outIface.getIpAddress());
		ipPacket.setDestinationAddress(dstIp);

		UDP udpPacket = new UDP();
		udpPacket.setSourcePort(UDP.RIP_PORT);
		udpPacket.setDestinationPort(UDP.RIP_PORT);
		udpPacket.setChecksum((short)0);
		udpPacket.setPayload(ripPayload);

		ipPacket.setChecksum((short)0);
		ipPacket.setPayload(udpPacket);
		etherPacket.setPayload(ipPacket);
		this.sendPacket(etherPacket, outIface);
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
