# CS640 Lab 2 Testing Guide (Checklist + Step-by-Step)

This guide follows `CS640_Lab_2.pdf` and matches this repo.

## 1) Test Case Checklist (from Lab 2)

Mark each one when done.

### Switch topologies (Part 2)

- [] `topos/single_sw.topo`
- [] `topos/linear5_sw.topo`
- [] `topos/inclass_sw.topo`

### Router topologies (Part 3)

- [ ] `topos/single_rt.topo`
- [ ] `topos/pair_rt.topo`
- [ ] `topos/triangle_rt.topo`
- [ ] `topos/linear5_rt.topo`

### Combined switch + router topologies

- [ ] `topos/single_each.topo`
- [ ] `topos/triangle_with_sw.topo`

## 2) Setup (one-time, then quick checks before each test)

### One-time setup

```bash
cd ~/Projects/assign2
./config.sh
```

### Build before testing

```bash
cd ~/Projects/assign2
ant clean
ant
```

### Open 3 terminals for each test run

- Terminal A: Mininet
- Terminal B: POX
- Terminal C: VirtualNetwork device process(es)

## 3) Running a Test Case (same flow every time)

Use these steps for each topology in the checklist.

### Step 1: Start Mininet (Terminal A)

```bash
cd ~/Projects/assign2
sudo ./run_mininet.py <TOPO_FILE> -a
```

Example:

```bash
sudo ./run_mininet.py topos/single_sw.topo -a
```

### Step 2: Start POX (Terminal B)

```bash
cd ~/Projects/assign2
./run_pox.sh
```

Wait until POX shows the OpenFlow device connection(s). Do not start Java first.

### Step 3: Start all required devices (Terminal C)

Option A (recommended): start all devices automatically from topology file.

```bash
cd ~/Projects/assign2
./run_devices.sh start <TOPO_FILE> --kill-first
```

Option B (manual): start one Java process per switch/router.

Switch form:

```bash
java -jar VirtualNetwork.jar -v sX
```

Router form:

```bash
java -jar VirtualNetwork.jar -v rX -r rtable.rX -a arp_cache
```

If topology has multiple devices, run all of them.

### Step 4: Run traffic tests in Mininet CLI (Terminal A)

For any topology, run:

```bash
pingall
```

For switch-focused verification, also run at least one HTTP fetch:

```bash
h1 curl -s 10.0.1.102 | head
```

If `h1`/IP is not in the topology, use any host and destination host IP from that topology.

### Step 5: Record result

- Pass if `pingall` reports 0% dropped (or expected success between reachable hosts).
- Pass switch test when `curl` returns HTML content.
- Mark the corresponding checkbox in Section 1.

## 4) Exact Device Commands by Topology (manual mode reference)

### Switch topologies

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

`topos/inclass_sw.topo`

```bash
java -jar VirtualNetwork.jar -v s1
java -jar VirtualNetwork.jar -v s2
java -jar VirtualNetwork.jar -v s3
java -jar VirtualNetwork.jar -v s4
java -jar VirtualNetwork.jar -v s5
```

### Router topologies

`topos/single_rt.topo`

```bash
java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
```

`topos/pair_rt.topo`

```bash
java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -r rtable.r2 -a arp_cache
```

`topos/triangle_rt.topo`

```bash
java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -r rtable.r2 -a arp_cache
java -jar VirtualNetwork.jar -v r3 -r rtable.r3 -a arp_cache
```

`topos/linear5_rt.topo`

```bash
java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -r rtable.r2 -a arp_cache
java -jar VirtualNetwork.jar -v r3 -r rtable.r3 -a arp_cache
java -jar VirtualNetwork.jar -v r4 -r rtable.r4 -a arp_cache
java -jar VirtualNetwork.jar -v r5 -r rtable.r5 -a arp_cache
```

### Combined topologies

`topos/single_each.topo`

```bash
java -jar VirtualNetwork.jar -v s1
java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
```

`topos/triangle_with_sw.topo`

```bash
java -jar VirtualNetwork.jar -v s1
java -jar VirtualNetwork.jar -v s2
java -jar VirtualNetwork.jar -v s3
java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
java -jar VirtualNetwork.jar -v r2 -r rtable.r2 -a arp_cache
java -jar VirtualNetwork.jar -v r3 -r rtable.r3 -a arp_cache
```

## 5) Cleanup / Reset Between Test Cases

Run this before starting the next topology.

### In Mininet terminal

```bash
exit
```

### Stop VirtualNetwork processes

```bash
cd ~/Projects/assign2
./run_devices.sh stop
```

(Equivalent manual kill)

```bash
pkill -f 'VirtualNetwork.jar -v' || true
```

### Optional: stop POX

```bash
pkill -f pox.py || true
```

### Clean Mininet state

```bash
sudo mn -c
```

After cleanup, start the next test case from Section 3 Step 1.
