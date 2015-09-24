(ns fractalify.utils
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require
    [schema.core :as s :include-macros true]
    [instar.core :as i]
    [clojure.string :as str]
    [clojure.walk :as w]
    [clojure.test.check.generators :as gen]
    [schema.coerce :as coerce]
    #?@(:clj  [
    [environ.core :refer [env]]
    [clojure.core.async :refer [go go-loop chan close! >! <!]]
    [clj-time.core :as m]
    [clojurewerkz.scrypt.core :as sc]
    [clojure.pprint :refer [pprint]]]
        :cljs [[cljs.pprint :refer [pprint]]
               [cljs.core.async :refer [chan close! >! <!]]
               [cljs-time.core :as m]
               [reagent.core :as r]
               [re-frame.core :as f]])))

(defn ensure-vec [x]
  (if (vector? x) x [x]))

(defn p [& args]
  "Like print, but returns last arg. For debugging purposes"
  (doseq [a args]
    (let [f (if (map? a) pprint print)]
      (f a)))
  (println)
  (last args))

(defn pk [msg ks x]
  (p msg (get-in x (ensure-vec ks)))
  x)

(defn pk-> [x msg ks]
  (pk msg ks x))

(defn concat-vec
  [& args]
  (vec (apply concat args)))

(do
  #?@(:cljs
      [(defn mlog [& messages]
         (.apply (.-log js/console) js/console (clj->js messages)))

       (defn mwarn [& messages]
         (.apply (.-warn js/console) js/console (clj->js messages)))

       (defn merror [& messages]
         (.apply (.-error js/console) js/console (clj->js messages)))

       (defn e-val [event]
         (aget event "target" "value"))

       (s/defn set-timeout
         [f
          ms :- s/Int]
         (js/setTimeout f ms))

       (def clear-timeout js/clearTimeout)

       (defn timeout [ms]
         (let [c (chan)]
           (js/setTimeout (fn [] (close! c)) ms)
           c))

       (defn debounce-ch
         ([c ms] (debounce-ch (chan) c ms false))
         ([c ms instant] (debounce-ch (chan) c ms instant))
         ([c' c ms instant]
          (go
            (loop [start (js/Date.) timeout nil]
              (let [loc (<! c)]
                (when timeout
                  (js/clearTimeout timeout))
                (let [diff (- (js/Date.) start)
                      delay (if (and instant
                                     (or (>= diff ms)
                                         (not timeout)))
                              0 ms)
                      t (js/setTimeout #(go (>! c' loc)) delay)]
                  (recur (js/Date.) t)))))
          c'))

       (defn debounce
         ([f ms] (debounce f ms false))
         ([f ms instant]
          (let [change-ch (chan)
                debounced-chan (debounce-ch change-ch ms instant)]
            (go-loop []
                     (let [args (<! debounced-chan)]
                       (apply f args)
                       (recur)))
            (fn [& args]
              (go (>! change-ch args))))))

       (defn throttle [c ms]
         (let [c' (chan)]
           (go
             (while true
               (>! c' (<! c))
               (<! (timeout ms))))
           c'))


       (s/defn parse-float :- (s/maybe s/Num)
         [num :- (s/cond-pre s/Num s/Str)]
         (js/parseFloat num))

       (defn ceil [num]
         (.ceil js/Math num))

       (defn with-default-props [form default-props other-args]
         (let [[params props] (if (map? (last other-args))
                                [(butlast other-args) (last other-args)]
                                [other-args {}])]
           (concat-vec [form] params [(r/merge-props default-props props)])))

       (s/defn create-dispatch [key :- s/Keyword]
         #(f/dispatch (into [key] %&)))]))

(do
  #?@(:clj [(def is-dev? (env :is-dev?))

            (defn eval-map [m]
              (w/prewalk #(if (list? %) (eval %) %) m))

            (defn s->int [s]
              (if (number? s)
                s
                (Integer/parseInt (re-find #"\A-?\d+" s))))

            (defn hash-pass [password salt]
              (sc/encrypt (str salt password) 16384 8 1))]))

(defn range-count [coll]
  (range 0 (count coll)))

(def select-values (comp vals select-keys))

(defn ellipsis [s max-chars]
  (str (subs s 0 max-chars) "..."))

(defn select-key [map k]
  (select-keys map [k]))

(defn empty-seq? [x]
  (and (empty? x) (sequential? x)))



(s/defn gen-sentence :- s/Str
  [word-size words-min words-max]
  (gen/generate
    (gen/fmap
      (partial str/join " ")
      (gen/vector (gen/resize word-size gen/string-alphanumeric) words-min words-max))))

(s/defn gen-email []
  (str "some" (rand-int 9999) "@email.com"))


(defn gen-str [n]
  (let [charseq (map char (concat
                            (range 48 58)                   ; 0-9
                            (range 97 123)))]               ; 0-z
    (apply str
           (take n
                 (repeatedly #(rand-nth charseq))))))

(defn create-str-coercer [schema]
  (coerce/coercer schema coerce/string-coercion-matcher))

(s/defn validate-until-error-fn
  ([fns] (validate-until-error-fn nil fns))
  ([val :- s/Any
    fns :- [s/Any]]
    (when-let [f (first fns)]
      (if-let [error (f val)]
        error
        #(validate-until-error-fn val (rest fns))))))

(def validate-until-error
  "This is trampoline function, which stops executing until one of fns returns truthy value
  when val is passed to that function. It is used primary for getting error messages for inputs.
  Example:
  (do-until nil [reqired alphanumeric])
  => This field is required
  (do-until \"_*_\" [reqired alphanumeric])
  => This field needs to be alphanumeric"
  (partial trampoline validate-until-error-fn))

(defn drop-nth [n coll]
  (lazy-seq
    (when-let [s (seq coll)]
      (concat (take (dec n) s) (drop-nth n (drop n s))))))

(defn partial-right
  "Takes a function f and fewer than the normal arguments to f, and
 returns a fn that takes a variable number of additional args. When
 called, the returned function calls f with additional args + args."
  ([f] f)
  ([f arg1]
   (fn [& args] (apply f (concat args [arg1]))))
  ([f arg1 arg2]
   (fn [& args] (apply f (concat args [arg1 arg2]))))
  ([f arg1 arg2 arg3]
   (fn [& args] (apply f (concat args [arg1 arg2 arg3]))))
  ([f arg1 arg2 arg3 & more]
   (fn [& args] (apply f (concat args (concat [arg1 arg2 arg3] more))))))

(def trim-base64-prefix (partial-right str/replace-first #"^data:image/\w+;base64," ""))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn remove-first [pred coll]
  (let [pred (if (map? pred)
               #(= ((first (keys pred)) %)
                   (first (vals pred)))
               pred)
        n (take-while (complement pred) coll)]
    (concat n (take-last (- (count coll) (inc (count n))) coll))))

(defn remove-first-in [m ks pred]
  (update-in m ks #(remove-first pred %)))

(defn time-ago [time]
  (let [units [{:name "second" :limit 60 :in-second 1}
               {:name "minute" :limit 3600 :in-second 60}
               {:name "hour" :limit 86400 :in-second 3600}
               {:name "day" :limit 604800 :in-second 86400}
               {:name "week" :limit 2629743 :in-second 604800}
               {:name "month" :limit 31556926 :in-second 2629743}
               {:name "year" :limit nil :in-second 31556926}]
        diff (m/in-seconds (m/interval time (m/now)))]
    (if (< diff 5)
      "just now"
      (let [unit (first (drop-while #(or (>= diff (:limit %))
                                         (not (:limit %)))
                                    units))]
        (-> (/ diff (:in-second unit))
            int
            (#(str % " " (:name unit) (when (> % 1) "s") " ago")))))))

