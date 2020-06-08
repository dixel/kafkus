(ns kafkus.info)

(def info
  [:div.container
   [:div.row.p-2
    [:i "backdoor key to kafka topics"]]
   [:div.row.p-2
    [:div.col-2.align-self.center
     [:img.img-fluid
      {:src "./pic/kafkus-small.png"}]]
    [:div.col-2.align-self-center
     [:img.img-fluid
      {:src "./pic/clojure.png"}]]
    [:div.col.align-self-center
     [:h2 "Single message transformation"]]]
   [:div.row.p-2
    [:p "This tool is written in Clojure, which allows a lot of flexibility when it comes to data manipulation. "]
    [:p "Clojure is not hard! " [:a {:href "https://www.clojure.org/guides/learn/syntax"} "5 pages here"] " (Syntax, Functions, Sequential Collections, Hashed Collections, Flow Control) are more than enough to get you started."]
    [:p "Willing to share " [:a {:href "https://www.clojure.org/guides/repl/basic_usage"} "The Repl"] " experience, and to use an amazing "
     [:a {:href "https://github.com/borkdude/sci"} "SCI library"] " (small clojure interpreter), here's what can be done with "
     "single messages that are coming from your Kafka instance."]
    [:ul
     [:li "First things first - safety net. Whenever there is a syntax error in the statement you've written - no worries, it just generates an " [:i.text-danger "error message"] " followed by complete payload of each Kafka message. Just make sure to stop consuming in case you experience too many difficulties."]
     [:li "Pretty much anything available in Clojure(Script) standart library is available here"
      [:p [:code "1"]
       ", "
       [:code "[1 2 3 4]"]
       ", "
       [:code "{:name \"test\" :value \"something}"]
       ", "
       [:code "(+ 10 2)"]
       ", - are all valid statements"]]
     [:li "Payload from kafka topic is bounded to the following symbols"
      [:ul
       [:li [:code "value"] " - deserialized kafka message"]
       [:li [:code "key"] " - key for the message as string"]
       [:li [:code "i"] " - complete payload, containing all the known parameters of the message"]]]
     [:li "Data transformations"
      [:ul
       [:li [:code "(get value :phoneNumber)"] " - get the field called \"phoneNumber\" from the payload"]
       [:li [:code "(-> value :customers first :name)"] " - getting the name of the first customer in a list of customers"
        [:a {:href "https://clojure.org/guides/threading_macros"} " (threading macros)"]]
       [:li [:code "(map :name (-> value :customers))"] " - getting names of all the customers in a message"]]]
     [:li "Variables, bindings"
      [:pre.border.rounded.bg-dark.text-white
       "(def customers-count
  (-> value :customers count))
(assoc value :customers-count customers-count)"]
      [:pre.border.rounded.bg-dark.text-white
       "(let [customers-count (-> value :customers count)]
  (assoc value :customers-count customers-count))"]
      [:p "Both snippets doing pretty much the same, adding number of customers to the payload"]]]]
   [:div.row.p-2
    [:h2 "Feedback"]]
   [:div.row.p-2
    [:p "Please, report any feedback, issues, suggestions on " [:a {:href "https://github.com/dixel/kafkus"} "GitHub"]]]])
