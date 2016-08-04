(ns triangular.core-test
  (:require [clojure.test :refer :all]
            [triangular.core :refer :all]
            [clj-time.core :as t]))

(deftest test-add-participant
  (testing "Adding the first participant to an event"
    (is (= (add-participant {:name "My Event"
                             :start (t/date-time 2016 7 22 12)
                             :end (t/date-time 2016 7 22 20)}
                            "John Doe")
           {:name "My Event"
            :start (t/date-time 2016 7 22 12)
            :end (t/date-time 2016 7 22 20)
            :participants #{"John Doe"}})))
  (testing "Adding a participant to an event that already has participants"
    (is (= (add-participant {:name "My Event"
                             :start (t/date-time 2016 7 22 12)
                             :end (t/date-time 2016 7 22 20)
                             :participants #{"John Doe"}}
                            "Jane Doe")
           {:name "My Event"
            :start (t/date-time 2016 7 22 12)
            :end (t/date-time 2016 7 22 20)
            :participants #{"John Doe" "Jane Doe"}})))
  (testing "Adding a participant with conditions"
    (is (= (add-participant {:name "My Event"
                             :start (t/date-time 2016 7 22 12)
                             :end (t/date-time 2016 7 22 20)}
                            {"John Doe" {:min-participants 8}})
           {:name "My Event"
            :start (t/date-time 2016 7 22 12)
            :end (t/date-time 2016 7 22 20)
            :conditional-participants {"John Doe" {:min-participants 8}}}))))

(def group-participants-by-condition
  @#'triangular.core/group-participants-by-condition)

(deftest test-group-participants-by-condition
  (testing "Making maps of people with conditions, grouped by condition"
    (is (= (group-participants-by-condition {"Jillian Doe"
                                              {:min-participants 8}
                                             "Julius Doe"
                                              {:min-participants 8}
                                             "Jennifer Doe"
                                              {:min-participants 7}})
           {:min-participants
            {7 ["Jennifer Doe"] 8 ["Jillian Doe" "Julius Doe"]}})))
  (testing "Making maps of people with min-participants conditions, grouped
           by min-participants conditions"
    (is (= (group-participants-by-condition {"Jillian Doe"
                                              {:min-participants 8}
                                             "Julius Doe"
                                              {:min-participants 9}
                                             "Jennifer Doe"
                                              {:min-participants 7}}
                                            :min-participants)
           {7 ["Jennifer Doe"] 8 ["Jillian Doe"] 9 ["Julius Doe"]})))
  (testing "Other currently unsupported conditions are not included"
    (is (= (group-participants-by-condition {"Jillian Doe"
                                              {:min-participants 8}
                                             "Julius Doe"
                                              {:max-participants 9}
                                             "Jennifer Doe"
                                              {:min-participants 7}}
                                            :min-participants)
           {7 ["Jennifer Doe"] 8 ["Jillian Doe"]}))))

(deftest test-has-started?
  (testing "An event that has started"
    (is (true? (has-started? {:name "My Event"
                              :start (t/date-time 2016 7 22 12)
                              :end (t/date-time 2016 7 22 20)}))))
  (testing "An event that has not started"
    (is (false? (has-started? {:name "My Event"
                               :start (t/plus (t/now) (t/days 1))
                               :end (t/plus (t/now) (t/days 1)
                                                    (t/hours 2))})))))

; Rather than use, say, `take` to compare a subset of the sequences, we use
; first and second, because comparing date-times from a clj-time periodic-seq
; with ones we define yields false negatives (i.e. tests that fail, even
; though the behaviour is correct).
(deftest test-recurring-event
  (testing "Creating a recurring event should create an infinite sequence
   starting at the beginning of time (the UNIX epoch)"
    (let [pattern (recurring-event :name "My Event"
                                   :start-time [(t/hours 14)]
                                   :end-time [(t/hours 16)]
                                   :weekdays [3 4])]
     (is (= (first pattern)
            {:name "My Event"
             :start (t/date-time 1970 1 1 14)
             :end (t/date-time 1970 1 1 16)}))
     (is (= (second pattern)
            {:name "My Event"
             :start (t/date-time 1970 1 7 14)
             :end (t/date-time 1970 1 7 16)}))))
  (testing "Creating a recurring event that starts at a specified date"
    (let [pattern (recurring-event :name "My Event"
                                   :start-time [(t/hours 14)]
                                   :end-time [(t/hours 16)]
                                   :weekdays [2 5]
                                   :start-date (t/date-time 1989 4 11))]
      (is (= (first pattern)
             {:name "My Event"
              :start (t/date-time 1989 4 11 14)
              :end (t/date-time 1989 4 11 16)}))
      (is (= (second pattern)
             {:name "My Event"
              :start (t/date-time 1989 4 14 14)
              :end (t/date-time 1989 4 14 16)}))))
  (testing "Creating a recurring event that ends"
    (let [pattern (recurring-event :name "My Event"
                                   :start-time [(t/hours 14)]
                                   :end-time [(t/hours 16)]
                                   :weekdays [4 5]
                                   :end-date (t/date-time 1970 1 3))]
      (is (= (count pattern) 2))
      (is (= (first pattern)
             {:name "My Event"
              :start (t/date-time 1970 1 1 14)
              :end (t/date-time 1970 1 1 16)}))
      (is (= (second pattern)
             {:name "My Event"
              :start (t/date-time 1970 1 2 14)
              :end (t/date-time 1970 1 2 16)}))))
  (testing "Creating a recurring event in the future without an end"
    (is (= (first (recurring-event :name "My Event"
                                   :start-time [(t/hours 14)]
                                   :end-time [(t/hours 16)]
                                   :weekdays [1 2 3 4 5 6 7]
                                   :start-date (t/plus (t/floor (t/now) t/day)
                                                       (t/days 10))))
           {:name "My Event"
            :start (t/plus (t/floor (t/now) t/day) (t/days 10) (t/hours 14))
            :end (t/plus (t/floor (t/now) t/day) (t/days 10) (t/hours 16))})))
  (testing "Creating a recurring event with min-participants"
    (is (= (first (recurring-event :name "My Event"
                                   :start-time [(t/hours 14)]
                                   :end-time [(t/hours 16)]
                                   :weekdays [1 2 3 4 5 6 7]
                                   :min-participants 6))
           {:name "My Event"
            :start (t/date-time 1970 1 1 14)
            :end (t/date-time 1970 1 1 16)
            :min-participants 6})))
  (testing "Not specifying weekdays should mean all weekdays"
    (is (= (first (recurring-event :name "My Event"
                                   :start-time [(t/hours 14)]
                                   :end-time [(t/hours 16)]))
           {:name "My Event"
            :start (t/date-time 1970 1 1 14)
            :end (t/date-time 1970 1 1 16)})))
  (testing "If the start date doesn't fit in weekdays, it shouldn't be included"
    (is (= (first (recurring-event :name "My Event"
                                   :start-date (t/date-time 1970 1 1)
                                   :start-time [(t/hours 14)]
                                   :end-time [(t/hours 16)]
                                   :weekdays [5]))
           {:name "My Event"
            :start (t/date-time 1970 1 2 14)
            :end (t/date-time 1970 1 2 16)}))))

(deftest test-determine-participants
  (testing "An event's participants are its unconditional ones"
    (is (= (determine-participants {:name "My Event"
                                    :start (t/date-time 2016 7 22 12)
                                    :end (t/date-time 2016 7 22 20)
                                    :participants #{"John Doe" "Jane Doe"}})
           #{"John Doe" "Jane Doe"})))
  (testing "An event's participants are also the conditional ones whose conditions have been met"
    (is (= (determine-participants {:name "My Event"
                                    :start (t/date-time 2016 7 22 12)
                                    :end (t/date-time 2016 7 22 20)
                                    :participants #{"John Doe" "Jane Doe"}
                                    :conditional-participants {"James Doe"
                                                               {:min-participants 3}}})
           #{"John Doe" "Jane Doe" "James Doe"})))
  (testing "Even participants whose conditions weren't met at first can become participants"
    (is (= (determine-participants {:name "My Event"
                                    :start (t/date-time 2016 7 22 12)
                                    :end (t/date-time 2016 7 22 20)
                                    :participants #{"John Doe" "Jane Doe"}
                                    :conditional-participants {"James Doe"
                                                               {:min-participants 4}
                                                               "Jennifer Doe"
                                                               {:min-participants 5}
                                                               "Julius Doe"
                                                               {:min-participants 5}}})
           #{"John Doe" "Jane Doe" "James Doe" "Jennifer Doe" "Julius Doe"})))
  (testing "Even if the event can go on without the person left in the hole, he's still included"
    (is (= (determine-participants {:name "My Event"
                                    :start (t/date-time 2016 7 22 12)
                                    :end (t/date-time 2016 7 22 20)
                                    :participants #{"John Doe" "Jane Doe"}
                                    :conditional-participants {"James Doe"
                                                               {:min-participants 4}
                                                               "Jennifer Doe"
                                                               {:min-participants 5}
                                                               "Julius Doe"
                                                               {:min-participants 5}
                                                               "Jessica Doe"
                                                               {:min-participants 5}}})
           #{"John Doe" "Jane Doe" "James Doe" "Jennifer Doe" "Julius Doe"
             "Jessica Doe"})))
  (testing "Providing requirements in any order should yield the correct result"
    (is (= (determine-participants {:name "PDC Fodbold",
                                    :start (t/date-time 2016 8 9 14)
                                    :end (t/date-time 2016 8 9 16)
                                    :min-participants 6
                                    :participants #{"John Doe" "Jane Doe"}
                                    :conditional-participants {"James Doe"
                                                               {:min-participants 7}
                                                               "Jessica Doe"
                                                               {:min-participants 8}
                                                               "Jimmy Doe"
                                                               {:min-participants 10}
                                                               "Johnny Doe"
                                                               {:min-participants 3}}})
           #{"John Doe" "Jane Doe" "Johnny Doe"}))))

(deftest test-will-go-through
  (testing "That an event's implicit min-participants is 0"
    (is (true? (will-go-through? {:name "My Event"
                                  :start (t/date-time 2016 7 22 12)
                                  :end (t/date-time 2016 7 22 20)}))))
  (testing "An event with 3 min-participants and 4 participants goes through"
    (is (true? (will-go-through? {:name "My Event"
                                  :start (t/date-time 2016 7 22 12)
                                  :end (t/date-time 2016 7 22 20)
                                  :min-participants 3
                                  :participants #{"John Doe" "Jane Doe"
                                                  "Jim Doe" "June Doe"}}))))
  (testing "An event with 3 min-participants and 2 participants doesn't go
           through"
    (is (false? (will-go-through? {:name "My Event"
                                   :start (t/date-time 2016 7 22 12)
                                   :end (t/date-time 2016 7 22 20)
                                   :min-participants 3
                                   :participants #{"John Doe" "Jane Doe"}}))))
  (testing "An event with conditional participants that should go through"
    (is (true? (will-go-through? {:name "My Event"
                                  :start (t/date-time 2016 7 22 12)
                                  :end (t/date-time 2016 7 22 20)
                                  :min-participants 6
                                  :participants #{"John Doe" "Jane Doe"
                                                  "Jim Doe" "June Doe"
                                                  "Jeff Doe"}
                                  :conditional-participants {"Jillian Doe"
                                                             {:min-participants 8}
                                                             "Julius Doe"
                                                             {:min-participants 8}
                                                             "Jennifer Doe"
                                                             {:min-participants 7}}}))))
  (testing "An event with conditional participants that should not go through"
    (is (false? (will-go-through? {:name "My Event"
                                   :start (t/date-time 2016 7 22 12)
                                   :end (t/date-time 2016 7 22 20)
                                   :min-participants 3
                                   :participants #{"John Doe" "Jane Doe"}
                                   :conditional-participants {"Jim Doe"
                                                              {:min-participants 8}}}))))
  (testing "A complex set of conditions which should ultimately go through"
    (is (true? (will-go-through? {:name "My Event"
                                  :start (t/date-time 2016 7 22 12)
                                  :end (t/date-time 2016 7 22 20)
                                  :min-participants 6
                                  :participants #{"John Doe" "Jane Doe"}
                                  :conditional-participants {"Jim Doe"
                                                             {:min-participants 7}
                                                             "June Doe"
                                                              {:min-participants 7}
                                                             "Jeff Doe"
                                                              {:min-participants 8}
                                                             "Jillian Doe"
                                                              {:min-participants 8}
                                                             "Julius Doe"
                                                              {:min-participants 8}
                                                             "Jennifer Doe"
                                                              {:min-participants 8}}}))))
  (testing "A complex set of conditions which should ultimately not go through"
    (is (false? (will-go-through? {:name "My Event"
                                   :start (t/date-time 2016 7 22 12)
                                   :end (t/date-time 2016 7 22 20)
                                   :min-participants 6
                                   :participants #{"John Doe" "Jane Doe"}
                                   :conditional-participants {"Jim Doe"
                                                              {:min-participants 7}
                                                              "June Doe"
                                                               {:min-participants 7}
                                                              "Jeff Doe"
                                                               {:min-participants 8}
                                                              "Jillian Doe"
                                                               {:min-participants 8}
                                                              "Julius Doe"
                                                               {:min-participants 8}
                                                              "Jennifer Doe"
                                                               {:min-participants 9}}}))))
  (testing "Someone making high demands, but others willing to settle for less"
    (is (true? (will-go-through? {:name "My Event"
                                  :start (t/date-time 2016 7 22 12)
                                  :end (t/date-time 2016 7 22 20)
                                  :min-participants 6
                                  :participants #{"John Doe" "Jane Doe"}
                                  :conditional-participants {"Jim Doe"
                                                              {:min-participants 7}
                                                             "June Doe"
                                                              {:min-participants 7}
                                                             "Jeff Doe"
                                                              {:min-participants 7}
                                                             "Jillian Doe"
                                                              {:min-participants 7}
                                                             "Julius Doe"
                                                              {:min-participants 7}
                                                             "Jennifer Doe"
                                                              {:min-participants 9}}})))))

(deftest test-remove-participant
  (testing "Removing an existing participant"
    (is (= (remove-participant {:name "My Event"
                                :start (t/date-time 2016 7 22 12)
                                :end (t/date-time 2016 7 22 20)
                                :participants #{"John Doe" "Jane Doe"}}
                               "John Doe")
           {:name "My Event"
            :start (t/date-time 2016 7 22 12)
            :end (t/date-time 2016 7 22 20)
            :participants #{"Jane Doe"}})))
  (testing "Removing an existing conditional participant"
    (is (= (remove-participant {:name "My Event"
                                :start (t/date-time 2016 7 22 12)
                                :end (t/date-time 2016 7 22 20)
                                :conditional-participants {"John Doe"
                                                           {:min-participants 7}}}
                               "John Doe")
           {:name "My Event"
            :start (t/date-time 2016 7 22 12)
            :end (t/date-time 2016 7 22 20)}))))
