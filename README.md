# Simple search service example

Scalable HTTP API that optimize for high throughput and low latency searching in data.json by word (or combination of words).
The search complexity is independent from the growth of the data set


# Implementation details

A DB initialisation will pipeline the following Redis commands:
```
SET(rid_date, message) -> Add the entry (review_id, date, message): O(1)
SADD(word, rid_date) -> Add (review_id, date) to the `word` set: O(1)
```

A search (GET request) will pipeline the following Redis commands:
```
SINTER(word1, ..., wordN) -> Get intersection between the sets of each word searched: O(N*M) worst case where N is the cardinality of the smallest set and M is the number of sets.
MGET(rid_date1, ..., rid_dateN) -> Get messages where words searched appear: O(1)
```

## Concurrency notes
Data will be eventually consistent. To improve things transaction could be add between REDIS commands. 

## Scalability
This solution can scale horizontally, multiple service can work together behind a load balancer with their remote Redis clusters.
It could also be possible to have a specialized service inserting data and one just retrieving it (searching).
 

## Getting Started

Launch the service using:
    ```
    make run
    ```
The service will run with default configurations (on port 8888). 

To stop the service 
```
    make stop    
```

### Prerequisites

A running Redis instance/cluster

### Installing

Redis host and port can be configured on conf.json.

## Testing the service

It's possible to test the service with `make loadtest` which will do: 

```
    > xargs curl -s < queries.txt > /dev/null
```

or with wrk (runs as many reqs as possible for 1m)
```
    > wrk -t4 -c1000 -d1m http://localhost:8888/s/guide%20super
```


## Built With

* [Vert.x](http://vertx.io/)
* [Redis](http://redis.io)
* [Maven](https://maven.apache.org/)
