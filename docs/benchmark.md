# Exploratory matcher benchmark

Run on 10 September 2026, Windows, AMD Ryzen 9 9950X3D, Java 21.0.9, JMH 1.37. Docker build activity was also present: this is a local exploratory baseline, not an isolated performance certification.

Command (PowerShell or POSIX shell, using the appropriate wrapper):

```sh
./mvnw -Pbenchmark compile exec:exec "-Dexec.args=-classpath %classpath org.openjdk.jmh.Main MatcherBenchmark.sanitize -p vocabularySize=228,2000 -p messageLength=1024 -p density=none,dense -wi 2 -i 3 -w 1s -r 1s -f 1 -prof gc"
```

One fork, two one-second warmups, three one-second measurements. Messages contain 1,024 UTF-16 units. Vocabulary uses deterministic synthetic terms; this isolates vocabulary size rather than claiming the exact SQL list has identical performance.

| Vocabulary | Match density | Average μs/op | JMH score error (99.9%) | Allocated bytes/op |
|---|---|---:|---:|---:|
| 228 | None | 37.25 | ±37.00 | 224 |
| 2,000 | None | 256.97 | ±171.00 | 226 |
| 228 | Dense | 7.19 | ±8.20 | 2,352 |
| 2,000 | Dense | 7.28 | ±9.58 | 2,352 |

The wide error intervals prevent precise latency claims. The no-match case suggests vocabulary growth is an important load-test dimension: the regex must reject alternatives while scanning. Dense messages repeatedly match an early alternative, so these are deliberately different workloads.

The matcher avoids an output builder when no match exists, but still allocates per-request regex state. Folding mixed/upper-case Unicode text can allocate another string. These synthetic lower-case inputs do not measure that extra cost. Allocation results therefore do not imply an allocation-free service.

Use the full profile (two forks, three warmups, five measurements), include representative Unicode and the actual vocabulary, and run on isolated hardware before selecting algorithms. HTTP throughput, tail latency, concurrent refresh, database contention, security processing and container resource limits require a separate load test.

No requests-per-second target or production SLO is inferred from these results.
