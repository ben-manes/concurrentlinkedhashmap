## Efficiency Tests ##
#### _Approach_ ####
3 factor full factorial experiment of three cache replacement strategies.

| **Factor** | | **Levels** ||
|:-----------|:|:-----------|:|
| _Name_ |_Abbr_|1 |-1|
|Working Set Size|wss|20000|2000|
|Cache Size|cs|200|20|
|Mean|mn|1000|100|

#### _Data_ ####
  * Data Distribution: Rounded Exponential
  * Experimental Results: Hit Rates

| **wss** | **cs** | **mn** | **fifo** | **second chance** | **lru** |
|:--------|:-------|:-------|:---------|:------------------|:--------|
|1 |1 |1 |.09|.10|.10|
|1 |1 |-1|.69|.78|.76|
|1 |-1|1 |.10|.10|.10|
|1 |-1|-1|.10|.10|.10|
|-1|1 |1 |.09|.09|.09|
|-1|1 |-1|.62|.70|.69|
|-1|-1|1 |.01|.01|.01|
|-1|-1|-1|.10|.11|.10|

#### _Allocation of Variation_ ####

> fifo.lm$coefficients[2:8]<sup>2</sup>/sum(fifo.lm$coefficients[2:8]<sup>2</sup>) 
| **wss** | **cs** | **mn** | **wss:cs** | **wss:mn** | **cs:mn** | **wss:cs:mn** |
|:--------|:-------|:-------|:-----------|:-----------|:----------|:--------------|
|0.0063770426|0.3468513352|0.3707652451|0.0000996413|0.0000996413|0.2694300518|0.0063770426|

> second\_chance.lm$coefficients[2:8]<sup>2</sup>/sum(second\_chance.lm$coefficients[2:8]<sup>2</sup>)
| **wss** | **cs** | **mn** | **wss:cs** | **wss:mn** | **cs:mn** | **wss:cs:mn** |
|:--------|:-------|:-------|:-----------|:-----------|:----------|:--------------|
|5.526341e-03|3.485037e<sup>-1</sup>|3.694617e<sup>-1</sup>|1.912229e<sup>-5</sup>|1.721006e<sup>-4</sup>|2.707907e<sup>-1</sup>|5.526341e<sup>-3</sup>|

> lru.lm$coefficients[2:8]<sup>2</sup>/sum(lru.lm$coefficients[2:8]<sup>2</sup>)
| **wss** | **cs** | **mn** | **wss:cs** | **wss:mn** | **cs:mn** | **wss:cs:mn** |
|:--------|:-------|:-------|:-----------|:-----------|:----------|:--------------|
|5.765356e<sup>-3</sup>|3.528837e<sup>-1</sup>|3.635765e<sup>-1</sup>|1.994933e<sup>-5</sup>|1.795440e<sup>-4</sup>|2.730864e<sup>-1</sup>|4.488599e<sup>-3</sup>|

#### _Observations_ ####
  1. _Second Chance_ always provides the best or comparable hit rates.
  1. _Second Chance_ combines strengths of _FIFO_ and _MFU_ cache strategies.
  1. Two factors and their interaction account for most of the variation for all replacement strategies.

| **policy** | **size** | **mean** | **size:mean** |
|:-----------|:---------|:---------|:--------------|
|fifo|34.7%|37.1%|26.9%|
|second chance|34.9%|36.9%|27.1%|
|lru|35.3%|36.4%|27.3%|