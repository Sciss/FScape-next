# Akka Streams

- http://blog.lancearlaus.com/akka/streams/scala/2015/05/27/Akka-Streams-Balancing-Buffer/
- http://doc.akka.io/docs/akka/2.4.4/scala/stream/stream-parallelism.html
- http://doc.akka.io/docs/akka/2.4.4/scala/stream/stream-rate.html#stream-rate-scala
- ! https://softwaremill.com/comparing-akka-stream-scalaz-stream/
- http://doc.akka.io/docs/akka/2.4.4/scala/stream/stream-customize.html
- https://medium.com/@kvnwbbr/diving-into-akka-streams-2770b3aeabb0

Using just `Double` (un-blocked) as signals, the disk-in > fft > disk-out example
takes 2 min 55 sec. Versus the "pure" variant without Akka Stream taking 4 sec !
So this is completely inacceptable.

# Spark Streaming

- https://spark.apache.org/streaming/
- https://spark.apache.org/docs/latest/streaming-programming-guide.html

# Other

- https://github.com/runarorama/scala-machines
- https://ci.apache.org/projects/flink/flink-docs-master/apis/streaming/index.html
- https://softwaremill.com/comparing-akka-stream-scalaz-stream/
