# Tasks for Anahata ASI Core (`anahata-asi-core`)

This file tracks the development tasks for the foundational AI framework.

check tasks.md in parent

## High Priority

- [ ] Some times Find and replace takes indentaion away and also seems to work with an empty occurrencesIndexes??

replacements" : {
              "replacements" : [ {
                "target" : "import java.util.HashMap;",
                "totalOccurrences" : 1,
                "reason" : "Using ConcurrentHashMap to prevent race conditions",
                "occurrenceIndexes" : [ ],
                "replacement" : "import java.util.concurrent.ConcurrentHashMap;"
              }, {
                "totalOccurrences" : 1,
                "target" : "private final Map<String, AgiPanel> agiPanels = new HashMap<>();",
                "reason" : "Using ConcurrentHashMap to prevent race conditions",
                "occurrenceIndexes" : [ ],
                "replacement" : "private final Map<String, AgiPanel> agiPanels = new ConcurrentHashMap<>();"
              } ],
              "resourceUuid" : "89070801-4c59-44fe-9a81-67b1cf819550",
              "lastModified" : 1778179163735


## Medium Priority

