# kafkus
*backdoor key to kafka topics*
[![CircleCI](https://circleci.com/gh/dixel/kafkus.svg?style=svg)](https://circleci.com/gh/dixel/kafkus)
[![Built with Spacemacs](https://cdn.rawgit.com/syl20bnr/spacemacs/442d025779da2f62fc86c2082703697714db6514/assets/spacemacs-badge.svg)](http://spacemacs.org)

![](./pic/screenshot.png)

## Running kafkus

```bash
lein uberjar
docker-compose up -d
```

or

```bash
docker run -p 4040:4040 -v $PWD:/tmp -e LOG_LEVEL=debug -e AVRO_SCHEMAS_PATH=/tmp -ti dixel/kafkus
```

## License

Copyright © 2018 Avdiushkin Vasilii

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
