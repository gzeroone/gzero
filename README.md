# gzero
GZero simplifies large scale graph-based computing, storage, and machine learning model predictions.

## Getting Started

#### Titan 1.1
Clone [titan](https://github.com/thinkaurelius/titan/tree/titan11) branch `titan11` and run:  
```mvn clean install -DskipTests=true -Paurelius-release -Dgpg.skip=true```

#### Cassandra & Elastic Search
Titan 1.1 comes with cassandra and elastic search. Start cassandra and enable thrift then start elastic search:

```Shell
titan/bin/cassandra -f  
titan/bin/nodetool enablethrift  
titan/bin/elasticsearch
```

#### Gremlin Server

Ensure config in `titan/conf/gremlin-server.yaml` is using the `HttpChannelizer` to enable the REST API. You'll also want to make sure the graph properties include cassandra and elastic search. You can find an example in [conf/gremlin-server.yaml](conf/gremlin-server.yaml)

Now, start the server: `bin/gremlin-server.sh`

#### Running GZero API
Run [Boot.scala](src/main/scala/one/gzero/Boot.scala) to start the API on port 8080.

## API

### POST `/edge`
Create an edge. Will create verticies if they do not exist.

Request:
```Shell
curl \
	-H 'Content-Type: application/json' \
	-X POST localhost:8080/edge \
	-d '{"label":"drove", "head":{"label":"person","name":"Bonnie"},"tail":{"label":"vehicle","name":"1932 Ford V-8 B-400 convertible sedan"}}'
```

### POST `/vertex`
Create a vertex.

Request:
```Shell
curl \
	-H 'Content-Type: application/json' \
	-X POST localhost:8080/vertex \
	-d '{"label":"person","name":"Clyde"}'
```


### GET `/query`
Run a gremlin query against the graph.

Request:
```Shell
curl \
	-H 'Content-Type: application/json' \
	-X GET localhost:8080/query \
	-d '{"gremlin":"g.V().has(\"name\", \"Bonnie\")"}'
```
