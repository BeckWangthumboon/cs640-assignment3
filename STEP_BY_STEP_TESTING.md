# CS640 Lab 3 Testing Guide

This guide follows `CS640-Lab3-1.pdf` and focuses on RIP-based router testing.

## 1) Test checklist

Mark each one when done.

### Router RIP topologies

- [ ] `topos/pair_rt.topo`
- [ ] `topos/triangle_rt.topo`
- [ ] `topos/linear5_rt.topo`

### Mixed switch + router topology

- [ ] `topos/triangle_with_sw.topo`

### Optional switch smoke tests

- [ ] `topos/single_sw.topo`
- [ ] `topos/linear5_sw.topo`

## 2) One-time setup

```bash
cd ~/assign3
./config.sh
```

## 3) Build before each test run

```bash
cd ~/assign3
ant clean
ant
```

## 4) Standard Lab 3 test flow

Open 3 terminals:

- Terminal A: Mininet
- Terminal B: POX
- Terminal C: VirtualNetwork device process(es)

### Step 1: Start Mininet

```bash
cd ~/assign3
sudo ./run_mininet.py <TOPO_FILE> -a
```

Example:

```bash
sudo ./run_mininet.py topos/pair_rt.topo -a
```

Notes:

- `-a` preloads static ARP entries into hosts.
- `run_mininet.py` may generate `rtable.rX`, but Lab 3 router launches should
  ignore those files.

### Step 2: Start POX

```bash
cd ~/assign3
./run_pox.sh
```

Wait until POX shows the OpenFlow device connections.

### Step 3: Start switches and routers

Option A: helper script

```bash
cd ~/assign3
./run_devices.sh start <TOPO_FILE> --kill-first
```

Option B: manual mode

Switch form:

```bash
java -jar VirtualNetwork.jar -v sX
```

Router form for Lab 3 RIP mode:

```bash
java -jar VirtualNetwork.jar -v rX -a arp_cache
```

Important:

- Do not pass `-r rtable.rX` in Lab 3.
- RIP should start only when the router is launched without `-r`.

### Step 4: Allow RIP to converge

Wait about 10 to 15 seconds before declaring failure on multi-router
topologies. RIP requests are sent immediately, but route exchange may take a
short moment to settle.

### Step 5: Run connectivity tests

At the Mininet prompt:

```bash
pingall
```

If needed, retry after a short wait.

For a more focused path check:

```bash
h1 traceroute -n <destination-host-ip>
```

Pass criteria:

- hosts in different subnets become reachable
- traceroute shows routing across the expected routers

### Step 6: Run failover test

Use `topos/triangle_rt.topo`.

1. Confirm connectivity first with `pingall`.
2. Stop one router process, for example by pressing Ctrl-C in the `r1` process.
3. Wait about 35 to 45 seconds.
4. Run connectivity checks again.

Expected result:

- traffic should recover using the alternate path after RIP reconverges

### Step 7: Optional packet capture

From the Mininet CLI:

```bash
xterm h1
```

Then in the new xterm:

```bash
sudo tcpdump -n -vv -e -i h1-eth0
```

This is useful for verifying RIP timing or forwarded traffic headers.

## 5) Exact device commands by topology

### `topos/pair_rt.topo`

```bash
java -jar VirtualNetwork.jar -v r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -a arp_cache
```

### `topos/triangle_rt.topo`

```bash
java -jar VirtualNetwork.jar -v r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -a arp_cache
java -jar VirtualNetwork.jar -v r3 -a arp_cache
```

### `topos/linear5_rt.topo`

```bash
java -jar VirtualNetwork.jar -v r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -a arp_cache
java -jar VirtualNetwork.jar -v r3 -a arp_cache
java -jar VirtualNetwork.jar -v r4 -a arp_cache
java -jar VirtualNetwork.jar -v r5 -a arp_cache
```

### `topos/triangle_with_sw.topo`

```bash
java -jar VirtualNetwork.jar -v s1
java -jar VirtualNetwork.jar -v s2
java -jar VirtualNetwork.jar -v s3
java -jar VirtualNetwork.jar -v r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -a arp_cache
java -jar VirtualNetwork.jar -v r3 -a arp_cache
```

### Optional switch-only topologies

`topos/single_sw.topo`

```bash
java -jar VirtualNetwork.jar -v s1
```

`topos/linear5_sw.topo`

```bash
java -jar VirtualNetwork.jar -v s1
java -jar VirtualNetwork.jar -v s2
java -jar VirtualNetwork.jar -v s3
java -jar VirtualNetwork.jar -v s4
java -jar VirtualNetwork.jar -v s5
```

## 6) Cleanup between tests

In the Mininet terminal:

```bash
exit
```

Stop VirtualNetwork processes:

```bash
cd ~/assign3
./run_devices.sh stop
```

Optional manual cleanup:

```bash
pkill -f 'VirtualNetwork.jar -v' || true
pkill -f pox.py || true
sudo mn -c
```

After cleanup, start the next test from Section 4 Step 1.
