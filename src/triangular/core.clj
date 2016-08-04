(ns triangular.core
  "Triangular - Recurring event library"
  (:require [clj-time.core :as t]
            [clj-time.periodic :as p]))

;; Utility functions
(def ^:private conj-multiple (comp flatten conj))

(defn- dissoc-if
  "Dissociate a map key if predicate for that key is true"
  [pred map key]
  (if (pred (get map key)) (dissoc map key) map))

(def ^:private dissoc-if-empty (partial dissoc-if empty?))
(def ^:private dissoc-if-nil (partial dissoc-if nil?))

(defn- group-participants-by-condition
  "Given a map of conditional participants, group them by their conditions with the participant's name as the key and the condition value as they value"
  ([conditional-participants] (group-participants-by-condition
                                conditional-participants nil))
  ([conditional-participants condition]
   (let [conditions
           (reduce (fn [m [name conditions]]
                       (let [condition-key (first (keys conditions))
                             condition-val (first (vals conditions))]
                         (if (or (not condition) (= condition condition-key))
                           (merge m {condition-key
                                     (merge (get m condition-key {})
                                            {condition-val
                                              (conj (get-in m
                                                            [condition-key
                                                             condition-val]
                                                            [])
                                                    name)})})
                           m)))
                   {}
                   conditional-participants)]
        (if condition (get conditions condition) conditions))))

(defn- remove-empty-participants'-lists
  ([event] (-> event
               (remove-empty-participants'-lists :participants)
               (remove-empty-participants'-lists :conditional-participants)))
  ([event key] (dissoc-if-empty event key)))

;; Library
(defn add-participant
  "Add a participant to an event"
  [event name]
  (if (map? name) (assoc-in event [:conditional-participants
                                   (first (keys name))]
                                  (first (vals name)))
                  (assoc-in event [:participants]
                                  (conj (or (:participants event) #{})
                                        name))))

(defn determine-participants
  "Determine who amongst the event's participants and conditional participants will be joining"
  [event]
  (first
    (reduce (fn [[confirmed unconfirmed] [condition conditioners]]
                (let [all (set (filter identity (conj-multiple
                                                  (vec confirmed)
                                                  (vals unconfirmed)
                                                  conditioners)))]
                 (if (<= condition (count all))
                     [all {}]
                     [confirmed (assoc unconfirmed condition conditioners)])))
            [(:participants event) {}]
            (sort (group-participants-by-condition
                    (:conditional-participants event) :min-participants)))))

(defn has-started?
  "Check whether an event has started"
  [event]
  (t/after? (t/now) (:start event)))

(defn recurring-event
  "Create a pattern for an event that repeats.

  :start-time and :end-time are sequences of any of the clj-time PeriodTypes: `hours`, `minutes`, `seconds`, `millis`.

  If :start-date is omitted, the UNIX epoch (1 January 1970) will be used as the outset.

  Omitting :end-date results in an infinite sequence. (Triangular theoretically supports dates up to the year 292278994.)

  :weekdays is a sequence of weekday numbers (1 for Monday through 7 for Sunday) for which the event will repeat on. Omitting it will repeat the event on every day of the week."
  [& {:keys [name start-time end-time start-date end-date weekdays
             min-participants]}]
  (let [start-date (if start-date (t/floor start-date t/day) (t/epoch))
        weekdays (if (seq weekdays) weekdays '(1 2 3 4 5 6 7))
        sequence (if end-date (p/periodic-seq start-date end-date (t/days 1))
                              (p/periodic-seq start-date (t/days 1)))]
       (for [date sequence :when (some #{(t/day-of-week date)} weekdays)]
         (dissoc-if-nil {:name name
                         :start (reduce t/plus date start-time)
                         :end (reduce t/plus date end-time)
                         :min-participants min-participants}
                        :min-participants))))

(defn remove-participant
  "Remove a participant who's already on the list. Removes empty participants' lists"
  [event participant]
  (remove-empty-participants'-lists
    (assoc event :participants (set (remove #{participant}
                                            (:participants event)))
                 :conditional-participants (dissoc (:conditional-participants
                                                    event)
                                                   participant))))

(defn will-go-through?
  "Check whether an event lives up its conditions to go through"
  [event]
  (<= (or (:min-participants event) 0)
      (count (determine-participants event))))
