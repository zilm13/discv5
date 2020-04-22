# Discovery v5 simulations

[Simulator](src/main/kotlin/org/ethereum/discv5/Simulator.kt) for different advertisement options in Discovery V5 
- ENR attributes
- Topic advertisements

Setup creates network of p2p nodes (without real network) with knowledge about other nodes according to configured Pareto distribution. Runs simulated advertisement workflow and measures traffic and estimated latency, delays by logging each message size and queueing tasks.  
