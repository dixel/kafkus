# kafkus
*backdoor key to kafka topics*

[![CircleCI](https://circleci.com/gh/dixel/kafkus.svg?style=svg)](https://circleci.com/gh/dixel/kafkus)
[![Built with Spacemacs](https://cdn.rawgit.com/syl20bnr/spacemacs/442d025779da2f62fc86c2082703697714db6514/assets/spacemacs-badge.svg)](http://spacemacs.org)

![](./pic/badge-1.png) ![](./pic/badge-2.png)

![](./pic/screenshot.jpg)

## Goal
Provide a minimalistic way to inspect, what kind of data is available in a certain Kafka topic.

## Features
- Check contents of a Kafka topic with a functionality similar to `kafkacat`, `kafka-avro-console-consumer` and `kafka-console-consumer`
- 3 modes:
    - confluent schema registry (needs valid schema-registry-url) - handles the schema change on the flight
    - raw text format
    - raw avro schemas uploaded to the server folder (configured with `AVRO_SCHEMAS_PATH` env variable).
- Rate limiting (server-side, so that the load on Kafka is also limited).

## Running Kafkus

You can configure Kafkus with some defaults and run it using docker, or provide the configuration at runtime in the UI.

```bash
docker run -p 4040:4040 -v $PWD/schemas-repository:/tmp \
    -e LOG_LEVEL=debug \
    -e AVRO_SCHEMAS_PATH=/tmp \
    -e LOAD_DEFAULT_CONFIG: "true" \
    -e DEFAULT_BOOTSTRAP_SERVER: localhost:9092 \
    -e DEFAULT_SCHEMA_REGISTRY_URL: "http://localhost:8081" \
    -e DEFAULT_MODE: avro-schema-registry \
    -e DEFAULT_AUTO_OFFSET_RESET: earliest \
    -e DEFAULT_RATE: 1 \
    -e DEFAULT_LIMIT: 1000 \
    -ti dixel/kafkus
```

Kafkus tries to load the topics from server once the `boostrap.servers` field gets unfocused or the `topic` dropdown menu gets opened.
In the browser go to http://localhost:4040/, select the topic, adjust the configuration and press "Play" to start consuming the data.

## Configuration
Numerous configuration options are available, you can inspect them in the logs of Kafkus during the startup.

## Implementation
The design goal was to make the application as composable as possible.
Clojure/Clojurescript is used as the main language to simplify the process of working with custom schemas.
For client <-> server communication, amazing [sente](https://github.com/ptaoussanis/sente) is used in `ajax` mode (places where I use Kafkus
have bad-behaving proxies).
For stateful components management, [mount](https://github.com/tolitius/mount) is used.

## Roadmap
- Better process/error communication (connected/failed to connect, etc...)
- Generating and producing the messages, validating the schema (carefully looking into [lancaster](https://github.com/deercreeklabs/lancaster) library)
- Improving the UX and giving the frontend part a bit more love (input welcome)
- Supporting other ser/de formats (protobuf, thrift)

## Motivation
- https://github.com/edenhill/kafkacat/issues/119
- clojure REPL was just fine for me most of the time, but it's not for everyone (check [samples](./dev/user.clj))
- POC for not using uber-frameworks for similar projects

## License

Copyright © 2018 Avdiushkin Vasilii

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
