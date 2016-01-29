organization  := "one.gzero"

version       := "0.0.1"

scalaVersion  := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

val akkaV = "2.3.9"
val sprayV = "1.3.3"
val akkaStreamV = "2.0.1"
val titanV = "1.1.0-SNAPSHOT"
val gremlinV = "3.1.0-incubating"
val gremlinScalaV = "3.1.0-incubating"

libraryDependencies ++= Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing-shapeless2" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "com.typesafe.akka" %% "akka-http-core-experimental"          % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental"               % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental"    % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-experimental"       % akkaStreamV,
    "com.michaelpollmeier" %% "gremlin-scala" % gremlinScalaV,
    "com.thinkaurelius.titan" % "titan-core" % titanV,
    "com.thinkaurelius.titan" % "titan-cassandra" % titanV,
    "com.thinkaurelius.titan" % "titan-es" % titanV,
//    "com.thinkaurelius.titan" % "titan-berkeleyje" % titanV,
//    "com.thinkaurelius.titan" % "titan-lucene" % titanV,
//    "com.thinkaurelius.titan" % "titan-hbase" % titanV,
//    "com.thinkaurelius.titan" % "titan-solr" % titanV,
    "org.apache.tinkerpop" % "tinkergraph-gremlin" % gremlinV,
    "org.apache.tinkerpop" % "gremlin-driver" % gremlinV,
    "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test"
)

resolvers += "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository"


Revolver.settings