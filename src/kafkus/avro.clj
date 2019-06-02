(ns kafkus.avro
  (:require [deercreeklabs.lancaster :as avro]))

(defn type-parser [parse-fields-fun f]
  (let [field-type (:type f)
        name (:name f)
        ftype (cond
                (vector? field-type) (first field-type)
                (map? field-type) (:type field-type)
                :else field-type)
        f (merge f (when (map? field-type) field-type))]
    [name (case ftype
            :string "test"
            :long 10
            :int 10
            :float 1.0
            :double 1.0
            :record (parse-fields-fun (:fields f))
            :array []
            :enum (first (:symbols f))
            :map {}
            nil)]))

(defn parse-fields [fields]
  (into {} (map
            #(type-parser parse-fields %)
            fields)))

(defn sample-data
  "Provide sample data for an avro schema in json format"
  [schema]
  (parse-fields
   (-> schema
       avro/json->schema
       :edn-schema
       :fields)))

(def sample-schema
  "{
   \"type\" : \"record\",
   \"namespace\" : \"Tutorialspoint\",
   \"name\" : \"Employee\",
   \"fields\" : [
      { \"name\" : \"Name\" , \"type\" : \"string\" },
      { \"name\" : \"Age\" , \"type\" : \"int\" }
   ]
  }"
  )

(def complex-schema
  "
  {
  \"name\": \"myRecord\",
  \"type\": \"record\",
  \"fields\": [
    {\"name\":\"name_tag\", \"type\":\"string\",\"default\": null},
    {
  \"name\": \"known_nested_structure\",
  \"type\": {
        \"name\": \"known_nested_structure\",
        \"type\": \"record\",
        \"fields\": [
                {\"name\":\"fieldA\", \"type\":{\"type\":\"array\",\"items\":\"string\"},\"default\":null},
                {\"name\":\"fieldB\", \"type\":{\"type\":\"array\",\"items\":\"string\"},\"default\":null},
                {\"name\":\"fieldC\", \"type\":{\"type\":\"array\",\"items\":\"string\"},\"default\":null},
                {\"name\":\"fieldD\", \"type\":{\"type\":\"array\",\"items\":\"string\"},\"default\":null}
              ],
              \"default\":null

       }
    },
        {\"name\": \"another_field\",\"type\": \"string\",\"default\": null}
  ]
  }
  ")


