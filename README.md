# kafkus
*because we need another tool for kafka*

## Configuration
Configuration is generally done via environment variables.
For local development and REPL, `.config.edn` file can be used.
The following variables define the configuration of the application

```
"HTTP_PORT" 4040
"HTTP_HOST" "127.0.0.1"
```

## Running kafkus
```bash
lein repl
```

```clojure
(start) ;; This project uses mount as a state manager. Mount enables reloading the components during the development.

; by default, the http://localhost:4040/ping is available

```

By default, database methods are defined in [](./resources/queries/example.sql) and loaded into the driver adapters namespaces.

```clojure
```

The endpoint that targets this database method is exposed here:
```bash
curl http://localhost:4040/sample?name=test&age=25&date=2018-11-11
```


## License
