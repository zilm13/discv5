# Discovery v5 simulations

[Simulator](src/main/kotlin/org/ethereum/discv5/Simulator.kt) for different advertisement options in Discovery V5 
- ENR attributes
- Topic advertisements

Setup creates a network of p2p nodes (without real network) with knowledge about other nodes according to configured Pareto distribution. Runs simulated advertisement workflow by steps, where step is single leg network message delivery. So 2 steps is round-trip, PING-PONG, for example.

Different configurations are made to exercise a number of metrics. By comparing the number of steps spent for each goal, the simulator measures efficiency of each approach. Traffic measurements give an idea on bandwidth system requirements. Plus, seed randomization helps to configure identical setups to have apple to apple comparison. 

Write-up with results: to be published soon