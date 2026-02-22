# CS640 Assignment 2 – Script Summary + Test Instructions

## What each script/file does

- `config.sh`
  - Runs POX module setup:
  - `cd pox_module && sudo python setup.py develop`
  - Do this once per fresh VM/setup.

- `run_mininet.py <topo> [-a]`
  - Starts Mininet with your chosen topology.
  - Auto-generates:
    - `ip_config`
    - `arp_cache`
    - `rtable.<router>` files for router topologies
  - Starts host HTTP servers.
  - `-a` preloads static ARP entries into hosts.

- `run_pox.sh`
  - Starts POX with the assignment handlers:
  - `cs640.ofhandler` and `cs640.vnethandler`

- `build.xml`
  - Ant build file:
  - `ant` compiles Java and builds `VirtualNetwork.jar`
  - `ant clean` removes `bin/` and jar.

- `VirtualNetwork.jar`
  - Your switch/router executable.
  - Run one instance per virtual device (e.g., `s1`, `r1`, etc.).

---

## What I implemented

- `src/edu/wisc/cs/sdn/vnet/sw/Switch.java`
  - Learning switch (source MAC learning, known unicast forwarding, flooding unknown/broadcast/multicast, same-port drop, MAC aging timeout).

- `src/edu/wisc/cs/sdn/vnet/rt/RouteTable.java`
  - Longest-prefix-match route lookup.

- `src/edu/wisc/cs/sdn/vnet/rt/Router.java`
  - IPv4 forwarding pipeline:
    - ARP request reply for router IPs
    - IPv4 checksum validation
    - TTL decrement + drop/send ICMP time exceeded
    - LPM route lookup + forwarding via ARP cache
    - ICMP echo reply when pinging router interface
    - ICMP destination unreachable (net/port) where applicable

---

## Prereqs

On the Mininet VM (or equivalent assignment VM), ensure:
- Python 2.7 available (for provided scripts)
- Mininet + POX installed (as expected by course VM)
- Java + Ant installed

---

## Standard run flow (3 terminals)

### Terminal 1: Mininet
```bash
cd ~/assign2
sudo ./run_mininet.py topos/single_sw.topo -a
```
Leave this open at the `mininet>` prompt.

### Terminal 2: POX
```bash
cd ~/assign2
./run_pox.sh
```
Wait until switch/router connects to POX.

### Terminal 3: Build + device process
```bash
cd ~/assign2
ant
java -jar VirtualNetwork.jar -v s1
```
For router topologies, run with router name instead (example: `-v r1`).
For multi-device topologies, run one process per device.

---

## Quick tests

## A) Switch (single switch topology)
Use `topos/single_sw.topo`.

At Mininet prompt:
```bash
h1 ping -c 2 10.0.1.102
h1 ping -c 2 10.0.1.103
h2 curl -s 10.0.1.101 | head
```
Expected: pings and HTTP fetch succeed after MAC learning.

## B) Router basics
Use `topos/single_rt.topo` (or whichever your course expects first).

Run router process in Terminal 3:
```bash
java -jar VirtualNetwork.jar -v r1
```
Then in Mininet:
```bash
h1 ping -c 2 <host-in-other-subnet>
h1 traceroute -n <host-in-other-subnet>
```
Expected:
- Inter-subnet forwarding works
- TTL exhaustion paths produce ICMP time exceeded
- Pings to router interface IPs get ICMP echo replies

## C) Unreachable behavior
Try a destination with no route / blocked port scenario per your topology.
Expected ICMP unreachable responses from router where required.

---

## Troubleshooting

- If POX and Mininet don’t connect:
  - Restart both in clean order (often POX first, then Mininet).
- If stale processes interfere:
  - rerun and let scripts kill stale HTTP servers; or reboot VM if messy.
- If jar didn’t rebuild:
  - `ant clean && ant`
- If forwarding fails unexpectedly:
  - verify generated `rtable.*` and `arp_cache` files in assignment directory.

---

## Repo tip

If you copy to VM from GitHub, keep path as `~/assign2` (matching script assumptions and class instructions).
