# Triangular

Triangular is a library for defining patterns of recurring events.

It uses [clj-time][] for date and time handling. Be aware that by default all clj-time's date-times are UTC, and Triangular makes no attempts to convert that to any other time zone. You are however free to pass non-UTC objects.

## Usage

Include Triangular in your app like this:

    (require '[triangular.core :as triangular])

An event is a map with the following layout:

* `:name` The name of the event
* `:start` A clj-time date-time object representing the start of the event
* `:end` A clj-time date-time object representing the end of the event

It can have three other fields:

* `:min-participants` A minimum number of participants for the event to go through.
* `:participants` A vector of people who will participate.
* `:conditional-participant` A map of people who will *maybe* participate. The key is the name (or any other identifier) of the person, and the value is a map of conditions. Currently the only condition supported is *:min-participants*, used for signifying intent to participate if a minimum number of participants (self included) sign up.

An example event could look like this:

    {:name "My Event"
     :start (t/date-time 2016 8 4 12)
     :end (t/date-time 2016 8 4 20)
     :participants #{"John Doe" "Jane Doe"}
     :conditional-participants {"James Doe" {:min-participants 8}
                                "June Doe" {:min-participants 10}}

### Recurring events

Triangular supports defining a pattern of recurring events, with settings for
when they repeat. This is done using the function `recurring-event`.

    (recurring-event :name "My event"
                     :start-date (t/date-time 2016 8 4)
                     :end-date (t/date-time 2018 8 4)
                     :start-time [(t/hours 14)]
                     :end-time [(t/hours 16) (t/minutes 30)]
                     :weekdays [1 3 5])
    ;=> ({:name "My event"
    ;     :start (t/date-time 2016 8 5 14)
    ;     :end (t/date-time 2016 8 5 16 30)
    ;    {:name "My event"
    ;     :start 2016 8 8 14
    ;     :end 2016 8 8 16 30}
    ;   …)

Be aware that if `:start-date` doesn't fit in `:weekdays` it isn't included.

### Determining participants

If you're using conditional participants, you can use `determine-participants` to find out whose conditions have been met (and should thus be considered participants):

    (determine-participants {:name "My Event"
                             :start (t/date-time 2016 8 4 12)
                             :end (t/date-time 2016 8 4 20)
                             :participants #{"John Doe" "Jane Doe"}
                             :conditional-participants {"James Doe" {:min-participants 3}
                                                        "June Doe" {:min-participants 10}}
    ;=> #{"John Doe" "Jane Doe" "James Doe"}

### Adding and removing participants

Rather than manipulating the map directly, you can use some convenience functions for adding and removing participants:

    (add-participant {:name "My Event"
                      :start (t/date-time 2016 8 4 12)
                      :end (t/date-time 2016 8 4 20)}
                     "John Doe")
    ; => {:name "My Event"
    ;     :start (t/date-time 2016 8 4 12)
    ;     :end (t/date-time 2016 8 4 20)
    ;     :participants #{"John Doe"}}

    (add-participant {:name "My Event"
                      :start (t/date-time 2016 8 4 12)
                      :end (t/date-time 2016 8 4 20)
                      :participants #{"John Doe"}}
                     {"Jane Doe" {:min-participants 4}})
    ; => {:name "My Event"
    ;     :start (t/date-time 2016 8 4 12)
    ;     :end (t/date-time 2016 8 4 20)
    ;     :participants #{"John Doe"}
    ;     :conditional-participants {"Jane Doe" {:min-participants 4}}}

    (remove-participant {:name "My Event"
                         :start (t/date-time 2016 8 4 12)
                         :end (t/date-time 2016 8 4 20)
                         :participants #{"John Doe" "James Doe"}
                         :conditional-participants {"Jane Doe" {:min-participants 4}}}
                        "James Doe")
    ; => {:name "My Event"
    ;     :start (t/date-time 2016 8 4 12)
    ;     :end (t/date-time 2016 8 4 20)
    ;     :participants #{"John Doe"}
    ;     :conditional-participants {"Jane Doe" {:min-participants 4}}}

    (remove-participant {:name "My Event"
                         :start (t/date-time 2016 8 4 12)
                         :end (t/date-time 2016 8 4 20)
                         :participants #{"John Doe"}
                         :conditional-participants {"Jane Doe" {:min-participants 4}
                                                    "June Doe" {:min-participants 6}}}
                        "June Doe")
    ; => {:name "My Event"
    ;     :start (t/date-time 2016 8 4 12)
    ;     :end (t/date-time 2016 8 4 20)
    ;     :participants #{"John Doe"}
    ;     :conditional-participants {"Jane Doe" {:min-participants 4}}}

## Licence

Copyright © 2016 Jonathan Holst.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[clj-time]: https://github.com/clj-time/clj-time
