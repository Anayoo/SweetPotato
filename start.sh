#!/bin/bash

args=`ls BOOT-INF/lib/ | xargs -i echo -n ":BOOT-INF/lib/{}" | cut -c 2-`
java -classpath config:BOOT-INF/classes:$args cn.anayoo.sweetpotato.ClientApplication