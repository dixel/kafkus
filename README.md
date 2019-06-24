# kafkus
*backdoor key to kafka topics*
![](./pic/screenshot.png)

## Running kafkus

```bash
lein uberjar
docker-compose up -d
```

or

```bash
docker run -p 4040:4040 -v $PWD:/tmp -e LOG_LEVEL=debug -e AVRO_SCHEMAS_PATH=/tmp -ti dixel/kafkus:0.1.0-SNAPSHOT.5
```

## License

Copyright © 2018 Avdiushkin Vasilii

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
