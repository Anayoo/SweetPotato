#!/bin/bash

args=`ls BOOT-INF/lib/ | xargs -i echo -n ":BOOT-INF/lib/{}" | cut -c 2-`
java -classpath config:config/ojdbc8-12.2.0.1.jar:config/ucp-12.2.0.1.jar:BOOT-INF/classes:$args cn.anayoo.sweetpotato.ClientApplication