# Discovery v5 simulations

[Simulator](blob/master/src/main/kotlin/org/ethereum/discv5/Simulator.kt) for different Kademlia peer neighborhood lookups: 
- V5 `Node.findNodesStrict`
- V4/Kademlia  `Node.findNeighbors` 
- Experimental `Node.findNodesDown`

Setup creates network of p2p nodes (without real network) with knowledge about other nodes according to configured Pareto distribution. Runs simulated lookups and measures traffic and estimated latency by logging each message size.  
